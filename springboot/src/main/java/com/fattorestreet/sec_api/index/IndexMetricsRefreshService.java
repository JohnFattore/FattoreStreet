package com.fattorestreet.sec_api.index;

import com.fattorestreet.sec_api.client.WebService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Refreshes {@link ListingIndexMetrics} from IEX-derived {@link DailyPrice} rows and SEC companyfacts.
 */
@Service
public class IndexMetricsRefreshService {

    private static final Logger log = LoggerFactory.getLogger(IndexMetricsRefreshService.class);

    private static final List<String> MLPS = List.of("EPD", "ET", "MPLX", "CQP", "WES", "PAA", "SUN");
    private static final List<String> PREFERRED = List.of("AGNCN", "AGNCM", "FITBI", "SLMBP", "VLYPO", "VLYPP");

    private final ListingRepository listingRepository;
    private final ListingIndexMetricsRepository metricsRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final WebService webService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final IwbHoldingsTickerSet iwbHoldingsTickerSet;

    public IndexMetricsRefreshService(
            ListingRepository listingRepository,
            ListingIndexMetricsRepository metricsRepository,
            DailyPriceRepository dailyPriceRepository,
            WebService webService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            IwbHoldingsTickerSet iwbHoldingsTickerSet) {
        this.listingRepository = listingRepository;
        this.metricsRepository = metricsRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.webService = webService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.iwbHoldingsTickerSet = iwbHoldingsTickerSet;
    }

    public record RefreshResult(int processed, int skipped, List<String> skippedTickers) {
    }

    /**
     * Updates metrics for listings whose tickers appear in {@code data/IWB_holdings.csv} (Russell 1000 / IWB
     * equity constituents), have a CIK (SEC), are not funds, and have at least one daily price row (IEX).
     */
    public RefreshResult refreshAllListings(int year) {
        List<String> skippedTickers = new ArrayList<>();
        int processed = 0;
        int skipped = 0;
        java.util.Set<String> iwb = iwbHoldingsTickerSet.tickers();
        List<Listing> listings = listingRepository.findAll().stream()
                .filter(l -> iwb.contains(l.getTicker()))
                .toList();
        int n = 0;
        for (Listing listing : listings) {
            n++;
            try {
                RefreshOutcome outcome = transactionTemplate.execute(status -> refreshOneListing(listing, year));
                if (outcome == null) {
                    skipped++;
                    skippedTickers.add(listing.getTicker() + ":txn_null");
                    continue;
                }
                if (outcome.processed()) {
                    processed++;
                } else {
                    skipped++;
                    skippedTickers.add(outcome.reason());
                }
            } catch (Exception e) {
                skipped++;
                skippedTickers.add(listing.getTicker() + ":txn_error");
                log.warn("[{}] refresh txn failed: {}", listing.getTicker(), e.getMessage());
            }
            if (n % 50 == 0) {
                log.info("Index metrics refresh progress: processed={}, skipped={}, lastTicker={}",
                        processed, skipped, listing.getTicker());
            }
        }
        return new RefreshResult(processed, skipped, skippedTickers);
    }

    @Transactional
    RefreshOutcome refreshOneListing(Listing listing, int year) {
        Asset asset = listing.getAsset();
        if (asset == null) {
            return RefreshOutcome.skipped(listing.getTicker() + ":missing_asset");
        }
        if (Boolean.TRUE.equals(asset.getIsFund())) {
            return RefreshOutcome.skipped(listing.getTicker() + ":fund");
        }
        Long cik = asset.getCik();
        if (cik == null) {
            return RefreshOutcome.skipped(listing.getTicker() + ":no_cik");
        }

        Optional<DailyPrice> priceOpt = dailyPriceRepository.findTopByTickerOrderByTradeDateDesc(listing.getTicker());
        if (priceOpt.isEmpty()) {
            return RefreshOutcome.skipped(listing.getTicker() + ":no_iex_daily_price");
        }
        DailyPrice dp = priceOpt.get();
        Double price = dp.getAdjustedClose() != null ? dp.getAdjustedClose() : dp.getClosePrice();
        if (price == null || price <= 0) {
            return RefreshOutcome.skipped(listing.getTicker() + ":no_price");
        }

        JsonNode root;
        try {
            String json = webService.fetchFinancials(cik);
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[{}] SEC companyfacts failed: {}", listing.getTicker(), e.getMessage());
            return RefreshOutcome.skipped(listing.getTicker() + ":sec_fetch");
        }

        SecIndexFactsParser.SecShareFacts sf = SecIndexFactsParser.parseShareFacts(root);
        if (sf.sharesOutstanding() == null || sf.sharesOutstanding() <= 0) {
            return RefreshOutcome.skipped(listing.getTicker() + ":no_shares");
        }

        BigDecimal priceBd = BigDecimal.valueOf(price);
        BigDecimal sharesBd = BigDecimal.valueOf(sf.sharesOutstanding());
        BigDecimal marketCap = sharesBd.multiply(priceBd).setScale(5, RoundingMode.HALF_UP);

        BigDecimal vol = dp.getVolume() != null
                ? BigDecimal.valueOf(dp.getVolume())
                : BigDecimal.ZERO;
        BigDecimal volumeUsd = vol.multiply(priceBd).setScale(5, RoundingMode.HALF_UP);

        BigDecimal freeFloat = computeFreeFloat(sf, listing.getTicker(), sharesBd);
        BigDecimal ffMc = marketCap.multiply(freeFloat).setScale(5, RoundingMode.HALF_UP);

        String ticker = listing.getTicker();
        if (DualClassIndexCapSplit.halvesCapForTicker(ticker)) {
            marketCap = DualClassIndexCapSplit.apply(ticker, marketCap);
            ffMc = DualClassIndexCapSplit.apply(ticker, ffMc);
        }

        ListingIndexMetrics m = metricsRepository.findByListingAndYear(listing, year).orElseGet(() -> {
            ListingIndexMetrics nm = new ListingIndexMetrics();
            nm.setListing(listing);
            nm.setYear(year);
            return nm;
        });
        m.setMarketCap(marketCap);
        m.setVolume(vol);
        m.setVolumeUsd(volumeUsd);
        m.setFreeFloat(freeFloat);
        m.setFreeFloatMarketCap(ffMc);
        SecIndexFactsParser.SubmissionsLocation loc = fetchSubmissionsLocation(cik, ticker);
        m.setCountryIncorp(SecIndexFactsParser.normalizeCountry(loc.countryIncorp()));
        m.setCountryHq(SecIndexFactsParser.normalizeCountry(loc.countryHq()));
        m.setStateIncorp(loc.stateIncorp());
        m.setStateHq(loc.stateHq());
        m.setSecurityType("Common Stock");
        m.setYearIpo(0);
        applyTickerOverrides(ticker, m);
        metricsRepository.save(m);
        return RefreshOutcome.ok();
    }

    record RefreshOutcome(boolean processed, String reason) {
        static RefreshOutcome ok() {
            return new RefreshOutcome(true, "");
        }

        static RefreshOutcome skipped(String reason) {
            return new RefreshOutcome(false, reason);
        }
    }

    private SecIndexFactsParser.SubmissionsLocation fetchSubmissionsLocation(Long cik, String ticker) {
        try {
            String json = webService.fetchSubmissions(cik);
            JsonNode root = objectMapper.readTree(json);
            return SecIndexFactsParser.parseSubmissionsLocation(root);
        } catch (Exception e) {
            log.warn("[{}] SEC submissions failed (country lookup): {}", ticker, e.getMessage());
            return SecIndexFactsParser.SubmissionsLocation.empty();
        }
    }

    /**
     * Computes free-float ratio as floatShares / sharesOutstanding.
     * Derives floatShares from SEC's EntityPublicFloat (USD) divided by the price on the float reporting date,
     * avoiding stale-dollar-value distortion when the stock price has moved since the filing.
     * Returns ONE when public float data is unavailable.
     */
    private BigDecimal computeFreeFloat(SecIndexFactsParser.SecShareFacts sf, String ticker, BigDecimal sharesOutstanding) {
        if (sf.publicFloatUsd() == null || sf.publicFloatUsd() <= 0 || sf.publicFloatDate() == null) {
            return BigDecimal.ONE;
        }
        Optional<DailyPrice> floatDatePrice = dailyPriceRepository
                .findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(ticker, sf.publicFloatDate());
        if (floatDatePrice.isEmpty()) {
            return BigDecimal.ONE;
        }
        Double closePrice = floatDatePrice.get().getClosePrice();
        if (closePrice == null || closePrice <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal priceBd = BigDecimal.valueOf(closePrice);
        BigDecimal floatShares = BigDecimal.valueOf(sf.publicFloatUsd())
                .divide(priceBd, 0, RoundingMode.HALF_UP);
        BigDecimal ff = floatShares.divide(sharesOutstanding, 10, RoundingMode.HALF_UP);
        if (ff.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return ff;
    }

    private void applyTickerOverrides(String ticker, ListingIndexMetrics m) {
        if (MLPS.contains(ticker)) {
            m.setSecurityType("MLP");
        }
        if (PREFERRED.contains(ticker)) {
            m.setSecurityType("Preferred Stock");
        }
    }
}

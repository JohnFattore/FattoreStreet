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
    private static final List<String> ADR = List.of("CUK");

    private final ListingRepository listingRepository;
    private final ListingIndexMetricsRepository metricsRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final WebService webService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public IndexMetricsRefreshService(
            ListingRepository listingRepository,
            ListingIndexMetricsRepository metricsRepository,
            DailyPriceRepository dailyPriceRepository,
            WebService webService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.listingRepository = listingRepository;
        this.metricsRepository = metricsRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.webService = webService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public record RefreshResult(int processed, int skipped, List<String> skippedTickers) {
    }

    /**
     * Updates metrics for all non-fund listings that have a CIK (SEC) and at least one daily price row (IEX).
     */
    public RefreshResult refreshAllListings(int year) {
        List<String> skippedTickers = new ArrayList<>();
        int processed = 0;
        int skipped = 0;
        List<Listing> listings = listingRepository.findAll();
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
        if (asset == null || Boolean.TRUE.equals(asset.getIsFund())) {
            return RefreshOutcome.skipped(listing.getTicker() + ":fund_or_missing_asset");
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

        BigDecimal freeFloat = SecIndexFactsParser.computeFreeFloat(sf.sharesOutstanding(), sf.publicFloatUsd(), priceBd);
        if (freeFloat == null) {
            freeFloat = BigDecimal.ONE;
        }
        BigDecimal ffMc = marketCap.multiply(freeFloat).setScale(5, RoundingMode.HALF_UP);

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
        m.setCountryIncorp(SecIndexFactsParser.normalizeCountry(sf.countryCodeOrName()));
        m.setCountryHq(SecIndexFactsParser.normalizeCountry(sf.countryCodeOrName()));
        m.setSecurityType("Common Stock");
        m.setYearIpo(0);
        applyTickerOverrides(listing.getTicker(), m);
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

    private void applyTickerOverrides(String ticker, ListingIndexMetrics m) {
        if ("GOOG".equals(ticker)) {
            m.setFreeFloat(BigDecimal.valueOf((5.19 / 5.59) / 2));
            if (m.getMarketCap() != null) {
                m.setFreeFloatMarketCap(m.getMarketCap().multiply(m.getFreeFloat()).setScale(5, RoundingMode.HALF_UP));
            }
        } else if ("GOOGL".equals(ticker)) {
            m.setFreeFloat(BigDecimal.valueOf((5.84 / 5.86) / 2));
            if (m.getMarketCap() != null) {
                m.setFreeFloatMarketCap(m.getMarketCap().multiply(m.getFreeFloat()).setScale(5, RoundingMode.HALF_UP));
            }
        }
        if (MLPS.contains(ticker)) {
            m.setSecurityType("MLP");
        }
        if (PREFERRED.contains(ticker)) {
            m.setSecurityType("Preferred Stock");
        }
        if (ADR.contains(ticker)) {
            m.setSecurityType("ADR");
        }
    }
}

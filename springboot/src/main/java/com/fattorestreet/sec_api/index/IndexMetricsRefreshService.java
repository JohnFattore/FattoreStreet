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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Refreshes {@link ListingIndexMetrics} from IEX-derived {@link DailyPrice} rows and SEC companyfacts.
 */
@Service
public class IndexMetricsRefreshService {

    private static final Logger log = LoggerFactory.getLogger(IndexMetricsRefreshService.class);

    /**
     * Hardcoded security-type overrides applied in {@link #applyTickerOverrides}.
     * Update these lists when adding/removing MLPs or preferred-stock listings whose
     * SEC filings would otherwise classify them as common stock.
     */
    private static final List<String> MLPS = List.of("EPD", "ET", "MPLX", "CQP", "WES", "PAA", "SUN");
    private static final List<String> PREFERRED = List.of("AGNCN", "AGNCM", "FITBI", "SLMBP", "VLYPO", "VLYPP");

    /**
     * Abort the batch after this many consecutive SEC fetch failures — likely SEC outage,
     * not per-ticker data issues, so failing fast avoids burning the rest of the run.
     */
    private static final int CONSECUTIVE_SEC_FAILURE_ABORT_THRESHOLD = 25;

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
     * Updates metrics for Russell 1000 / IWB tickers (from {@code data/IWB_holdings.csv}).
     * Listings must have a CIK (SEC), not be funds, and have at least one daily price row (IEX).
     */
    public RefreshResult refreshRussell1000Listings(int year) {
        Set<String> iwb = iwbHoldingsTickerSet.tickers();
        List<Listing> listings = listingRepository.findAll().stream()
                .filter(l -> iwb.contains(l.getTicker()))
                .toList();
        return refreshListings(year, listings);
    }

    /**
     * Updates metrics for all listings in the database (subject to per-listing eligibility checks).
     */
    public RefreshResult refreshAllTickers(int year) {
        List<Listing> listings = listingRepository.findAll();
        return refreshListings(year, listings);
    }

    /**
     * Updates metrics for a single ticker. Returns a result with one skipped entry if the ticker
     * does not resolve to a known listing.
     */
    public RefreshResult refreshSingleTicker(String ticker, int year) {
        if (ticker == null || ticker.isBlank()) {
            return new RefreshResult(0, 1, List.of(":blank_ticker"));
        }
        String normalized = ticker.trim().toUpperCase(java.util.Locale.ROOT);
        Optional<Listing> listingOpt = listingRepository.findByTicker(normalized);
        if (listingOpt.isEmpty()) {
            return new RefreshResult(0, 1, List.of(normalized + ":unknown_ticker"));
        }
        return refreshListings(year, List.of(listingOpt.get()));
    }

    private RefreshResult refreshListings(int year, List<Listing> listings) {
        List<String> skippedTickers = new ArrayList<>();
        int processed = 0;
        int skipped = 0;
        int consecutiveSecFailures = 0;
        int total = listings.size();
        int nextProgressPct = 10;

        for (int n = 0; n < total; n++) {
            Listing listing = listings.get(n);
            RefreshOutcome outcome = refreshOneListingSafely(listing, year);
            if (outcome.processed()) {
                processed++;
                consecutiveSecFailures = 0;
            } else {
                skipped++;
                skippedTickers.add(outcome.reason());
                if (outcome.reason().endsWith(":sec_fetch")) {
                    consecutiveSecFailures++;
                    if (consecutiveSecFailures >= CONSECUTIVE_SEC_FAILURE_ABORT_THRESHOLD) {
                        log.error("Aborting index metrics refresh: {} consecutive SEC fetch failures (likely SEC outage). " +
                                        "Processed={}, skipped={}, remaining={}",
                                consecutiveSecFailures, processed, skipped, total - (n + 1));
                        break;
                    }
                } else {
                    consecutiveSecFailures = 0;
                }
            }
            int pct = total > 0 ? ((n + 1) * 100) / total : 100;
            if (pct >= nextProgressPct) {
                log.info("Index metrics refresh progress: {}% ({}/{}), processed={}, skipped={}, lastTicker={}",
                        pct, n + 1, total, processed, skipped, listing.getTicker());
                nextProgressPct = pct + 10;
            }
        }
        return new RefreshResult(processed, skipped, skippedTickers);
    }

    private RefreshOutcome refreshOneListingSafely(Listing listing, int year) {
        try {
            // Pre-flight checks (no DB writes, no network) — fail fast on ineligible listings.
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
            // Market cap = current shares × current UNADJUSTED close. Adjusted close is back-corrected
            // for splits/dividends and would mismatch the current sharesOutstanding figure if the latest
            // daily price row is stale across a corporate action. Fall back to adjustedClose only if
            // closePrice is missing.
            Double price = dp.getClosePrice() != null ? dp.getClosePrice() : dp.getAdjustedClose();
            if (price == null || price <= 0) {
                return RefreshOutcome.skipped(listing.getTicker() + ":no_price");
            }

            // SEC fetches — slow network I/O — done outside any DB transaction.
            JsonNode factsRoot;
            try {
                String json = webService.fetchFinancials(cik);
                factsRoot = objectMapper.readTree(json);
            } catch (Exception e) {
                log.warn("[{}] SEC companyfacts failed: {}", listing.getTicker(), e.getMessage());
                return RefreshOutcome.skipped(listing.getTicker() + ":sec_fetch");
            }

            SecIndexFactsParser.SecShareFacts sf = SecIndexFactsParser.parseShareFacts(factsRoot);
            if (sf.sharesOutstanding() == null || sf.sharesOutstanding() <= 0) {
                return RefreshOutcome.skipped(listing.getTicker() + ":no_shares");
            }

            SecIndexFactsParser.SubmissionsLocation loc = fetchSubmissionsLocation(cik, listing.getTicker());

            // Free-float lookup also reads daily prices; safe to do before the write txn.
            BigDecimal sharesBd = BigDecimal.valueOf(sf.sharesOutstanding());
            BigDecimal freeFloat = computeFreeFloat(sf, listing.getTicker(), sharesBd);

            // Compute write-time values now so the txn body is pure DB read+write.
            BigDecimal priceBd = BigDecimal.valueOf(price);
            BigDecimal marketCap = sharesBd.multiply(priceBd).setScale(5, RoundingMode.HALF_UP);
            BigDecimal vol = dp.getVolume() != null ? BigDecimal.valueOf(dp.getVolume()) : BigDecimal.ZERO;
            BigDecimal volumeUsd = vol.multiply(priceBd).setScale(5, RoundingMode.HALF_UP);
            BigDecimal ffMc = marketCap.multiply(freeFloat).setScale(5, RoundingMode.HALF_UP);

            String ticker = listing.getTicker();
            if (DualClassIndexCapSplit.halvesCapForTicker(ticker)) {
                marketCap = DualClassIndexCapSplit.apply(ticker, marketCap);
                ffMc = DualClassIndexCapSplit.apply(ticker, ffMc);
            }

            BigDecimal finalMarketCap = marketCap;
            BigDecimal finalFfMc = ffMc;
            RefreshOutcome outcome = transactionTemplate.execute(status -> persistMetrics(
                    listing, year, finalMarketCap, vol, volumeUsd, freeFloat, finalFfMc, loc));
            if (outcome == null) {
                return RefreshOutcome.skipped(listing.getTicker() + ":txn_null");
            }
            return outcome;
        } catch (Exception e) {
            log.warn("[{}] refresh txn failed: {}", listing.getTicker(), e.getMessage());
            return RefreshOutcome.skipped(listing.getTicker() + ":txn_error");
        }
    }

    /**
     * Persists computed metrics for one listing in a single short DB transaction.
     * Pure read-modify-write: no network I/O, no expensive computation.
     */
    private RefreshOutcome persistMetrics(
            Listing listing,
            int year,
            BigDecimal marketCap,
            BigDecimal vol,
            BigDecimal volumeUsd,
            BigDecimal freeFloat,
            BigDecimal ffMc,
            SecIndexFactsParser.SubmissionsLocation loc) {
        String ticker = listing.getTicker();
        boolean[] isInsert = new boolean[1];
        ListingIndexMetrics m = metricsRepository.findByListingAndYear(listing, year).orElseGet(() -> {
            ListingIndexMetrics nm = new ListingIndexMetrics();
            nm.setListing(listing);
            nm.setYear(year);
            isInsert[0] = true;
            return nm;
        });
        m.setMarketCap(marketCap);
        m.setVolume(vol);
        m.setVolumeUsd(volumeUsd);
        m.setFreeFloat(freeFloat);
        m.setFreeFloatMarketCap(ffMc);
        m.setCountryIncorp(SecIndexFactsParser.normalizeCountry(loc.countryIncorp()));
        m.setCountryHq(SecIndexFactsParser.normalizeCountry(loc.countryHq()));
        m.setStateIncorp(loc.stateIncorp());
        m.setStateHq(loc.stateHq());
        // Defaults are only seeded on insert so prior values (e.g. yearIpo populated by another job)
        // aren't clobbered on every refresh.
        if (isInsert[0]) {
            m.setSecurityType("Common Stock");
            m.setYearIpo(0);
            applyTickerOverrides(ticker, m);
        } else if (MLPS.contains(ticker) || PREFERRED.contains(ticker)) {
            // Override lists are still authoritative — re-apply on update so a misclassified
            // existing row gets corrected once the ticker is added to the list.
            applyTickerOverrides(ticker, m);
        }
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
     *
     * <p>Derives floatShares from SEC's EntityPublicFloat (USD) divided by the price on the float
     * reporting date, avoiding stale-dollar-value distortion when the stock price has moved since
     * the filing. Returns ONE when public float data is unavailable.
     *
     * <p><b>Uses adjusted close, not unadjusted close, for the float-date price lookup.</b>
     * SEC's {@code EntityCommonStockSharesOutstanding} is the latest snapshot — already post-split
     * if a split has happened since the float reporting date. To divide by it consistently, the
     * historical float-date price must be expressed in the same (current) share basis, which is
     * exactly what adjustedClose represents. Example: NFLX did a 10:1 split on 2025-11-14; the
     * 2025-06-30 unadjusted close was ~$1,300, but the post-split-adjusted close is ~$130, and the
     * latest sharesOutstanding (~4.22B) is post-split. Using closePrice here yields ff ≈ 0.10
     * (~10x too small); using adjustedClose yields ~1.0. Falls back to closePrice only if
     * adjustedClose is missing.
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
        DailyPrice fd = floatDatePrice.get();
        Double price = fd.getAdjustedClose() != null ? fd.getAdjustedClose() : fd.getClosePrice();
        if (price == null || price <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal priceBd = BigDecimal.valueOf(price);
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

package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import com.fattorestreet.sec_api.util.TickerFormats;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    /**
     * A share count reported more than this many days before the latest price is stale. Every US filer
     * restates it on each 10-Q, so a single-class company's figure is weeks old; a multi-class filer's
     * undimensioned figure predates per-class cover pages and can be a decade old (Berkshire's is from
     * 2011), so falling past this sends the refresh to the cover page instead.
     */
    static final int MAX_SHARE_COUNT_AGE_DAYS = 456;

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
        List<Listing> listings = matchIwbListings(listingRepository.findAll(), iwbHoldingsTickerSet.tickers());
        return refreshListings(year, listings);
    }

    /**
     * Listings named by the IWB file. iShares drops the share-class separator ({@code BRKB}) that SEC
     * listings keep ({@code BRK-B}), so an IWB ticker with no exact listing matches a separated
     * listing ticker on its compact form. Only separated listing tickers match that way, so an
     * unrelated plain ticker that happens to spell the compact form is never pulled in.
     */
    static List<Listing> matchIwbListings(List<Listing> listings, Set<String> iwbTickers) {
        Set<String> listingTickers = listings.stream().map(Listing::getTicker).collect(Collectors.toSet());
        Set<String> unmatchedCompact = iwbTickers.stream()
                .filter(t -> !listingTickers.contains(t))
                .map(TickerFormats::compact)
                .collect(Collectors.toSet());
        return listings.stream()
                .filter(l -> iwbTickers.contains(l.getTicker())
                        || (TickerFormats.hasClassSeparator(l.getTicker())
                                && unmatchedCompact.contains(TickerFormats.compact(l.getTicker()))))
                .toList();
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

        // Cover pages by CIK for this run, so share classes of one company (BRK-A, BRK-B) fetch it once.
        // An empty Optional records a failed fetch, so siblings don't retry it against a failing source.
        Map<Long, Optional<CoverPageShares>> coverPages = new HashMap<>();
        for (int n = 0; n < total; n++) {
            Listing listing = listings.get(n);
            RefreshOutcome outcome = refreshOneListingSafely(listing, year, coverPages);
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

    private RefreshOutcome refreshOneListingSafely(
            Listing listing, int year, Map<Long, Optional<CoverPageShares>> coverPages) {
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

            // daily_prices is keyed by IEX symbol (BRK.B), listings by SEC ticker (BRK-B).
            String iexSymbol = TickerFormats.iexSymbol(listing.getTicker());
            Optional<DailyPrice> priceOpt = dailyPriceRepository.findTopByTickerOrderByTradeDateDesc(iexSymbol);
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

            String ticker = listing.getTicker();
            SecIndexFactsParser.SecShareFacts sf = SecIndexFactsParser.parseShareFacts(factsRoot);
            JsonNode submissions = fetchSubmissionsRoot(cik, ticker);
            SecIndexFactsParser.SubmissionsLocation loc = submissions != null
                    ? SecIndexFactsParser.parseSubmissionsLocation(submissions)
                    : SecIndexFactsParser.SubmissionsLocation.empty();

            // Shares: a current company-wide DEI figure; else this listing's class from the latest cover
            // page (multi-class filers have no current company-wide figure in companyfacts at all); else
            // a current us-gaap fallback. A stale figure is never used. A company with several listings
            // always goes to the cover page: a company-wide figure, even a current one, would size
            // every class at the whole company.
            LocalDate priceDate = dp.getTradeDate();
            boolean multiListed = listingRepository.findByAsset(asset).size() > 1;
            boolean deiCurrent = !multiListed && sf.sharesFromDei() && isCurrent(sf.sharesAsOf(), priceDate);
            CoverPageShares cover = null;
            Long shares;
            if (deiCurrent) {
                shares = sf.sharesOutstanding();
            } else {
                if (submissions == null) {
                    return RefreshOutcome.skipped(ticker + ":sec_fetch");
                }
                Optional<CoverPageShares> fetched = coverPages.get(cik);
                if (fetched == null) {
                    try {
                        fetched = Optional.of(coverPage(cik, submissions));
                    } catch (Exception e) {
                        log.warn("[{}] SEC cover page failed: {}", ticker, e.getMessage());
                        fetched = Optional.empty();
                    }
                    coverPages.put(cik, fetched);
                }
                if (fetched.isEmpty()) {
                    return RefreshOutcome.skipped(ticker + ":sec_fetch");
                }
                cover = fetched.get();
                if (cover.hasClassShares()) {
                    shares = cover.sharesFor(ticker);
                    if (shares == null) {
                        return RefreshOutcome.skipped(ticker + ":class_unmatched");
                    }
                } else if (cover.plainShares() != null) {
                    shares = cover.plainShares();
                } else if (sf.sharesOutstanding() != null && isCurrent(sf.sharesAsOf(), priceDate)) {
                    shares = sf.sharesOutstanding();
                } else {
                    return RefreshOutcome.skipped(ticker + (sf.sharesOutstanding() == null ? ":no_shares" : ":stale_shares"));
                }
            }
            if (shares == null || shares <= 0) {
                return RefreshOutcome.skipped(ticker + ":no_shares");
            }

            // Free-float lookup also reads daily prices; safe to do before the write txn.
            BigDecimal sharesBd = BigDecimal.valueOf(shares);
            BigDecimal freeFloat = cover != null && cover.hasClassShares()
                    ? computeMultiClassFreeFloat(sf, cover, asset)
                    : computeFreeFloat(sf, iexSymbol, sharesBd);

            // Compute write-time values now so the txn body is pure DB read+write.
            BigDecimal priceBd = BigDecimal.valueOf(price);
            BigDecimal marketCap = sharesBd.multiply(priceBd).setScale(5, RoundingMode.HALF_UP);
            BigDecimal vol = dp.getVolume() != null ? BigDecimal.valueOf(dp.getVolume()) : BigDecimal.ZERO;
            BigDecimal volumeUsd = vol.multiply(priceBd).setScale(5, RoundingMode.HALF_UP);
            BigDecimal ffMc = marketCap.multiply(freeFloat).setScale(5, RoundingMode.HALF_UP);

            RefreshOutcome outcome = transactionTemplate.execute(status -> persistMetrics(
                    listing, year, marketCap, vol, volumeUsd, freeFloat, ffMc, priceDate, loc));
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
            LocalDate priceDate,
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
        m.setPriceDate(priceDate);
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

    /** SEC submissions JSON, or {@code null} when it can't be fetched (location then stays unknown). */
    private JsonNode fetchSubmissionsRoot(Long cik, String ticker) {
        try {
            return objectMapper.readTree(webService.fetchSubmissions(cik));
        } catch (Exception e) {
            log.warn("[{}] SEC submissions failed: {}", ticker, e.getMessage());
            return null;
        }
    }

    static boolean isCurrent(LocalDate asOf, LocalDate priceDate) {
        return asOf != null && priceDate != null && !asOf.isBefore(priceDate.minusDays(MAX_SHARE_COUNT_AGE_DAYS));
    }

    /**
     * The latest periodic filing's cover page, from its XBRL instance ({@code *_htm.xml}, present for
     * every inline-XBRL filing). {@link CoverPageShares#EMPTY} when the company has no recent periodic
     * filing or the filing has no instance document.
     */
    private CoverPageShares coverPage(Long cik, JsonNode submissions) throws XMLStreamException {
        CoverPageShares cover = CoverPageShares.EMPTY;
        String accession = SecIndexFactsParser.latestPeriodicFilingAccession(submissions);
        if (accession != null) {
            JsonNode items = objectMapper.readTree(webService.fetchFilingIndex(cik, accession))
                    .path("directory").path("item");
            String instance = null;
            for (JsonNode item : items) {
                String name = item.path("name").asString("");
                if (name.endsWith("_htm.xml")) {
                    instance = name;
                    break;
                }
            }
            if (instance != null) {
                cover = CoverPageShares.parse(webService.fetchFilingDocument(cik, accession, instance));
            }
        }
        return cover;
    }

    /**
     * Free float for one class of a multi-class company: public float (USD, company-wide) over the value
     * of every listed class on the float date. Dividing the company-wide figure by one class's value, as
     * the single-class path would, overstates the ratio by the other classes' share of the company.
     * Unlisted classes (Alphabet's Class B, Visa's Class B and C) have no price and are left out, which
     * suits a float measure: they are mostly the affiliate holdings that public float excludes.
     * Adjusted close puts the float-date price on the current share basis, as in {@link #computeFreeFloat}.
     */
    private BigDecimal computeMultiClassFreeFloat(SecIndexFactsParser.SecShareFacts sf, CoverPageShares cover,
            Asset asset) {
        if (sf.publicFloatUsd() == null || sf.publicFloatUsd() <= 0 || sf.publicFloatDate() == null) {
            return BigDecimal.ONE;
        }
        Map<String, String> iexBySymbol = new HashMap<>();
        for (Listing sibling : listingRepository.findByAsset(asset)) {
            iexBySymbol.put(TickerFormats.compact(sibling.getTicker()), TickerFormats.iexSymbol(sibling.getTicker()));
        }
        BigDecimal listedValue = BigDecimal.ZERO;
        for (CoverPageShares.ListedClass c : cover.listedClasses()) {
            String symbol = iexBySymbol.getOrDefault(c.compactSymbol(), c.compactSymbol());
            Optional<DailyPrice> fd = dailyPriceRepository
                    .findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(symbol, sf.publicFloatDate());
            Double price = fd.map(p -> p.getAdjustedClose() != null ? p.getAdjustedClose() : p.getClosePrice())
                    .orElse(null);
            if (price == null || price <= 0) {
                // Dropping the class would shrink the denominator and inflate the ratio. Fall back to the
                // rule for an unknown float: the whole company counts as free-floating.
                log.info("[{}] no float-date price for listed class {}; free float defaults to 1",
                        asset.getCik(), c.compactSymbol());
                return BigDecimal.ONE;
            }
            listedValue = listedValue.add(BigDecimal.valueOf(c.shares()).multiply(BigDecimal.valueOf(price)));
        }
        if (listedValue.signum() <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal ff = BigDecimal.valueOf(sf.publicFloatUsd()).divide(listedValue, 10, RoundingMode.HALF_UP);
        return ff.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : ff;
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
    private BigDecimal computeFreeFloat(SecIndexFactsParser.SecShareFacts sf, String iexSymbol, BigDecimal sharesOutstanding) {
        if (sf.publicFloatUsd() == null || sf.publicFloatUsd() <= 0 || sf.publicFloatDate() == null) {
            return BigDecimal.ONE;
        }
        Optional<DailyPrice> floatDatePrice = dailyPriceRepository
                .findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(iexSymbol, sf.publicFloatDate());
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

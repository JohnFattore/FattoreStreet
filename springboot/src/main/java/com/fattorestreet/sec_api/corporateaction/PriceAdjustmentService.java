package com.fattorestreet.sec_api.corporateaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import com.fattorestreet.sec_api.util.MarketTime;

@Service
public class PriceAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(PriceAdjustmentService.class);
    private static final MathContext FACTOR_MATH = MathContext.DECIMAL64;
    private static final LocalDate VALIDATION_MIN_DATE = LocalDate.of(2016, 1, 1);
    /** Rolling SEC re-detection cadence: every ticker is re-scanned about weekly. */
    static final int SEC_REDETECTION_INTERVAL_DAYS = 7;
    /** Overnight |return| above this forces same-run re-detection (splits show up as huge raw jumps). */
    static final double JUMP_REDETECTION_RETURN_THRESHOLD = 0.25;

    private final DailyPriceRepository dailyPriceRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final AssetRepository assetRepository;
    private final EquityCorporateActionService equityCorporateActionService;
    private final EtfCorporateActionService etfCorporateActionService;
    private final CorporateActionValidationService corporateActionValidationService;
    private final AdjustedPriceValidationService adjustedPriceValidationService;
    private final ListingRepository listingRepository;

    public PriceAdjustmentService(DailyPriceRepository dailyPriceRepository,
                                  CorporateActionRepository corporateActionRepository,
                                  AssetRepository assetRepository,
                                  EquityCorporateActionService equityCorporateActionService,
                                  EtfCorporateActionService etfCorporateActionService,
                                  CorporateActionValidationService corporateActionValidationService,
                                  AdjustedPriceValidationService adjustedPriceValidationService,
                                  ListingRepository listingRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.assetRepository = assetRepository;
        this.equityCorporateActionService = equityCorporateActionService;
        this.etfCorporateActionService = etfCorporateActionService;
        this.corporateActionValidationService = corporateActionValidationService;
        this.adjustedPriceValidationService = adjustedPriceValidationService;
        this.listingRepository = listingRepository;
    }

    /**
     * Knobs for a detection/adjustment run.
     *
     * @param force                re-fetch SEC data even when detection is fresh
     * @param etfOnly              only process fund tickers
     * @param equityOnly           only process non-fund tickers
     * @param validateWithYfinance attach yfinance diagnostic reports (dev verification only)
     */
    public record AdjustmentOptions(boolean force, boolean etfOnly, boolean equityOnly, boolean validateWithYfinance) {
        public static final AdjustmentOptions DEFAULTS = new AdjustmentOptions(false, false, false, false);
    }

    /**
     * Detect corporate actions from SEC and adjust prices for a single ticker.
     * @return summary map
     */
    public Map<String, Object> adjustTicker(String ticker) {
        return adjustTicker(ticker, AdjustmentOptions.DEFAULTS);
    }

    public Map<String, Object> adjustTicker(String ticker, AdjustmentOptions options) {
        if (options.etfOnly() && options.equityOnly()) {
            return Map.of(
                    "ticker", ticker,
                    "status", "invalid_args",
                    "message", "etfOnly and equityOnly cannot both be true",
                    "pricesUpdated", 0
            );
        }

        String normalizedTicker = ticker == null ? null : ticker.trim().toUpperCase(Locale.US);
        Asset asset = assetRepository.findByListings_Ticker(normalizedTicker);
        if (asset == null) {
            return Map.of("ticker", normalizedTicker, "status", "no_asset", "pricesUpdated", 0);
        }

        boolean isFund = Boolean.TRUE.equals(asset.getIsFund());
        if (options.etfOnly() && !isFund) {
            return Map.of("ticker", normalizedTicker, "status", "skipped_mode", "pricesUpdated", 0);
        }
        if (options.equityOnly() && isFund) {
            return Map.of("ticker", normalizedTicker, "status", "skipped_mode", "pricesUpdated", 0);
        }

        List<CorporateAction> actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(normalizedTicker);
        boolean hasEquityDividends = actions.stream().anyMatch(a -> a.getActionType() == ActionType.DIVIDEND);
        Listing listing = listingRepository.findByTicker(normalizedTicker).orElse(null);
        boolean shouldFetchSec = options.force()
                || actions.isEmpty()
                || isDetectionStale(listing)
                || (!isFund && options.validateWithYfinance() && !hasEquityDividends)
                || hasOvernightPriceJump(normalizedTicker);
        int newActions;
        EtfCorporateActionService.EtfDetectionReport etfReport = null;
        EquityCorporateActionService.EquityDetectionReport equityReport = null;
        if (shouldFetchSec) {
            if (isFund) {
                etfReport = etfCorporateActionService.detectAndPersist(normalizedTicker, asset.getCik());
                newActions = etfReport.saved();
                stampDetection(listing);
            } else {
                equityReport = equityCorporateActionService.detectAndPersistWithDiagnostics(normalizedTicker, asset.getCik());
                newActions = equityReport.savedActions();
                stampDetectionIfSucceeded(listing, equityReport);
            }
            actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(normalizedTicker);
        } else {
            newActions = 0;
        }
        int splits = (int) actions.stream().filter(a -> a.getActionType() == ActionType.SPLIT).count();
        int dividends = (int) actions.stream().filter(a -> a.getActionType() == ActionType.DIVIDEND).count();

        AdjustmentOutcome outcome = applyAdjustments(normalizedTicker, actions);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ticker", normalizedTicker);
        out.put("status", "ok");
        out.put("newActions", newActions);
        out.put("splits", splits);
        out.put("dividends", dividends);
        out.put("pricesUpdated", outcome.pricesUpdated());
        out.put("snappedActions", outcome.snappedActions());
        if (isFund && etfReport != null) {
            out.put("etfDiagnostics", etfReport.toMap());
        }
        if (!isFund && equityReport != null) {
            out.put("equityDiagnostics", equityReport.toMap());
        }
        if (options.validateWithYfinance()) {
            CorporateActionValidationService.ValidationReport validationReport =
                    corporateActionValidationService.validateTicker(normalizedTicker, VALIDATION_MIN_DATE);
            out.put("validationReport", validationReport.toMap());
            out.put("priceValidationReport",
                    adjustedPriceValidationService.validateTicker(normalizedTicker, VALIDATION_MIN_DATE).toMap());
        }
        return out;
    }

    /**
     * Detect and adjust for all tickers that have price data and a corresponding SEC CIK.
     * When {@code options.force()} is false, SEC re-fetch is skipped for tickers whose
     * detection is fresh; when true, SEC data is re-fetched for all tickers.
     */
    public Map<String, Object> adjustAllTickers(AdjustmentOptions options) {
        if (options.etfOnly() && options.equityOnly()) {
            return Map.of(
                    "status", "invalid_args",
                    "message", "etfOnly and equityOnly cannot both be true"
            );
        }

        Set<String> tickersNeedingAdjustment = new HashSet<>(dailyPriceRepository.findTickersWithUnadjustedPrices());
        Set<String> tickersWithExistingActions = new HashSet<>(corporateActionRepository.findDistinctTickers());

        Map<String, Asset> assetByTicker = new HashMap<>();
        Map<String, Listing> listingByTicker = new HashMap<>();
        for (Asset asset : assetRepository.findAllWithListings()) {
            for (Listing listing : asset.getListings()) {
                assetByTicker.putIfAbsent(listing.getTicker(), asset);
                listingByTicker.putIfAbsent(listing.getTicker(), listing);
            }
        }

        Set<String> allPriceTickers = new HashSet<>(dailyPriceRepository.findDistinctTickers());

        Set<String> scheduledDetections = scheduleStaleDetections(allPriceTickers, listingByTicker);

        Set<String> tickersToProcess = new HashSet<>(tickersNeedingAdjustment);
        tickersToProcess.addAll(scheduledDetections);
        if (options.force()) {
            tickersToProcess.addAll(allPriceTickers);
        }

        log.info("Adjust prices: {} total price tickers, {} needing adjustment, {} with existing actions, {} scheduled for re-detection, force={}",
                allPriceTickers.size(), tickersNeedingAdjustment.size(), tickersWithExistingActions.size(),
                scheduledDetections.size(), options.force());

        int tickersProcessed = 0;
        // long: these accumulate Stream.count(), so an int would hide a lossy narrowing cast.
        long totalSplits = 0;
        long totalDividends = 0;
        int totalPricesUpdated = 0;
        int totalSnappedActions = 0;
        int skippedNoAsset = 0;
        int failedTickers = 0;
        int jumpTriggeredDetections = 0;
        List<EtfCorporateActionService.EtfDetectionReport> etfReports = new ArrayList<>();
        List<EquityCorporateActionService.EquityDetectionReport> equityReports = new ArrayList<>();
        List<CorporateActionValidationService.ValidationReport> validationReports = new ArrayList<>();
        List<AdjustedPriceValidationService.PriceValidationReport> priceValidationReports = new ArrayList<>();

        for (String ticker : tickersToProcess) {
            Asset asset = assetByTicker.get(ticker);
            if (asset == null) {
                if (tickersNeedingAdjustment.contains(ticker)) {
                    setRawAsAdjusted(ticker);
                }
                skippedNoAsset++;
                continue;
            }

            try {
                boolean hasExistingActions = tickersWithExistingActions.contains(ticker);
                boolean isFund = Boolean.TRUE.equals(asset.getIsFund());

                if (options.etfOnly() && !isFund) {
                    continue;
                }
                if (options.equityOnly() && isFund) {
                    continue;
                }

                List<CorporateAction> existingActions = hasExistingActions
                        ? corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker)
                        : List.of();
                boolean hasEquityDividends = existingActions.stream()
                        .anyMatch(a -> a.getActionType() == ActionType.DIVIDEND);
                Listing listing = listingByTicker.get(ticker);
                boolean neverDetected = listing == null || listing.getLastSecDetectionAt() == null;
                boolean shouldFetchSec = options.force()
                        || scheduledDetections.contains(ticker)
                        || (!hasExistingActions && neverDetected)
                        || (!isFund && options.validateWithYfinance() && !hasEquityDividends);
                // A huge overnight raw move on freshly loaded prices is the signature of a
                // just-effective split; re-detect immediately instead of waiting for the
                // rolling refresh to reach this ticker.
                if (!shouldFetchSec
                        && tickersNeedingAdjustment.contains(ticker)
                        && hasOvernightPriceJump(ticker)) {
                    jumpTriggeredDetections++;
                    shouldFetchSec = true;
                }

                if (shouldFetchSec) {
                    if (isFund) {
                        EtfCorporateActionService.EtfDetectionReport report =
                                etfCorporateActionService.detectAndPersist(ticker, asset.getCik());
                        etfReports.add(report);
                        stampDetection(listing);
                    } else {
                        EquityCorporateActionService.EquityDetectionReport report =
                                equityCorporateActionService.detectAndPersistWithDiagnostics(ticker, asset.getCik());
                        equityReports.add(report);
                        stampDetectionIfSucceeded(listing, report);
                    }
                    Thread.sleep(100);
                }

                List<CorporateAction> actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker);
                totalSplits += actions.stream().filter(a -> a.getActionType() == ActionType.SPLIT).count();
                totalDividends += actions.stream().filter(a -> a.getActionType() == ActionType.DIVIDEND).count();
                AdjustmentOutcome outcome = applyAdjustments(ticker, actions);
                totalPricesUpdated += outcome.pricesUpdated();
                totalSnappedActions += outcome.snappedActions();
                if (options.validateWithYfinance()) {
                    validationReports.add(corporateActionValidationService.validateTicker(ticker, VALIDATION_MIN_DATE));
                    priceValidationReports.add(adjustedPriceValidationService.validateTicker(ticker, VALIDATION_MIN_DATE));
                }
                tickersProcessed++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Leave adjusted columns untouched (NULL rows stay NULL) so the ticker is
                // retried on the next run instead of freezing raw prices as "adjusted".
                failedTickers++;
                log.warn("[{}] Error during adjustment: {}", ticker, e.getMessage());
            }
        }

        log.info("Adjustment complete. Processed: {}, Skipped (no asset): {}, Failed: {}, Splits: {}, Dividends: {}, Prices updated: {}, Snapped actions: {}",
                tickersProcessed, skippedNoAsset, failedTickers, totalSplits, totalDividends, totalPricesUpdated, totalSnappedActions);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tickersProcessed", tickersProcessed);
        out.put("skippedNoAsset", skippedNoAsset);
        out.put("failedTickers", failedTickers);
        out.put("totalSplits", totalSplits);
        out.put("totalDividends", totalDividends);
        out.put("totalPricesUpdated", totalPricesUpdated);
        out.put("totalSnappedActions", totalSnappedActions);
        out.put("scheduledDetections", scheduledDetections.size());
        out.put("jumpTriggeredDetections", jumpTriggeredDetections);
        out.put("etfDiagnosticsSummary", summarizeEtfDiagnostics(etfReports));
        out.put("equityDiagnosticsSummary", summarizeEquityDiagnostics(equityReports));
        if (options.validateWithYfinance()) {
            out.put("validationSummary", corporateActionValidationService.summarizeBatch(validationReports));
            out.put("priceValidationSummary", adjustedPriceValidationService.summarizeBatch(priceValidationReports));
        }
        return out;
    }

    /**
     * Picks the tickers whose SEC detection is stale (never ran, or older than
     * {@link #SEC_REDETECTION_INTERVAL_DAYS}), oldest first, capped at roughly
     * 1/{@value #SEC_REDETECTION_INTERVAL_DAYS} of the price universe per run so the
     * refresh rolls through every ticker about weekly with bounded runtime and SEC load.
     */
    private Set<String> scheduleStaleDetections(Set<String> allPriceTickers, Map<String, Listing> listingByTicker) {
        LocalDateTime staleCutoff = LocalDateTime.now(MarketTime.STORAGE).minusDays(SEC_REDETECTION_INTERVAL_DAYS);
        List<String> staleCandidates = new ArrayList<>();
        for (String ticker : allPriceTickers) {
            Listing listing = listingByTicker.get(ticker);
            if (listing == null) {
                continue;
            }
            LocalDateTime last = listing.getLastSecDetectionAt();
            if (last == null || last.isBefore(staleCutoff)) {
                staleCandidates.add(ticker);
            }
        }
        staleCandidates.sort(Comparator.comparing(
                ticker -> listingByTicker.get(ticker).getLastSecDetectionAt(),
                Comparator.nullsFirst(Comparator.naturalOrder())));
        int cap = (int) Math.ceil(allPriceTickers.size() / (double) SEC_REDETECTION_INTERVAL_DAYS);
        return new LinkedHashSet<>(staleCandidates.subList(0, Math.min(cap, staleCandidates.size())));
    }

    private boolean isDetectionStale(Listing listing) {
        if (listing == null || listing.getLastSecDetectionAt() == null) {
            return true;
        }
        return listing.getLastSecDetectionAt()
                .isBefore(LocalDateTime.now(MarketTime.STORAGE).minusDays(SEC_REDETECTION_INTERVAL_DAYS));
    }

    private void stampDetection(Listing listing) {
        if (listing == null) {
            return;
        }
        listing.setLastSecDetectionAt(LocalDateTime.now(MarketTime.STORAGE));
        listingRepository.save(listing);
    }

    /**
     * Stamps the detection timestamp only when the SEC fetch actually succeeded; a failed fetch
     * must leave the listing stale so the ticker is retried on the next run instead of waiting
     * out a full re-detection interval with zero actions detected.
     */
    private void stampDetectionIfSucceeded(Listing listing, EquityCorporateActionService.EquityDetectionReport report) {
        if (report != null && report.failureReason() != null) {
            log.warn("[{}] Skipping detection stamp: detection failed ({})", report.ticker(), report.failureReason());
            return;
        }
        stampDetection(listing);
    }

    /**
     * True when any recent overnight raw-close move is extreme. Checks the last few rows, not just
     * the newest pair, so a split buried by a multi-day catch-up load still triggers re-detection.
     */
    private boolean hasOvernightPriceJump(String ticker) {
        List<DailyPrice> latest = dailyPriceRepository.findTop6ByTickerOrderByTradeDateDesc(ticker);
        for (int i = 0; i + 1 < latest.size(); i++) {
            Double newClose = latest.get(i).getClosePrice();
            Double prevClose = latest.get(i + 1).getClosePrice();
            if (newClose == null || prevClose == null || newClose <= 0 || prevClose <= 0) {
                continue;
            }
            double overnightReturn = newClose / prevClose - 1.0;
            if (Math.abs(overnightReturn) > JUMP_REDETECTION_RETURN_THRESHOLD) {
                log.info("[{}] Overnight return {} into {} exceeds jump threshold; forcing SEC re-detection",
                        ticker, String.format(Locale.US, "%.2f%%", overnightReturn * 100),
                        latest.get(i).getTradeDate());
                return true;
            }
        }
        return false;
    }

    /**
     * Apply cumulative adjustment factors to all DailyPrice records for a ticker.
     * Walks from most recent to oldest, accumulating split and dividend factors.
     */
    private AdjustmentOutcome applyAdjustments(String ticker, List<CorporateAction> actions) {
        List<DailyPrice> prices = dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
        if (prices.isEmpty()) return new AdjustmentOutcome(0, 0);

        if (actions.isEmpty()) {
            List<DailyPrice> dirty = new ArrayList<>();
            for (DailyPrice dp : prices) {
                if (applyAdjustedIfChanged(dp, dp.getOpenPrice(), dp.getHighPrice(), dp.getLowPrice(), dp.getClosePrice())) {
                    dirty.add(dp);
                }
            }
            if (!dirty.isEmpty()) {
                dailyPriceRepository.saveAll(dirty);
            }
            return new AdjustmentOutcome(dirty.size(), 0);
        }

        Map<LocalDate, Double> priorTradingCloseByDate = new HashMap<>();
        TreeSet<LocalDate> tradeDates = new TreeSet<>();
        List<DailyPrice> pricesAsc = new ArrayList<>(prices);
        Collections.reverse(pricesAsc);
        Double priorClose = null;
        for (DailyPrice dp : pricesAsc) {
            tradeDates.add(dp.getTradeDate());
            priorTradingCloseByDate.put(dp.getTradeDate(), priorClose);
            priorClose = dp.getClosePrice();
        }

        // Actions dated on non-trading days (weekends, holidays, inferred ex-dates) would
        // never match a price row; snap them forward to the next trade date instead of
        // silently dropping them.
        int snappedActions = 0;
        Map<LocalDate, List<CorporateAction>> actionsByDate = new HashMap<>();
        for (CorporateAction a : actions) {
            if (a.getEffectiveDate() == null) {
                continue;
            }
            LocalDate applyDate = tradeDates.ceiling(a.getEffectiveDate());
            if (applyDate == null) {
                continue; // effective after the newest price row; nothing to adjust yet
            }
            if (!applyDate.equals(a.getEffectiveDate())) {
                snappedActions++;
                log.debug("[{}] {} effective {} snapped to trade date {}",
                        ticker, a.getActionType(), a.getEffectiveDate(), applyDate);
            }
            actionsByDate.computeIfAbsent(applyDate, k -> new ArrayList<>()).add(a);
        }
        for (Map.Entry<LocalDate, List<CorporateAction>> entry : actionsByDate.entrySet()) {
            List<CorporateAction> dayActions = entry.getValue();
            dayActions.sort(Comparator.comparingInt(this::actionPriority));
            boolean hasSplit = dayActions.stream().anyMatch(a -> a.getActionType() == ActionType.SPLIT);
            boolean hasDividend = dayActions.stream().anyMatch(a -> a.getActionType() == ActionType.DIVIDEND);
            if (hasSplit && hasDividend) {
                // The dividend factor divides by the pre-split prior close while the dividend cash
                // may be post-split scale; the combined factor can be off by the split ratio.
                log.warn("[{}] Split and dividend share effective date {}; dividend factor may be mis-scaled",
                        ticker, entry.getKey());
            }
        }

        BigDecimal cumulativeFactor = BigDecimal.ONE;
        List<DailyPrice> dirty = new ArrayList<>();

        for (DailyPrice dp : prices) {
            LocalDate date = dp.getTradeDate();
            double factor = cumulativeFactor.doubleValue();
            if (applyAdjustedIfChanged(dp,
                    round(dp.getOpenPrice() * factor),
                    round(dp.getHighPrice() * factor),
                    round(dp.getLowPrice() * factor),
                    round(dp.getClosePrice() * factor))) {
                dirty.add(dp);
            }

            List<CorporateAction> dayActions = actionsByDate.get(date);
            if (dayActions != null) {
                for (CorporateAction action : dayActions) {
                    if (action.getActionType() == ActionType.SPLIT) {
                        if (action.getRatio() != null && action.getRatio() > 0) {
                            cumulativeFactor = cumulativeFactor.multiply(BigDecimal.valueOf(action.getRatio()), FACTOR_MATH);
                        }
                    } else if (action.getActionType() == ActionType.DIVIDEND) {
                        Double priorTradingClose = priorTradingCloseByDate.get(date);
                        if (priorTradingClose == null || priorTradingClose <= 0) {
                            continue;
                        }
                        double dividendValue = resolveDividendCashForAdjustment(action);
                        if (dividendValue <= 0 || dividendValue >= priorTradingClose) {
                            continue;
                        }
                        BigDecimal dividendFactor = BigDecimal.ONE.subtract(
                                BigDecimal.valueOf(dividendValue).divide(BigDecimal.valueOf(priorTradingClose), FACTOR_MATH),
                                FACTOR_MATH);
                        cumulativeFactor = cumulativeFactor.multiply(dividendFactor, FACTOR_MATH);
                    }
                }
            }
        }

        if (!dirty.isEmpty()) {
            dailyPriceRepository.saveAll(dirty);
        }
        return new AdjustmentOutcome(dirty.size(), snappedActions);
    }

    private record AdjustmentOutcome(int pricesUpdated, int snappedActions) {
    }

    /**
     * Applies the computed adjusted OHLC to the row only when a value actually differs, so the
     * nightly run writes just the new rows (and any rows whose factors changed) instead of
     * rewriting the entire price history for every ticker.
     */
    private boolean applyAdjustedIfChanged(DailyPrice dp, Double open, Double high, Double low, Double close) {
        boolean changed = !Objects.equals(dp.getAdjustedOpen(), open)
                || !Objects.equals(dp.getAdjustedHigh(), high)
                || !Objects.equals(dp.getAdjustedLow(), low)
                || !Objects.equals(dp.getAdjustedClose(), close);
        if (changed) {
            dp.setAdjustedOpen(open);
            dp.setAdjustedHigh(high);
            dp.setAdjustedLow(low);
            dp.setAdjustedClose(close);
        }
        return changed;
    }

    /** For tickers without SEC data, set adjusted = raw. */
    private void setRawAsAdjusted(String ticker) {
        List<DailyPrice> prices = dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
        List<DailyPrice> dirty = new ArrayList<>();
        for (DailyPrice dp : prices) {
            if (applyAdjustedIfChanged(dp, dp.getOpenPrice(), dp.getHighPrice(), dp.getLowPrice(), dp.getClosePrice())) {
                dirty.add(dp);
            }
        }
        if (!dirty.isEmpty()) {
            dailyPriceRepository.saveAll(dirty);
        }
    }

    private Double round(Double val) {
        if (val == null) return null;
        return Math.round(val * 10000.0) / 10000.0;
    }

    /**
     * Cash dividend per share to use in {@code 1 - dividend/priorRawClose} factors.
     * Must match the scale of {@link DailyPrice#getClosePrice()} (raw, as-of ex-date).
     * Prefers {@link CorporateAction#getRawDividend()}; falls back to adjusted/ratio for ETF rows
     * or legacy rows where raw was not persisted.
     */
    private double resolveDividendCashForAdjustment(CorporateAction action) {
        if (action.getRawDividend() != null && action.getRawDividend() > 0) {
            return action.getRawDividend();
        }
        if (action.getAdjustedDividend() != null && action.getAdjustedDividend() > 0) {
            return action.getAdjustedDividend();
        }
        if (action.getRatio() != null && action.getRatio() > 0) {
            return action.getRatio();
        }
        return 0.0;
    }

    private int actionPriority(CorporateAction action) {
        if (action.getActionType() == ActionType.SPLIT) {
            return 0;
        }
        if (action.getActionType() == ActionType.DIVIDEND) {
            return 1;
        }
        return 2;
    }

    private Map<String, Object> summarizeEtfDiagnostics(List<EtfCorporateActionService.EtfDetectionReport> reports) {
        Map<String, Integer> skipReasons = new LinkedHashMap<>();
        List<Map<String, Object>> sampleSkips = new ArrayList<>();
        List<Map<String, Object>> sampleCreated = new ArrayList<>();
        int filingsConsidered = 0;
        int filingsFetched = 0;
        int identityMatched = 0;
        int amountExtracted = 0;
        int dateExtracted = 0;
        int belowConfidence = 0;
        int duplicates = 0;
        int saved = 0;
        int candidateDocumentsScanned = 0;
        Map<String, Integer> identityScoreBuckets = new LinkedHashMap<>();
        Map<String, Integer> amountSourceCounts = new LinkedHashMap<>();
        Map<String, Integer> dateResolutionPathCounts = new LinkedHashMap<>();
        Map<String, Integer> dateSourceCounts = new LinkedHashMap<>();
        for (EtfCorporateActionService.EtfDetectionReport report : reports) {
            filingsConsidered += report.filingsConsidered();
            filingsFetched += report.filingsFetched();
            identityMatched += report.identityMatched();
            amountExtracted += report.amountExtracted();
            dateExtracted += report.dateExtracted();
            belowConfidence += report.belowConfidence();
            duplicates += report.duplicates();
            saved += report.saved();
            candidateDocumentsScanned += report.candidateDocumentsScanned();
            mergeCounter(identityScoreBuckets, report.identityScoreBuckets());
            mergeCounter(amountSourceCounts, report.amountSourceCounts());
            mergeCounter(dateResolutionPathCounts, report.dateResolutionPathCounts());
            mergeCounter(dateSourceCounts, report.dateSourceCounts());
            report.skipReasons().forEach((reason, count) -> skipReasons.merge(reason, count, Integer::sum));
            if (sampleSkips.size() < 10) {
                for (Map<String, Object> row : report.sampleSkips()) {
                    if (sampleSkips.size() >= 10) {
                        break;
                    }
                    sampleSkips.add(row);
                }
            }
            if (sampleCreated.size() < 10) {
                for (Map<String, Object> row : report.sampleCreated()) {
                    if (sampleCreated.size() >= 10) {
                        break;
                    }
                    sampleCreated.add(row);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fundTickersScanned", reports.size());
        out.put("filingsConsidered", filingsConsidered);
        out.put("filingsFetched", filingsFetched);
        out.put("identityMatched", identityMatched);
        out.put("amountExtracted", amountExtracted);
        out.put("dateExtracted", dateExtracted);
        out.put("belowConfidence", belowConfidence);
        out.put("duplicates", duplicates);
        out.put("saved", saved);
        out.put("candidateDocumentsScanned", candidateDocumentsScanned);
        out.put("identityScoreBuckets", identityScoreBuckets);
        out.put("amountSourceCounts", amountSourceCounts);
        out.put("dateResolutionPathCounts", dateResolutionPathCounts);
        out.put("dateSourceCounts", dateSourceCounts);
        out.put("skipReasons", skipReasons);
        out.put("sampleSkips", sampleSkips);
        out.put("sampleCreated", sampleCreated);
        return out;
    }

    private void mergeCounter(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((key, value) -> target.merge(key, value, Integer::sum));
    }

    private Map<String, Object> summarizeEquityDiagnostics(List<EquityCorporateActionService.EquityDetectionReport> reports) {
        Map<String, Integer> failureReasons = new LinkedHashMap<>();
        int tickersScanned = reports.size();
        int savedActions = 0;
        int splitFactsParsed = 0;
        int splitCreated = 0;
        int splitSecDateMatches = 0;
        int splitFallbackDetectedDate = 0;
        int splitPriceCorroborated = 0;
        int splitPriceSnapped = 0;
        int splitPriceRejected = 0;
        int splitPriceOnlyDetected = 0;
        int splitPriceOnlyUnconfirmed = 0;
        int dividendFactsParsed = 0;
        int normalizedEvents = 0;
        int recordDateCandidates = 0;
        int declarationTuples = 0;
        int exDateFromRecordPath = 0;
        int exDateFallbackPath = 0;
        int tupleMatchedAssignments = 0;
        int directExAssignments = 0;
        int dpAssignments = 0;
        int syntheticAssignments = 0;
        int promotedTupleEvents = 0;
        int dividendChanged = 0;
        int dividendInserted = 0;
        int dividendUpdated = 0;
        int scanFailedFilings = 0;
        int scanDegradedTickers = 0;
        for (EquityCorporateActionService.EquityDetectionReport report : reports) {
            savedActions += report.savedActions();
            if (report.failureReason() != null && !report.failureReason().isBlank()) {
                failureReasons.merge(report.failureReason(), 1, Integer::sum);
            }
            EquityCorporateActionService.SplitDetectionStats split = report.split();
            splitFactsParsed += split.sharesFactsParsed();
            splitCreated += split.created();
            splitSecDateMatches += split.secDateMatches();
            splitFallbackDetectedDate += split.fallbackDetectedDate();
            splitPriceCorroborated += split.priceCorroborated();
            splitPriceSnapped += split.priceSnapped();
            splitPriceRejected += split.priceRejected();
            splitPriceOnlyDetected += split.priceOnlyDetected();
            splitPriceOnlyUnconfirmed += split.priceOnlyUnconfirmed();
            EquityCorporateActionService.DividendDetectionStats dividend = report.dividend();
            dividendFactsParsed += dividend.factsParsed();
            normalizedEvents += dividend.normalizedEvents();
            recordDateCandidates += dividend.recordDateCandidates();
            declarationTuples += dividend.declarationTuples();
            exDateFromRecordPath += dividend.exDateFromRecordPath();
            exDateFallbackPath += dividend.exDateFallbackPath();
            tupleMatchedAssignments += dividend.tupleMatchedAssignments();
            directExAssignments += dividend.directExAssignments();
            dpAssignments += dividend.dpAssignments();
            syntheticAssignments += dividend.syntheticAssignments();
            promotedTupleEvents += dividend.promotedTupleEvents();
            dividendChanged += dividend.changed();
            dividendInserted += dividend.inserted();
            dividendUpdated += dividend.updated();
            scanFailedFilings += dividend.scanFailedFilings();
            if (dividend.scanDegraded()) {
                scanDegradedTickers++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equityTickersScanned", tickersScanned);
        out.put("savedActions", savedActions);
        out.put("failureReasons", failureReasons);
        out.put("splitFactsParsed", splitFactsParsed);
        out.put("splitCreated", splitCreated);
        out.put("splitSecDateMatches", splitSecDateMatches);
        out.put("splitFallbackDetectedDate", splitFallbackDetectedDate);
        out.put("splitPriceCorroborated", splitPriceCorroborated);
        out.put("splitPriceSnapped", splitPriceSnapped);
        out.put("splitPriceRejected", splitPriceRejected);
        out.put("splitPriceOnlyDetected", splitPriceOnlyDetected);
        out.put("splitPriceOnlyUnconfirmed", splitPriceOnlyUnconfirmed);
        out.put("dividendFactsParsed", dividendFactsParsed);
        out.put("normalizedEvents", normalizedEvents);
        out.put("recordDateCandidates", recordDateCandidates);
        out.put("declarationTuples", declarationTuples);
        out.put("exDateFromRecordPath", exDateFromRecordPath);
        out.put("exDateFallbackPath", exDateFallbackPath);
        out.put("tupleMatchedAssignments", tupleMatchedAssignments);
        out.put("directExAssignments", directExAssignments);
        out.put("dpAssignments", dpAssignments);
        out.put("syntheticAssignments", syntheticAssignments);
        out.put("promotedTupleEvents", promotedTupleEvents);
        out.put("dividendChanged", dividendChanged);
        out.put("dividendInserted", dividendInserted);
        out.put("dividendUpdated", dividendUpdated);
        out.put("scanFailedFilings", scanFailedFilings);
        out.put("scanDegradedTickers", scanDegradedTickers);
        return out;
    }
}

package com.fattorestreet.sec_api.corporateaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.*;

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

import java.time.LocalDateTime;

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
     * Detect corporate actions from SEC and adjust prices for a single ticker.
     * @return summary map
     */
    public Map<String, Object> adjustTicker(String ticker) {
        return adjustTicker(ticker, false, false, false, false);
    }

    public Map<String, Object> adjustTicker(
            String ticker,
            boolean force,
            boolean etfOnly,
            boolean equityOnly) {
        return adjustTicker(ticker, force, etfOnly, equityOnly, false);
    }

    public Map<String, Object> adjustTicker(
            String ticker,
            boolean force,
            boolean etfOnly,
            boolean equityOnly,
            boolean validateWithYfinance) {
        if (etfOnly && equityOnly) {
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
        if (etfOnly && !isFund) {
            return Map.of("ticker", normalizedTicker, "status", "skipped_mode", "pricesUpdated", 0);
        }
        if (equityOnly && isFund) {
            return Map.of("ticker", normalizedTicker, "status", "skipped_mode", "pricesUpdated", 0);
        }

        List<CorporateAction> actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(normalizedTicker);
        boolean hasEquityDividends = actions.stream().anyMatch(a -> a.getActionType() == ActionType.DIVIDEND);
        Listing listing = listingRepository.findByTicker(normalizedTicker).orElse(null);
        boolean shouldFetchSec = force
                || actions.isEmpty()
                || isDetectionStale(listing)
                || (!isFund && validateWithYfinance && !hasEquityDividends)
                || hasOvernightPriceJump(normalizedTicker);
        int newActions;
        EtfCorporateActionService.EtfDetectionReport etfReport = null;
        EquityCorporateActionService.EquityDetectionReport equityReport = null;
        if (shouldFetchSec) {
            if (isFund) {
                etfReport = etfCorporateActionService.detectAndPersist(normalizedTicker, asset.getCik());
                newActions = etfReport.saved();
            } else {
                equityReport = equityCorporateActionService.detectAndPersistWithDiagnostics(normalizedTicker, asset.getCik());
                newActions = equityReport.savedActions();
            }
            stampDetection(listing);
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
        if (validateWithYfinance) {
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
     *
     * @param force when false, skips SEC re-fetch for tickers that already have corporate
     *              actions detected; when true, re-fetches SEC data for all tickers to
     *              catch new splits/dividends
     */
    public Map<String, Object> adjustAllTickers(boolean force) {
        return adjustAllTickers(force, false, false, false);
    }

    public Map<String, Object> adjustAllTickers(boolean force, boolean etfOnly, boolean equityOnly) {
        return adjustAllTickers(force, etfOnly, equityOnly, false);
    }

    public Map<String, Object> adjustAllTickers(
            boolean force,
            boolean etfOnly,
            boolean equityOnly,
            boolean validateWithYfinance) {
        if (etfOnly && equityOnly) {
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
        if (force) {
            tickersToProcess.addAll(allPriceTickers);
        }

        log.info("Adjust prices: {} total price tickers, {} needing adjustment, {} with existing actions, {} scheduled for re-detection, force={}",
                allPriceTickers.size(), tickersNeedingAdjustment.size(), tickersWithExistingActions.size(),
                scheduledDetections.size(), force);

        int tickersProcessed = 0;
        int totalSplits = 0;
        int totalDividends = 0;
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

                if (etfOnly && !isFund) {
                    continue;
                }
                if (equityOnly && isFund) {
                    continue;
                }

                List<CorporateAction> existingActions = hasExistingActions
                        ? corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker)
                        : List.of();
                boolean hasEquityDividends = existingActions.stream()
                        .anyMatch(a -> a.getActionType() == ActionType.DIVIDEND);
                Listing listing = listingByTicker.get(ticker);
                boolean neverDetected = listing == null || listing.getLastSecDetectionAt() == null;
                boolean shouldFetchSec = force
                        || scheduledDetections.contains(ticker)
                        || (!hasExistingActions && neverDetected)
                        || (!isFund && validateWithYfinance && !hasEquityDividends);
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
                    } else {
                        EquityCorporateActionService.EquityDetectionReport report =
                                equityCorporateActionService.detectAndPersistWithDiagnostics(ticker, asset.getCik());
                        equityReports.add(report);
                    }
                    stampDetection(listing);
                    Thread.sleep(100);
                }

                List<CorporateAction> actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker);
                totalSplits += actions.stream().filter(a -> a.getActionType() == ActionType.SPLIT).count();
                totalDividends += actions.stream().filter(a -> a.getActionType() == ActionType.DIVIDEND).count();
                AdjustmentOutcome outcome = applyAdjustments(ticker, actions);
                totalPricesUpdated += outcome.pricesUpdated();
                totalSnappedActions += outcome.snappedActions();
                if (validateWithYfinance) {
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
        if (validateWithYfinance) {
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
        LocalDateTime staleCutoff = LocalDateTime.now().minusDays(SEC_REDETECTION_INTERVAL_DAYS);
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
        return listing.getLastSecDetectionAt().isBefore(LocalDateTime.now().minusDays(SEC_REDETECTION_INTERVAL_DAYS));
    }

    private void stampDetection(Listing listing) {
        if (listing == null) {
            return;
        }
        listing.setLastSecDetectionAt(LocalDateTime.now());
        listingRepository.save(listing);
    }

    /** True when the two most recent raw closes imply an extreme overnight move. */
    private boolean hasOvernightPriceJump(String ticker) {
        List<DailyPrice> latest = dailyPriceRepository.findTop2ByTickerOrderByTradeDateDesc(ticker);
        if (latest.size() < 2) {
            return false;
        }
        Double newClose = latest.get(0).getClosePrice();
        Double prevClose = latest.get(1).getClosePrice();
        if (newClose == null || prevClose == null || newClose <= 0 || prevClose <= 0) {
            return false;
        }
        double overnightReturn = newClose / prevClose - 1.0;
        if (Math.abs(overnightReturn) > JUMP_REDETECTION_RETURN_THRESHOLD) {
            log.info("[{}] Overnight return {} exceeds jump threshold; forcing SEC re-detection",
                    ticker, String.format(Locale.US, "%.2f%%", overnightReturn * 100));
            return true;
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
            for (DailyPrice dp : prices) {
                dp.setAdjustedOpen(dp.getOpenPrice());
                dp.setAdjustedHigh(dp.getHighPrice());
                dp.setAdjustedLow(dp.getLowPrice());
                dp.setAdjustedClose(dp.getClosePrice());
            }
            dailyPriceRepository.saveAll(prices);
            return new AdjustmentOutcome(prices.size(), 0);
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
        for (List<CorporateAction> dayActions : actionsByDate.values()) {
            dayActions.sort(Comparator.comparingInt(this::actionPriority));
        }

        BigDecimal cumulativeFactor = BigDecimal.ONE;

        for (DailyPrice dp : prices) {
            LocalDate date = dp.getTradeDate();
            double factor = cumulativeFactor.doubleValue();
            dp.setAdjustedOpen(round(dp.getOpenPrice() * factor));
            dp.setAdjustedHigh(round(dp.getHighPrice() * factor));
            dp.setAdjustedLow(round(dp.getLowPrice() * factor));
            dp.setAdjustedClose(round(dp.getClosePrice() * factor));

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

        dailyPriceRepository.saveAll(prices);
        return new AdjustmentOutcome(prices.size(), snappedActions);
    }

    private record AdjustmentOutcome(int pricesUpdated, int snappedActions) {
    }

    /** For tickers without SEC data, set adjusted = raw. */
    private void setRawAsAdjusted(String ticker) {
        List<DailyPrice> prices = dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
        for (DailyPrice dp : prices) {
            dp.setAdjustedOpen(dp.getOpenPrice());
            dp.setAdjustedHigh(dp.getHighPrice());
            dp.setAdjustedLow(dp.getLowPrice());
            dp.setAdjustedClose(dp.getClosePrice());
        }
        if (!prices.isEmpty()) {
            dailyPriceRepository.saveAll(prices);
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
            Map<String, Object> reportMap = report.toMap();
            candidateDocumentsScanned += ((Number) reportMap.getOrDefault("candidateDocumentsScanned", 0)).intValue();
            mergeCounter(identityScoreBuckets, castCounter(reportMap.get("identityScoreBuckets")));
            mergeCounter(amountSourceCounts, castCounter(reportMap.get("amountSourceCounts")));
            mergeCounter(dateResolutionPathCounts, castCounter(reportMap.get("dateResolutionPathCounts")));
            mergeCounter(dateSourceCounts, castCounter(reportMap.get("dateSourceCounts")));
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

    private Map<String, Integer> castCounter(Object raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Number value)) {
                continue;
            }
            out.put(key, value.intValue());
        }
        return out;
    }

    private void mergeCounter(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((key, value) -> target.merge(key, value, Integer::sum));
    }

    private int getInt(Map<?, ?> source, String key) {
        if (source == null) {
            return 0;
        }
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
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
        int dividendChanged = 0;
        int dividendInserted = 0;
        int dividendUpdated = 0;
        for (EquityCorporateActionService.EquityDetectionReport report : reports) {
            Map<String, Object> row = report.toMap();
            savedActions += (int) row.getOrDefault("savedActions", 0);
            Object failureReason = row.get("failureReason");
            if (failureReason instanceof String reason && !reason.isBlank()) {
                failureReasons.merge(reason, 1, Integer::sum);
            }
            Map<?, ?> split = (Map<?, ?>) row.get("split");
            splitFactsParsed += getInt(split, "sharesFactsParsed");
            splitCreated += getInt(split, "created");
            splitSecDateMatches += getInt(split, "secDateMatches");
            splitFallbackDetectedDate += getInt(split, "fallbackDetectedDate");
            splitPriceCorroborated += getInt(split, "priceCorroborated");
            splitPriceSnapped += getInt(split, "priceSnapped");
            splitPriceRejected += getInt(split, "priceRejected");
            splitPriceOnlyDetected += getInt(split, "priceOnlyDetected");
            splitPriceOnlyUnconfirmed += getInt(split, "priceOnlyUnconfirmed");
            Map<?, ?> dividend = (Map<?, ?>) row.get("dividend");
            dividendFactsParsed += getInt(dividend, "factsParsed");
            normalizedEvents += getInt(dividend, "normalizedEvents");
            recordDateCandidates += getInt(dividend, "recordDateCandidates");
            declarationTuples += getInt(dividend, "declarationTuples");
            exDateFromRecordPath += getInt(dividend, "exDateFromRecordPath");
            exDateFallbackPath += getInt(dividend, "exDateFallbackPath");
            tupleMatchedAssignments += getInt(dividend, "tupleMatchedAssignments");
            directExAssignments += getInt(dividend, "directExAssignments");
            dpAssignments += getInt(dividend, "dpAssignments");
            syntheticAssignments += getInt(dividend, "syntheticAssignments");
            dividendChanged += getInt(dividend, "changed");
            dividendInserted += getInt(dividend, "inserted");
            dividendUpdated += getInt(dividend, "updated");
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
        out.put("dividendChanged", dividendChanged);
        out.put("dividendInserted", dividendInserted);
        out.put("dividendUpdated", dividendUpdated);
        return out;
    }
}

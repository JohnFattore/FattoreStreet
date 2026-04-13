package com.fattorestreet.sec_api.corporateaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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

@Service
public class PriceAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(PriceAdjustmentService.class);
    private static final MathContext FACTOR_MATH = MathContext.DECIMAL64;

    private final DailyPriceRepository dailyPriceRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final AssetRepository assetRepository;
    private final EquityCorporateActionService equityCorporateActionService;
    private final EtfCorporateActionService etfCorporateActionService;
    private final CorporateActionValidationService corporateActionValidationService;

    public PriceAdjustmentService(DailyPriceRepository dailyPriceRepository,
                                  CorporateActionRepository corporateActionRepository,
                                  AssetRepository assetRepository,
                                  EquityCorporateActionService equityCorporateActionService,
                                  EtfCorporateActionService etfCorporateActionService,
                                  CorporateActionValidationService corporateActionValidationService) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.assetRepository = assetRepository;
        this.equityCorporateActionService = equityCorporateActionService;
        this.etfCorporateActionService = etfCorporateActionService;
        this.corporateActionValidationService = corporateActionValidationService;
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
        boolean shouldFetchSec = force
                || actions.isEmpty()
                || (!isFund && validateWithYfinance && !hasEquityDividends);
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
            actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(normalizedTicker);
        } else {
            newActions = 0;
        }
        int splits = (int) actions.stream().filter(a -> a.getActionType() == ActionType.SPLIT).count();
        int dividends = (int) actions.stream().filter(a -> a.getActionType() == ActionType.DIVIDEND).count();

        int updated = applyAdjustments(normalizedTicker, actions);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ticker", normalizedTicker);
        out.put("status", "ok");
        out.put("newActions", newActions);
        out.put("splits", splits);
        out.put("dividends", dividends);
        out.put("pricesUpdated", updated);
        if (isFund && etfReport != null) {
            out.put("etfDiagnostics", etfReport.toMap());
        }
        if (!isFund && equityReport != null) {
            out.put("equityDiagnostics", equityReport.toMap());
        }
        if (validateWithYfinance) {
            CorporateActionValidationService.ValidationReport validationReport =
                    corporateActionValidationService.validateTicker(normalizedTicker, LocalDate.of(2016, 1, 1));
            out.put("validationReport", validationReport.toMap());
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

        Map<String, Asset> assetByTicker = assetRepository.findAllWithListings().stream()
                .flatMap(a -> a.getListings().stream()
                        .map(listing -> Map.entry(listing.getTicker(), a)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

        Set<String> allPriceTickers = new HashSet<>(dailyPriceRepository.findDistinctTickers());

        Set<String> tickersToProcess = new HashSet<>(tickersNeedingAdjustment);
        if (force) {
            tickersToProcess.addAll(allPriceTickers);
        }

        log.info("Adjust prices: {} total price tickers, {} needing adjustment, {} with existing actions, force={}",
                allPriceTickers.size(), tickersNeedingAdjustment.size(), tickersWithExistingActions.size(), force);

        int tickersProcessed = 0;
        int totalSplits = 0;
        int totalDividends = 0;
        int totalPricesUpdated = 0;
        int skippedNoAsset = 0;
        List<EtfCorporateActionService.EtfDetectionReport> etfReports = new ArrayList<>();
        List<EquityCorporateActionService.EquityDetectionReport> equityReports = new ArrayList<>();
        List<CorporateActionValidationService.ValidationReport> validationReports = new ArrayList<>();

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
                List<CorporateAction> existingActions = hasExistingActions
                        ? corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker)
                        : List.of();
                boolean hasEquityDividends = existingActions.stream()
                        .anyMatch(a -> a.getActionType() == ActionType.DIVIDEND);
                boolean shouldFetchSec = force
                        || !hasExistingActions
                        || (!isFund && validateWithYfinance && !hasEquityDividends);

                if (etfOnly && !isFund) {
                    continue;
                }
                if (equityOnly && isFund) {
                    continue;
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
                    Thread.sleep(100);
                }

                List<CorporateAction> actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker);
                totalSplits += actions.stream().filter(a -> a.getActionType() == ActionType.SPLIT).count();
                totalDividends += actions.stream().filter(a -> a.getActionType() == ActionType.DIVIDEND).count();
                totalPricesUpdated += applyAdjustments(ticker, actions);
                if (validateWithYfinance) {
                    validationReports.add(corporateActionValidationService.validateTicker(ticker, LocalDate.of(2016, 1, 1)));
                }
                tickersProcessed++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[{}] Error during adjustment: {}", ticker, e.getMessage());
                if (tickersNeedingAdjustment.contains(ticker)) {
                    setRawAsAdjusted(ticker);
                }
            }
        }

        log.info("Adjustment complete. Processed: {}, Skipped (no asset): {}, Splits: {}, Dividends: {}, Prices updated: {}",
                tickersProcessed, skippedNoAsset, totalSplits, totalDividends, totalPricesUpdated);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tickersProcessed", tickersProcessed);
        out.put("skippedNoAsset", skippedNoAsset);
        out.put("totalSplits", totalSplits);
        out.put("totalDividends", totalDividends);
        out.put("totalPricesUpdated", totalPricesUpdated);
        out.put("etfDiagnosticsSummary", summarizeEtfDiagnostics(etfReports));
        out.put("equityDiagnosticsSummary", summarizeEquityDiagnostics(equityReports));
        if (validateWithYfinance) {
            out.put("validationSummary", corporateActionValidationService.summarizeBatch(validationReports));
        }
        return out;
    }

    /**
     * Apply cumulative adjustment factors to all DailyPrice records for a ticker.
     * Walks from most recent to oldest, accumulating split and dividend factors.
     */
    private int applyAdjustments(String ticker, List<CorporateAction> actions) {
        List<DailyPrice> prices = dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
        if (prices.isEmpty()) return 0;

        if (actions.isEmpty()) {
            for (DailyPrice dp : prices) {
                dp.setAdjustedOpen(dp.getOpenPrice());
                dp.setAdjustedHigh(dp.getHighPrice());
                dp.setAdjustedLow(dp.getLowPrice());
                dp.setAdjustedClose(dp.getClosePrice());
            }
            dailyPriceRepository.saveAll(prices);
            return prices.size();
        }

        Map<LocalDate, List<CorporateAction>> actionsByDate = new HashMap<>();
        for (CorporateAction a : actions) {
            actionsByDate.computeIfAbsent(a.getEffectiveDate(), k -> new ArrayList<>()).add(a);
        }
        for (List<CorporateAction> dayActions : actionsByDate.values()) {
            dayActions.sort(Comparator.comparingInt(this::actionPriority));
        }

        Map<LocalDate, Double> priorTradingCloseByDate = new HashMap<>();
        List<DailyPrice> pricesAsc = new ArrayList<>(prices);
        Collections.reverse(pricesAsc);
        Double priorClose = null;
        for (DailyPrice dp : pricesAsc) {
            priorTradingCloseByDate.put(dp.getTradeDate(), priorClose);
            priorClose = dp.getClosePrice();
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
        return prices.size();
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
        int dividendFactsParsed = 0;
        int normalizedEvents = 0;
        int recordDateCandidates = 0;
        int exDateFromRecordPath = 0;
        int exDateFallbackPath = 0;
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
            Map<?, ?> dividend = (Map<?, ?>) row.get("dividend");
            dividendFactsParsed += getInt(dividend, "factsParsed");
            normalizedEvents += getInt(dividend, "normalizedEvents");
            recordDateCandidates += getInt(dividend, "recordDateCandidates");
            exDateFromRecordPath += getInt(dividend, "exDateFromRecordPath");
            exDateFallbackPath += getInt(dividend, "exDateFallbackPath");
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
        out.put("dividendFactsParsed", dividendFactsParsed);
        out.put("normalizedEvents", normalizedEvents);
        out.put("recordDateCandidates", recordDateCandidates);
        out.put("exDateFromRecordPath", exDateFromRecordPath);
        out.put("exDateFallbackPath", exDateFallbackPath);
        out.put("dividendChanged", dividendChanged);
        out.put("dividendInserted", dividendInserted);
        out.put("dividendUpdated", dividendUpdated);
        return out;
    }
}

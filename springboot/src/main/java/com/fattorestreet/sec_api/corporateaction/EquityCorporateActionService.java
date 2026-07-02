package com.fattorestreet.sec_api.corporateaction;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.corporateaction.support.EquityDividendFactParser;
import com.fattorestreet.sec_api.corporateaction.support.EquityDividendNormalizer;
import com.fattorestreet.sec_api.corporateaction.support.EquityDividendUpserter;
import com.fattorestreet.sec_api.corporateaction.support.EquityExDateAssigner;
import com.fattorestreet.sec_api.corporateaction.support.EquitySplitDetector;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EquityCorporateActionService {

    private static final Logger log = LoggerFactory.getLogger(EquityCorporateActionService.class);

    private final WebService webService;
    private final CorporateActionFilingDateService corporateActionFilingDateService;
    private final CorporateActionRepository corporateActionRepository;
    private final ObjectMapper mapper;
    private final EquitySplitDetector equitySplitDetector;
    private final EquityDividendFactParser equityDividendFactParser;
    private final EquityDividendNormalizer equityDividendNormalizer;
    private final EquityExDateAssigner equityExDateAssigner;
    private final EquityDividendUpserter equityDividendUpserter;

    public EquityCorporateActionService(WebService webService,
                                        CorporateActionFilingDateService corporateActionFilingDateService,
                                        CorporateActionRepository corporateActionRepository,
                                        ObjectMapper mapper) {
        this.webService = webService;
        this.corporateActionFilingDateService = corporateActionFilingDateService;
        this.corporateActionRepository = corporateActionRepository;
        this.mapper = mapper;
        this.equitySplitDetector = new EquitySplitDetector(corporateActionFilingDateService, corporateActionRepository);
        this.equityDividendFactParser = new EquityDividendFactParser();
        this.equityDividendNormalizer = new EquityDividendNormalizer();
        this.equityExDateAssigner = new EquityExDateAssigner(corporateActionFilingDateService);
        this.equityDividendUpserter = new EquityDividendUpserter(corporateActionRepository);
    }

    /**
     * Fetch SEC company facts for a CIK, detect splits and dividends,
     * and persist any new CorporateAction records for the given ticker.
     * @return count of new actions persisted
     */
    public int detectAndPersist(String ticker, Long cik) {
        return detectAndPersistWithDiagnostics(ticker, cik).savedActions();
    }

    public EquityDetectionReport detectAndPersistWithDiagnostics(String ticker, Long cik) {
        webService.beginSecTickerScopedCache();
        try {
            JsonNode root;
            try {
                String json = webService.fetchFinancials(cik);
                root = mapper.readTree(json);
            } catch (Exception e) {
                log.warn("[{}] Failed to fetch SEC facts for CIK {}: {}", ticker, cik, e.getMessage());
                return EquityDetectionReport.failed(ticker, cik, "sec_fetch_failed");
            }

            SplitDetectionStats splitStats = equitySplitDetector.detectSplits(ticker, cik, root);
            DividendDetectionStats dividendStats = detectDividends(ticker, cik, root);
            return new EquityDetectionReport(ticker, cik, splitStats, dividendStats, null);
        } finally {
            webService.endSecTickerScopedCache();
        }
    }

    private DividendDetectionStats detectDividends(String ticker, Long cik, JsonNode root) {
        List<DividendFact> facts = equityDividendFactParser.parseDividendFacts(root, ticker);
        List<DividendEvent> normalized = equityDividendNormalizer.normalizeDividendFacts(facts);
        if (normalized.isEmpty()) {
            return DividendDetectionStats.empty(facts.size());
        }

        CorporateActionFilingDateService.RecordDateScanResult recordDateScan = corporateActionFilingDateService.scanDividendRecordDates(cik);
        List<CorporateActionFilingDateService.RecordDateCandidate> recordDates = recordDateScan != null
                ? recordDateScan.candidates()
                : corporateActionFilingDateService.fetchDividendRecordDates(cik);
        List<CorporateActionFilingDateService.ExDividendDateCandidate> exDividendDirect = recordDateScan != null
                ? recordDateScan.exDividendDirectCandidates()
                : List.of();
        Map<String, Integer> discoveredForms = recordDateScan != null ? recordDateScan.discoveredByForm() : Map.of();
        Map<String, Integer> selectedForms = recordDateScan != null ? recordDateScan.selectedByForm() : Map.of();
        Map<String, Integer> rejectedForms = recordDateScan != null ? recordDateScan.rejectedByForm() : Map.of();
        log.info("[{}] Dividend detection inputs: {} SEC facts, {} normalized events, {} record-date candidates, {} direct ex-date candidates",
                ticker, facts.size(), normalized.size(), recordDates.size(), exDividendDirect.size());
        AssignmentResult assignmentResult = equityExDateAssigner.assignExDividendDates(normalized, recordDates, exDividendDirect);
        List<DividendEvent> detectedEvents = assignmentResult.events();
        List<CorporateAction> corporateActions = corporateActionRepository.findByTicker(ticker);
        List<DividendEvent> splitAdjustedEvents = equityDividendNormalizer.adjustDividendsForFutureSplits(detectedEvents, corporateActions);

        UpsertStats upsertStats = equityDividendUpserter.upsertDividendEvents(ticker, splitAdjustedEvents);
        if (upsertStats.changed() > 0) {
            log.info("[{}] Detected/Reconciled {} dividend entries (inserted={} updated={} pruned={})",
                    ticker, upsertStats.changed(), upsertStats.inserted(), upsertStats.updated(), upsertStats.pruned());
        }
        return new DividendDetectionStats(
                facts.size(),
                normalized.size(),
                recordDates.size(),
                assignmentResult.recordBasedAssignments(),
                assignmentResult.fallbackAssignments(),
                upsertStats.changed(),
                upsertStats.inserted(),
                upsertStats.updated(),
                discoveredForms,
                selectedForms,
                rejectedForms);
    }

    public static class EquityDetectionReport {
        private final String ticker;
        private final Long cik;
        private final SplitDetectionStats split;
        private final DividendDetectionStats dividend;
        private final String failureReason;

        public EquityDetectionReport(
                String ticker,
                Long cik,
                SplitDetectionStats split,
                DividendDetectionStats dividend,
                String failureReason) {
            this.ticker = ticker;
            this.cik = cik;
            this.split = split;
            this.dividend = dividend;
            this.failureReason = failureReason;
        }

        public static EquityDetectionReport failed(String ticker, Long cik, String failureReason) {
            return new EquityDetectionReport(
                    ticker,
                    cik,
                    SplitDetectionStats.empty(),
                    DividendDetectionStats.empty(0),
                    failureReason);
        }

        public int savedActions() {
            return split.created() + dividend.changed();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ticker", ticker);
            out.put("cik", cik);
            out.put("failureReason", failureReason);
            out.put("savedActions", savedActions());
            out.put("split", split.toMap());
            out.put("dividend", dividend.toMap());
            return out;
        }
    }

    public static record SplitDetectionStats(
            int sharesFactsParsed,
            int splitDateCandidates,
            int created,
            int secDateMatches,
            int fallbackDetectedDate) {
        private static SplitDetectionStats empty() {
            return new SplitDetectionStats(0, 0, 0, 0, 0);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sharesFactsParsed", sharesFactsParsed);
            out.put("splitDateCandidates", splitDateCandidates);
            out.put("created", created);
            out.put("secDateMatches", secDateMatches);
            out.put("fallbackDetectedDate", fallbackDetectedDate);
            return out;
        }
    }

    public static record DividendDetectionStats(
            int factsParsed,
            int normalizedEvents,
            int recordDateCandidates,
            int exDateFromRecordPath,
            int exDateFallbackPath,
            int changed,
            int inserted,
            int updated,
            Map<String, Integer> recordDateFormsDiscovered,
            Map<String, Integer> recordDateFormsSelected,
            Map<String, Integer> recordDateFormsRejected) {
        private static DividendDetectionStats empty(int factsParsed) {
            return new DividendDetectionStats(
                    factsParsed, 0, 0, 0, 0, 0, 0, 0,
                    Map.of(), Map.of(), Map.of());
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("factsParsed", factsParsed);
            out.put("normalizedEvents", normalizedEvents);
            out.put("recordDateCandidates", recordDateCandidates);
            out.put("exDateFromRecordPath", exDateFromRecordPath);
            out.put("exDateFallbackPath", exDateFallbackPath);
            out.put("changed", changed);
            out.put("inserted", inserted);
            out.put("updated", updated);
            out.put("recordDateFormsDiscovered", recordDateFormsDiscovered);
            out.put("recordDateFormsSelected", recordDateFormsSelected);
            out.put("recordDateFormsRejected", recordDateFormsRejected);
            return out;
        }
    }

    public static record AssignmentResult(
            List<DividendEvent> events,
            int recordBasedAssignments,
            int fallbackAssignments) {
    }

    public static record UpsertStats(int changed, int inserted, int updated, int pruned) {
    }

    public static record DividendFact(
            LocalDate startDate,
            LocalDate endDate,
            double value,
            String form,
            LocalDate filedDate,
            String concept) {}

    /**
     * @param fiscalPeriodEnd quarter-end (or period end) from XBRL facts
     * @param exDividendDate    ex-dividend date; null until {@code assignExDividendDates} runs
     */
    public static record DividendEvent(
            LocalDate fiscalPeriodEnd,
            LocalDate exDividendDate,
            double rawAmount,
            double adjustedAmount,
            int year,
            boolean specialEvent) {

        /** Trading date used for persistence and Yahoo comparison (ex-date when assigned). */
        public LocalDate effectiveDate() {
            return exDividendDate != null ? exDividendDate : fiscalPeriodEnd;
        }
    }
}

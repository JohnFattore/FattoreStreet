package com.fattorestreet.sec_api.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EquityCorporateActionService {

    private static final Logger log = LoggerFactory.getLogger(EquityCorporateActionService.class);
    private static final Set<Double> COMMON_SPLIT_RATIOS = Set.of(
            2.0, 3.0, 4.0, 5.0, 7.0, 10.0, 20.0, 50.0,
            0.5, 1.0 / 3, 0.25, 0.2, 1.0 / 7, 0.1, 0.05
    );
    private static final double RATIO_TOLERANCE = 0.02;
    private static final Set<String> PREFERRED_DIVIDEND_CONCEPTS = Set.of(
            "CommonStockDividendsPerShareDeclared",
            "CommonStockDividendsPerShareCashPaid",
            "CommonStockDividendsPerShareDeclaredAndPaid",
            "DividendsPaidPerShare"
    );

    private final WebService webService;
    private final DividendRecordDateService dividendRecordDateService;
    private final CorporateActionRepository corporateActionRepository;
    private final ObjectMapper mapper;

    public EquityCorporateActionService(WebService webService,
                                        DividendRecordDateService dividendRecordDateService,
                                        CorporateActionRepository corporateActionRepository,
                                        ObjectMapper mapper) {
        this.webService = webService;
        this.dividendRecordDateService = dividendRecordDateService;
        this.corporateActionRepository = corporateActionRepository;
        this.mapper = mapper;
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
        JsonNode root;
        try {
            String json = webService.fetchFinancials(cik);
            root = mapper.readTree(json);
        } catch (Exception e) {
            log.warn("[{}] Failed to fetch SEC facts for CIK {}: {}", ticker, cik, e.getMessage());
            return EquityDetectionReport.failed(ticker, cik, "sec_fetch_failed");
        }

        SplitDetectionStats splitStats = detectSplits(ticker, cik, root);
        DividendDetectionStats dividendStats = detectDividends(ticker, cik, root);
        return new EquityDetectionReport(ticker, cik, splitStats, dividendStats, null);
    }

    private static final int MAX_SPLIT_CANDIDATE_LEAD_DAYS = 260;
    private static final int MAX_SPLIT_CANDIDATE_LAG_DAYS = 60;

    private SplitDetectionStats detectSplits(String ticker, Long cik, JsonNode root) {
        JsonNode sharesNode = navigatePath(root,
                "facts", "dei", "EntityCommonStockSharesOutstanding", "units", "shares");
        if (sharesNode == null || !sharesNode.isArray()) return SplitDetectionStats.empty();

        List<SharesEntry> entries = new ArrayList<>();
        for (JsonNode entry : sharesNode) {
            String form = entry.has("form") ? entry.get("form").asText() : "";
            if (!form.equals("10-K") && !form.equals("10-Q")) continue;
            if (!entry.has("val") || !entry.has("end")) continue;

            long val = entry.get("val").asLong();
            LocalDate endDate = LocalDate.parse(entry.get("end").asText());
            if (val > 0) {
                entries.add(new SharesEntry(endDate, val));
            }
        }

        entries.sort(Comparator.comparing(e -> e.date));
        removeDuplicateDates(entries);
        List<DividendRecordDateService.SplitDateCandidate> splitCandidates = dividendRecordDateService.fetchSplitEffectiveDates(cik);
        Set<String> usedSplitCandidateKeys = new HashSet<>();
        int secDateMatches = 0;
        int fallbackDetectedDate = 0;
        log.info("[{}] Split detection inputs: {} shares rows, {} SEC split-date candidates",
                ticker, entries.size(), splitCandidates.size());

        int created = 0;
        for (int i = 1; i < entries.size(); i++) {
            SharesEntry prev = entries.get(i - 1);
            SharesEntry curr = entries.get(i);
            double rawRatio = (double) curr.shares / prev.shares;

            if (isCommonSplitRatio(rawRatio)) {
                double snappedRaw = nearestCommonSplitRatio(rawRatio);
                double splitRatio = 1.0 / snappedRaw;
                SplitDateResolution splitDateResolution = resolveSplitEffectiveDate(prev.date, curr.date, splitCandidates, usedSplitCandidateKeys);
                LocalDate effectiveDate = splitDateResolution.effectiveDate();
                if (splitDateResolution.matchedCandidate()) {
                    secDateMatches++;
                } else {
                    fallbackDetectedDate++;
                }

                if (!corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                        ticker, ActionType.SPLIT, effectiveDate)) {
                    CorporateAction action = new CorporateAction();
                    action.setTicker(ticker);
                    action.setActionType(ActionType.SPLIT);
                    action.setEffectiveDate(effectiveDate);
                    action.setRatio(splitRatio);
                    action.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
                    corporateActionRepository.save(action);
                    created++;
                    log.info("[{}] Detected split on {}: ratio {} (shares {} -> {})",
                            ticker, effectiveDate, String.format("%.4f", splitRatio),
                            prev.shares, curr.shares);
                }
            }
        }
        return new SplitDetectionStats(entries.size(), splitCandidates.size(), created, secDateMatches, fallbackDetectedDate);
    }

    private static final int MAX_QUARTERLY_PERIOD_DAYS = 120;
    private static final int MIN_RECORD_DATE_OFFSET_DAYS = 3;
    private static final int MAX_RECORD_DATE_OFFSET_DAYS = 120;
    private static final int ANNUAL_PERIOD_MIN_DAYS = 250;
    private static final double DIVIDEND_EPSILON = 0.000001d;
    private static final long QUARTER_CADENCE_DAYS = 91;
    private static final int FALLBACK_PENALTY = 140;
    private static final double MAX_Q4_RELATIVE_JUMP = 2.5;

    private DividendDetectionStats detectDividends(String ticker, Long cik, JsonNode root) {
        List<DividendFact> facts = parseDividendFacts(root, ticker);
        List<DividendEvent> normalized = normalizeDividendFacts(facts);
        if (normalized.isEmpty()) {
            return DividendDetectionStats.empty(facts.size());
        }

        List<DividendRecordDateService.RecordDateCandidate> recordDates = dividendRecordDateService.fetchDividendRecordDates(cik);
        log.info("[{}] Dividend detection inputs: {} SEC facts, {} normalized events, {} record-date candidates",
                ticker, facts.size(), normalized.size(), recordDates.size());
        AssignmentResult assignmentResult = assignExDividendDates(normalized, recordDates);
        List<DividendEvent> detectedEvents = assignmentResult.events();
        List<CorporateAction> splitActions = corporateActionRepository.findByTicker(ticker).stream()
                .filter(a -> a.getActionType() == ActionType.SPLIT)
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .collect(Collectors.toList());
        List<DividendEvent> splitAdjustedEvents = adjustDividendsForFutureSplits(detectedEvents, splitActions);

        UpsertStats upsertStats = upsertDividendEvents(ticker, splitAdjustedEvents);
        if (upsertStats.changed() > 0) {
            log.info("[{}] Detected/Reconciled {} dividend entries", ticker, upsertStats.changed());
        }
        return new DividendDetectionStats(
                facts.size(),
                normalized.size(),
                recordDates.size(),
                assignmentResult.recordBasedAssignments(),
                assignmentResult.fallbackAssignments(),
                upsertStats.changed(),
                upsertStats.inserted(),
                upsertStats.updated());
    }

    private List<DividendFact> parseDividendFacts(JsonNode root, String ticker) {
        JsonNode usGaapNode = navigatePath(root, "facts", "us-gaap");
        if (usGaapNode == null || !usGaapNode.isObject()) {
            return Collections.emptyList();
        }

        List<DividendFact> allFacts = new ArrayList<>();
        Set<String> consumedConcepts = new TreeSet<>();
        Iterator<Map.Entry<String, JsonNode>> fields = usGaapNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String conceptName = entry.getKey();
            if (!isDividendPerShareConcept(conceptName)) {
                continue;
            }
            JsonNode conceptNode = entry.getValue();
            JsonNode units = conceptNode.path("units");
            if (!units.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> unitFields = units.fields();
            while (unitFields.hasNext()) {
                Map.Entry<String, JsonNode> unitEntry = unitFields.next();
                String unitName = unitEntry.getKey();
                if (!isPerShareUsdUnit(unitName)) {
                    continue;
                }
                JsonNode values = unitEntry.getValue();
                if (!values.isArray()) {
                    continue;
                }
                consumedConcepts.add(conceptName + ":" + unitName);
                for (JsonNode factRow : values) {
                    DividendFact parsed = parseDividendFactRow(factRow, conceptName);
                    if (parsed != null) {
                        allFacts.add(parsed);
                    }
                }
            }
        }

        if (allFacts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, DividendFact> deduped = new LinkedHashMap<>();
        for (DividendFact fact : allFacts) {
            String key = String.join("|",
                    Objects.toString(fact.concept(), ""),
                    Objects.toString(fact.form(), ""),
                    Objects.toString(fact.startDate(), ""),
                    Objects.toString(fact.endDate(), ""),
                    String.format(Locale.US, "%.6f", fact.value()),
                    Objects.toString(fact.filedDate(), ""));
            deduped.put(key, fact);
        }
        log.info("[{}] Dividend fact intake consumed {} concept/unit streams and {} unique rows",
                ticker, consumedConcepts.size(), deduped.size());
        if (!consumedConcepts.isEmpty()) {
            log.debug("[{}] Dividend concept streams: {}", ticker, String.join(", ", consumedConcepts));
        }
        return new ArrayList<>(deduped.values());
    }

    private DividendFact parseDividendFactRow(JsonNode entry, String conceptName) {
        String form = entry.has("form") ? entry.get("form").asText() : "";
        if (!form.equals("10-K") && !form.equals("10-Q") && !form.equals("8-K")) return null;
        if (!entry.has("val") || !entry.has("end")) return null;
        LocalDate endDate;
        try {
            endDate = LocalDate.parse(entry.get("end").asText());
        } catch (Exception ignored) {
            return null;
        }
        LocalDate startDate = null;
        if (entry.has("start")) {
            try {
                startDate = LocalDate.parse(entry.get("start").asText());
            } catch (Exception ignored) {
                startDate = null;
            }
        }
        double amount = entry.get("val").asDouble();
        if (amount <= 0) return null;
        LocalDate filedDate = null;
        if (entry.has("filed")) {
            try {
                filedDate = LocalDate.parse(entry.get("filed").asText());
            } catch (Exception ignored) {
                filedDate = null;
            }
        }
        return new DividendFact(startDate, endDate, amount, form, filedDate, conceptName);
    }

    private boolean isDividendPerShareConcept(String conceptName) {
        if (conceptName == null || conceptName.isBlank()) {
            return false;
        }
        if (PREFERRED_DIVIDEND_CONCEPTS.contains(conceptName)) {
            return true;
        }
        String lower = conceptName.toLowerCase(Locale.US);
        return lower.contains("dividend") && (lower.contains("pershare") || lower.contains("per share"));
    }

    private boolean isPerShareUsdUnit(String unitName) {
        if (unitName == null || unitName.isBlank()) {
            return false;
        }
        String normalized = unitName.toLowerCase(Locale.US).replace(" ", "");
        return normalized.contains("usd/share") || normalized.contains("usd/shares");
    }

    private List<DividendEvent> normalizeDividendFacts(List<DividendFact> facts) {
        if (facts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<LocalDate, List<DividendFact>> byEndDate = facts.stream()
                .collect(Collectors.groupingBy(DividendFact::endDate));

        Map<LocalDate, Double> quarterlyByEndDate = new HashMap<>();
        List<DividendEvent> specialCandidates = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DividendFact>> entry : byEndDate.entrySet()) {
            LocalDate endDate = entry.getKey();
            List<DividendFact> rows = entry.getValue();

            List<DividendFact> quarterRows = rows.stream()
                    .filter(this::isQuarterLengthFact)
                    .collect(Collectors.toList());

            DividendFact selected = null;
            if (!quarterRows.isEmpty()) {
                // Prefer shortest quarter-like window, then latest filing.
                selected = quarterRows.stream().sorted(Comparator
                        .comparingInt(this::periodLengthDays)
                        .thenComparingInt((DividendFact d) -> formPriority(d.form))
                        .thenComparing((DividendFact d) -> d.filedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                        .findFirst()
                        .orElse(quarterRows.get(0));
            } else {
                // Fallback when only non-quarter rows exist for a period end; avoid carrying annual cumulative rows directly.
                List<DividendFact> nonAnnualRows = rows.stream()
                        .filter(f -> !isAnnualLengthFact(f))
                        .collect(Collectors.toList());
                if (nonAnnualRows.isEmpty()) {
                    selected = null;
                } else {
                    selected = nonAnnualRows.stream()
                            .sorted(Comparator
                                    .comparingInt((DividendFact d) -> d.startDate() == null ? 1 : 0)
                                    .thenComparingInt((DividendFact d) -> preferredConceptPriority(d.concept()))
                                    .thenComparingInt((DividendFact d) -> formPriority(d.form))
                                    .thenComparing((DividendFact d) -> d.filedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                            .findFirst()
                            .orElse(nonAnnualRows.get(0));
                }
            }

            if (selected != null) {
                double regularAmount = round4(selected.value);
                quarterlyByEndDate.put(endDate, regularAmount);
                for (DividendFact row : rows) {
                    if (isAnnualLengthFact(row)) {
                        continue;
                    }
                    double amount = round4(row.value());
                    if (Math.abs(amount - regularAmount) <= DIVIDEND_EPSILON) {
                        continue;
                    }
                    boolean muchLargerThanRegular = amount >= round4(regularAmount * 2.8) && (amount - regularAmount) >= 0.75;
                    boolean likelySpecialDisclosure = "8-K".equalsIgnoreCase(row.form()) && amount > regularAmount + 0.25;
                    if (muchLargerThanRegular || likelySpecialDisclosure) {
                        specialCandidates.add(new DividendEvent(endDate, amount, amount, endDate.getYear(), true));
                    }
                }
            }
        }

        // Derive missing or obviously cumulative fiscal-Q4 using annual cumulative 10-K facts.
        List<DividendFact> annualFacts = facts.stream()
                .filter(this::isAnnualLengthFact)
                .sorted(Comparator
                        .comparing(DividendFact::endDate)
                        .thenComparing((DividendFact d) -> d.filedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        Map<LocalDate, DividendFact> latestAnnualByEnd = new LinkedHashMap<>();
        for (DividendFact annual : annualFacts) {
            DividendFact existing = latestAnnualByEnd.get(annual.endDate());
            if (existing == null || isLater(annual.filedDate(), existing.filedDate())) {
                latestAnnualByEnd.put(annual.endDate(), annual);
            }
        }

        for (DividendFact annual : latestAnnualByEnd.values()) {
            if (annual.startDate == null) continue;
            LocalDate fyStart = annual.startDate;
            LocalDate fyEnd = annual.endDate;

            List<LocalDate> priorQuarterEnds = quarterlyByEndDate.keySet().stream()
                    .filter(d -> d.isAfter(fyStart.minusDays(1)) && d.isBefore(fyEnd))
                    .sorted()
                    .collect(Collectors.toList());

            double sumPrior = priorQuarterEnds.stream().mapToDouble(d -> quarterlyByEndDate.getOrDefault(d, 0.0)).sum();
            double derivedQ4 = round4(annual.value - sumPrior);
            if (derivedQ4 <= DIVIDEND_EPSILON) {
                continue;
            }
            if (!isPlausibleDerivedQuarter(derivedQ4, priorQuarterEnds, quarterlyByEndDate)) {
                continue;
            }

            Double existingAtFyEnd = quarterlyByEndDate.get(fyEnd);
            if (existingAtFyEnd == null) {
                quarterlyByEndDate.put(fyEnd, derivedQ4);
                continue;
            }

            // Replace FY-end cumulative row with derived quarter if it better fits annual reconciliation.
            boolean looksCumulative = existingAtFyEnd > derivedQ4 + 0.02;
            if (looksCumulative) {
                quarterlyByEndDate.put(fyEnd, derivedQ4);
            }
        }

        List<DividendEvent> regularEvents = quarterlyByEndDate.entrySet().stream()
                .filter(e -> e.getValue() > DIVIDEND_EPSILON)
                .map(e -> new DividendEvent(e.getKey(), round4(e.getValue()), round4(e.getValue()), e.getKey().getYear(), false))
                .sorted(Comparator.comparing(DividendEvent::periodEnd))
                .collect(Collectors.toList());
        if (specialCandidates.isEmpty()) {
            return regularEvents;
        }
        Set<String> regularKeys = regularEvents.stream()
                .map(e -> e.periodEnd() + "|" + String.format(Locale.US, "%.4f", e.rawAmount()))
                .collect(Collectors.toSet());
        Map<String, DividendEvent> dedupedSpecials = new LinkedHashMap<>();
        for (DividendEvent special : specialCandidates) {
            String key = special.periodEnd() + "|" + String.format(Locale.US, "%.4f", special.rawAmount());
            if (regularKeys.contains(key)) {
                continue;
            }
            dedupedSpecials.putIfAbsent(key, special);
        }
        List<DividendEvent> combined = new ArrayList<>(regularEvents);
        combined.addAll(dedupedSpecials.values());
        combined.sort(Comparator
                .comparing(DividendEvent::periodEnd)
                .thenComparing(DividendEvent::rawAmount));
        return combined;
    }

    private AssignmentResult assignExDividendDates(List<DividendEvent> normalized, List<DividendRecordDateService.RecordDateCandidate> recordDateCandidates) {
        List<DividendRecordDateService.RecordDateCandidate> sortedCandidates = new ArrayList<>(recordDateCandidates);
        sortedCandidates.sort(Comparator
                .comparing(DividendRecordDateService.RecordDateCandidate::recordDate)
                .thenComparing(DividendRecordDateService.RecordDateCandidate::confidenceScore, Comparator.reverseOrder())
                .thenComparing(DividendRecordDateService.RecordDateCandidate::filingDate));

        List<DividendEvent> regularEvents = normalized.stream()
                .filter(e -> !e.specialEvent())
                .sorted(Comparator.comparing(DividendEvent::periodEnd))
                .toList();
        List<DividendEvent> specialEvents = normalized.stream()
                .filter(DividendEvent::specialEvent)
                .sorted(Comparator.comparing(DividendEvent::periodEnd))
                .toList();

        List<DividendEvent> mapped = new ArrayList<>(normalized.size());
        int recordBasedAssignments = 0;
        int fallbackAssignments = 0;
        List<Integer> assignment = optimizeRecordDateAssignment(regularEvents, sortedCandidates);
        Set<Integer> usedCandidateIndexes = new HashSet<>();
        LocalDate lastMatchedRecordDate = null;
        for (int i = 0; i < regularEvents.size(); i++) {
            DividendEvent event = regularEvents.get(i);
            int chosen = assignment.get(i);
            if (chosen >= 0) {
                DividendRecordDateService.RecordDateCandidate candidate = sortedCandidates.get(chosen);
                lastMatchedRecordDate = candidate.recordDate();
                usedCandidateIndexes.add(chosen);
                LocalDate effectiveDate = safeComputeExDividendDate(candidate.recordDate(), event.periodEnd);
                mapped.add(new DividendEvent(effectiveDate, event.rawAmount(), event.adjustedAmount(), event.year(), false));
                recordBasedAssignments++;
                continue;
            }

            LocalDate inferred = inferFallbackExDate(event.periodEnd, lastMatchedRecordDate);
            log.debug("Using inferred ex-date {} for period end {} due to low-confidence record-date match",
                    inferred, event.periodEnd);
            mapped.add(new DividendEvent(inferred, event.rawAmount(), event.adjustedAmount(), event.year(), false));
            fallbackAssignments++;
        }

        for (DividendEvent special : specialEvents) {
            SpecialMappingResult specialMapping = mapSpecialDividendExDate(special, sortedCandidates, usedCandidateIndexes);
            mapped.add(new DividendEvent(
                    specialMapping.effectiveDate(),
                    special.rawAmount(),
                    special.adjustedAmount(),
                    special.year(),
                    true));
            if (specialMapping.recordBased()) {
                recordBasedAssignments++;
            } else {
                fallbackAssignments++;
            }
        }
        mapped.sort(Comparator.comparing(DividendEvent::periodEnd).thenComparing(DividendEvent::rawAmount));
        return new AssignmentResult(mapped, recordBasedAssignments, fallbackAssignments);
    }

    private SpecialMappingResult mapSpecialDividendExDate(
            DividendEvent event,
            List<DividendRecordDateService.RecordDateCandidate> sortedCandidates,
            Set<Integer> usedCandidateIndexes) {
        int bestIndex = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < sortedCandidates.size(); i++) {
            if (usedCandidateIndexes.contains(i)) {
                continue;
            }
            DividendRecordDateService.RecordDateCandidate candidate = sortedCandidates.get(i);
            long dayOffset = ChronoUnit.DAYS.between(event.periodEnd(), candidate.recordDate());
            if (dayOffset < MIN_RECORD_DATE_OFFSET_DAYS || dayOffset > MAX_RECORD_DATE_OFFSET_DAYS) {
                continue;
            }
            double score = Math.abs(dayOffset - 42) - (candidate.confidenceScore() / 15.0);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0) {
            usedCandidateIndexes.add(bestIndex);
            return new SpecialMappingResult(
                    safeComputeExDividendDate(sortedCandidates.get(bestIndex).recordDate(), event.periodEnd()),
                    true);
        }
        LocalDate fallback = inferFallbackExDate(event.periodEnd(), null);
        log.info("Using low-confidence fallback ex-date {} for special dividend period end {}",
                fallback, event.periodEnd());
        return new SpecialMappingResult(fallback, false);
    }

    private List<Integer> optimizeRecordDateAssignment(
            List<DividendEvent> events,
            List<DividendRecordDateService.RecordDateCandidate> candidates) {
        int n = events.size();
        int m = candidates.size();
        if (n == 0) {
            return Collections.emptyList();
        }

        double[][] dp = new double[n + 1][m + 1];
        int[][] choice = new int[n][m + 1];
        for (int i = 0; i <= n; i++) {
            Arrays.fill(dp[i], Double.POSITIVE_INFINITY);
            if (i < n) {
                Arrays.fill(choice[i], -2);
            }
        }
        for (int prevShift = 0; prevShift <= m; prevShift++) {
            dp[n][prevShift] = 0.0;
        }

        for (int i = n - 1; i >= 0; i--) {
            for (int prev = -1; prev < m; prev++) {
                int prevShift = prev + 1;
                double bestScore = FALLBACK_PENALTY + dp[i + 1][prevShift];
                int bestChoice = -1;

                for (int j = prev + 1; j < m; j++) {
                    DividendRecordDateService.RecordDateCandidate candidate = candidates.get(j);
                    if (!isCandidateEligible(events.get(i), candidate)) {
                        continue;
                    }
                    double score = candidateMatchScore(events.get(i), candidate, prev >= 0 ? candidates.get(prev) : null)
                            + dp[i + 1][j + 1];
                    if (score < bestScore) {
                        bestScore = score;
                        bestChoice = j;
                    }
                }

                dp[i][prevShift] = bestScore;
                choice[i][prevShift] = bestChoice;
            }
        }

        List<Integer> assignment = new ArrayList<>(n);
        int prev = -1;
        for (int i = 0; i < n; i++) {
            int picked = choice[i][prev + 1];
            assignment.add(picked);
            if (picked >= 0) {
                prev = picked;
            }
        }
        return assignment;
    }

    private boolean isCandidateEligible(DividendEvent event, DividendRecordDateService.RecordDateCandidate candidate) {
        long dayOffset = ChronoUnit.DAYS.between(event.periodEnd, candidate.recordDate());
        if (dayOffset < MIN_RECORD_DATE_OFFSET_DAYS || dayOffset > MAX_RECORD_DATE_OFFSET_DAYS) {
            return false;
        }
        return candidate.filingDate() == null || !candidate.filingDate().isBefore(event.periodEnd.minusDays(5));
    }

    private double candidateMatchScore(
            DividendEvent event,
            DividendRecordDateService.RecordDateCandidate candidate,
            DividendRecordDateService.RecordDateCandidate prevCandidate) {
        long dayOffset = ChronoUnit.DAYS.between(event.periodEnd, candidate.recordDate());
        double score = Math.abs(dayOffset - 42);
        if (prevCandidate != null) {
            long cadenceGap = ChronoUnit.DAYS.between(prevCandidate.recordDate(), candidate.recordDate());
            score += Math.abs(cadenceGap - QUARTER_CADENCE_DAYS) / 2.0;
        }
        score -= candidate.confidenceScore() / 12.0;
        return score;
    }

    private LocalDate inferFallbackExDate(LocalDate periodEnd, LocalDate lastMatchedRecordDate) {
        LocalDate inferredRecordDate;
        if (lastMatchedRecordDate != null) {
            inferredRecordDate = lastMatchedRecordDate.plusDays(QUARTER_CADENCE_DAYS);
            if (Math.abs(ChronoUnit.DAYS.between(periodEnd, inferredRecordDate)) > MAX_RECORD_DATE_OFFSET_DAYS) {
                inferredRecordDate = periodEnd.plusDays(42);
            }
        } else {
            inferredRecordDate = periodEnd.plusDays(42);
        }
        return safeComputeExDividendDate(inferredRecordDate, periodEnd);
    }

    private LocalDate safeComputeExDividendDate(LocalDate recordDate, LocalDate fallbackDate) {
        LocalDate computed = dividendRecordDateService.computeExDividendDate(recordDate);
        if (computed != null) {
            return computed;
        }
        return recordDate != null ? recordDate : fallbackDate;
    }

    private UpsertStats upsertDividendEvents(String ticker, List<DividendEvent> detectedEvents) {
        List<CorporateAction> existing = corporateActionRepository.findByTicker(ticker).stream()
                .filter(a -> a.getActionType() == ActionType.DIVIDEND)
                .sorted(Comparator
                        .comparing(CorporateAction::getEffectiveDate)
                        .thenComparing(CorporateAction::getRatio))
                .collect(Collectors.toList());
        List<DividendEvent> sortedDetected = dedupeDividendEvents(ticker, detectedEvents);
        int changed = 0;
        int inserted = 0;
        int updated = 0;
        Set<CorporateAction> usedActions = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DividendEvent target : sortedDetected) {
            // Preserve legacy hook usage while allowing same-date multi-dividend inserts when amounts differ.
            corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                    ticker, ActionType.DIVIDEND, target.periodEnd());
            CorporateAction matched = findExactDividendMatch(existing, target, usedActions);
            if (matched == null) {
                matched = findYearScopedDividendMatch(existing, target, usedActions);
            }
            if (matched != null) {
                usedActions.add(matched);
                boolean dateChanged = !matched.getEffectiveDate().equals(target.periodEnd());
                boolean ratioChanged = Math.abs(matched.getRatio() - target.adjustedAmount()) > DIVIDEND_EPSILON;
                boolean rawChanged = !roughlyEqual(matched.getRawDividend(), target.rawAmount());
                boolean adjustedChanged = !roughlyEqual(matched.getAdjustedDividend(), target.adjustedAmount());
                if (dateChanged || ratioChanged || rawChanged || adjustedChanged) {
                    matched.setEffectiveDate(target.periodEnd());
                    matched.setRatio(target.adjustedAmount());
                    matched.setRawDividend(target.rawAmount());
                    matched.setAdjustedDividend(target.adjustedAmount());
                    corporateActionRepository.save(matched);
                    changed++;
                    updated++;
                }
                continue;
            }
            if (hasExactSignature(existing, target)) {
                log.debug("[{}] Skipping exact duplicate dividend event on {} raw={} adjusted={}",
                        ticker,
                        target.periodEnd(),
                        String.format(Locale.US, "%.4f", target.rawAmount()),
                        String.format(Locale.US, "%.4f", target.adjustedAmount()));
                continue;
            }
            List<CorporateAction> sameDateExisting = corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(
                    ticker, ActionType.DIVIDEND, target.periodEnd());
            if (hasExactSignature(sameDateExisting, target)) {
                log.debug("[{}] Skipping same-date duplicate after repository recheck on {} raw={} adjusted={}",
                        ticker,
                        target.periodEnd(),
                        String.format(Locale.US, "%.4f", target.rawAmount()),
                        String.format(Locale.US, "%.4f", target.adjustedAmount()));
                continue;
            }
            CorporateAction action = new CorporateAction();
            action.setTicker(ticker);
            action.setActionType(ActionType.DIVIDEND);
            action.setEffectiveDate(target.periodEnd());
            action.setRatio(target.adjustedAmount());
            action.setRawDividend(target.rawAmount());
            action.setAdjustedDividend(target.adjustedAmount());
            action.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
            CorporateAction saved;
            try {
                saved = corporateActionRepository.save(action);
            } catch (DataIntegrityViolationException e) {
                List<CorporateAction> refreshedSameDate = corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(
                        ticker, ActionType.DIVIDEND, target.periodEnd());
                if (hasExactSignature(refreshedSameDate, target)) {
                    log.info("[{}] Dividend insert race resolved on {} raw={} adjusted={}: row already present",
                            ticker,
                            target.periodEnd(),
                            String.format(Locale.US, "%.4f", target.rawAmount()),
                            String.format(Locale.US, "%.4f", target.adjustedAmount()));
                } else {
                    log.warn("[{}] Skipping conflicting dividend insert on {} raw={} adjusted={} due to unique constraint: {}",
                            ticker,
                            target.periodEnd(),
                            String.format(Locale.US, "%.4f", target.rawAmount()),
                            String.format(Locale.US, "%.4f", target.adjustedAmount()),
                            e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
                }
                continue;
            }
            existing.add(saved);
            usedActions.add(saved);
            changed++;
            inserted++;
        }
        return new UpsertStats(changed, inserted, updated);
    }

    private List<DividendEvent> dedupeDividendEvents(String ticker, List<DividendEvent> events) {
        Map<String, DividendEvent> deduped = new LinkedHashMap<>();
        Map<LocalDate, Integer> keptByDate = new HashMap<>();
        for (DividendEvent event : events) {
            String key = event.periodEnd() + "|"
                    + String.format(Locale.US, "%.4f", event.rawAmount()) + "|"
                    + String.format(Locale.US, "%.4f", event.adjustedAmount());
            if (deduped.containsKey(key)) {
                log.debug("[{}] Dropping exact duplicate normalized dividend event on {} raw={} adjusted={}",
                        ticker,
                        event.periodEnd(),
                        String.format(Locale.US, "%.4f", event.rawAmount()),
                        String.format(Locale.US, "%.4f", event.adjustedAmount()));
                continue;
            }
            int sameDateCount = keptByDate.getOrDefault(event.periodEnd(), 0);
            if (sameDateCount > 0) {
                log.info("[{}] Keeping additional same-date dividend event on {} raw={} adjusted={} (existing same-date events={})",
                        ticker,
                        event.periodEnd(),
                        String.format(Locale.US, "%.4f", event.rawAmount()),
                        String.format(Locale.US, "%.4f", event.adjustedAmount()),
                        sameDateCount);
            }
            deduped.put(key, event);
            keptByDate.put(event.periodEnd(), sameDateCount + 1);
        }
        return deduped.values().stream()
                .sorted(Comparator.comparing(DividendEvent::periodEnd).thenComparing(DividendEvent::rawAmount))
                .toList();
    }

    private CorporateAction findExactDividendMatch(List<CorporateAction> existing, DividendEvent target, Set<CorporateAction> usedActions) {
        for (CorporateAction action : existing) {
            if (usedActions.contains(action)) {
                continue;
            }
            if (!action.getEffectiveDate().equals(target.periodEnd())) {
                continue;
            }
            if (Math.abs(action.getRatio() - target.adjustedAmount()) <= DIVIDEND_EPSILON
                    || (roughlyEqual(action.getRawDividend(), target.rawAmount())
                    && roughlyEqual(action.getAdjustedDividend(), target.adjustedAmount()))) {
                return action;
            }
        }
        return null;
    }

    private CorporateAction findYearScopedDividendMatch(List<CorporateAction> existing, DividendEvent target, Set<CorporateAction> usedActions) {
        CorporateAction best = null;
        double bestScore = Double.MAX_VALUE;
        for (CorporateAction action : existing) {
            if (usedActions.contains(action)) {
                continue;
            }
            if (action.getEffectiveDate().getYear() != target.year()) {
                continue;
            }
            long dayDistance = Math.abs(ChronoUnit.DAYS.between(action.getEffectiveDate(), target.periodEnd()));
            if (dayDistance > 55) {
                continue;
            }
            double amountDistance = Math.abs(action.getRatio() - target.adjustedAmount()) * 100.0;
            double score = dayDistance + amountDistance;
            if (score < bestScore) {
                best = action;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean hasExactSignature(List<CorporateAction> existing, DividendEvent target) {
        for (CorporateAction action : existing) {
            if (action.getActionType() != ActionType.DIVIDEND) {
                continue;
            }
            if (!action.getEffectiveDate().equals(target.periodEnd())) {
                continue;
            }
            if (Math.abs(action.getRatio() - target.adjustedAmount()) <= DIVIDEND_EPSILON
                    && roughlyEqual(action.getRawDividend(), target.rawAmount())
                    && roughlyEqual(action.getAdjustedDividend(), target.adjustedAmount())) {
                return true;
            }
        }
        return false;
    }

    private List<DividendEvent> adjustDividendsForFutureSplits(List<DividendEvent> events, List<CorporateAction> splits) {
        if (splits.isEmpty()) return events;

        List<CorporateAction> snappedSplits = splits.stream()
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .map(this::snapSplitAction)
                .toList();

        List<DividendEvent> adjusted = new ArrayList<>(events.size());
        for (DividendEvent event : events) {
            double factor = 1.0;
            for (CorporateAction split : snappedSplits) {
                if (split.getEffectiveDate().isAfter(event.periodEnd)) {
                    factor *= split.getRatio();
                }
            }
            adjusted.add(new DividendEvent(
                    event.periodEnd(),
                    event.rawAmount(),
                    round4(event.rawAmount() * factor),
                    event.year(),
                    event.specialEvent()));
        }
        return adjusted;
    }

    private boolean roughlyEqual(Double left, double right) {
        if (left == null) return false;
        return Math.abs(left - right) <= DIVIDEND_EPSILON;
    }

    private CorporateAction snapSplitAction(CorporateAction split) {
        if (split.getActionType() != ActionType.SPLIT || split.getRatio() == null || split.getRatio() <= 0) {
            return split;
        }
        double raw = 1.0 / split.getRatio();
        if (!isCommonSplitRatio(raw)) {
            return split;
        }
        double snappedRaw = nearestCommonSplitRatio(raw);
        CorporateAction out = new CorporateAction();
        out.setTicker(split.getTicker());
        out.setActionType(split.getActionType());
        out.setEffectiveDate(split.getEffectiveDate());
        out.setSourceType(split.getSourceType());
        out.setRatio(round4(1.0 / snappedRaw));
        return out;
    }

    private boolean isLater(LocalDate candidate, LocalDate baseline) {
        if (candidate == null) return false;
        if (baseline == null) return true;
        return candidate.isAfter(baseline);
    }

    private SplitDateResolution resolveSplitEffectiveDate(
            LocalDate prevDate,
            LocalDate detectedDate,
            List<DividendRecordDateService.SplitDateCandidate> splitCandidates,
            Set<String> usedSplitCandidateKeys) {
        DividendRecordDateService.SplitDateCandidate bestCandidate = null;
        double bestScore = Double.MAX_VALUE;
        int eligibleCandidates = 0;
        List<String> rejected = new ArrayList<>();
        for (DividendRecordDateService.SplitDateCandidate candidate : splitCandidates) {
            String candidateKey = candidate.accessionNumber() + "|" + candidate.effectiveDate();
            if (usedSplitCandidateKeys.contains(candidateKey)) {
                rejected.add(candidateKey + " rejected: already-used");
                continue;
            }
            long daysToDetected = ChronoUnit.DAYS.between(candidate.effectiveDate(), detectedDate);
            if (daysToDetected < -MAX_SPLIT_CANDIDATE_LAG_DAYS || daysToDetected > MAX_SPLIT_CANDIDATE_LEAD_DAYS) {
                rejected.add(candidateKey + " rejected: out-of-window daysToDetected=" + daysToDetected);
                continue;
            }
            if (candidate.filingDate() != null && prevDate != null && candidate.filingDate().isBefore(prevDate.minusDays(10))) {
                rejected.add(candidateKey + " rejected: filing-too-early filingDate=" + candidate.filingDate());
                continue;
            }
            eligibleCandidates++;
            double score = Math.abs(daysToDetected);
            score -= candidate.confidenceScore() / 20.0;
            if (bestCandidate == null || score < bestScore) {
                bestCandidate = candidate;
                bestScore = score;
            }
        }
        if (bestCandidate != null) {
            long selectedDaysToDetected = ChronoUnit.DAYS.between(bestCandidate.effectiveDate(), detectedDate);
            log.info("Selected split-date candidate {}|{} for detected date {} (eligible={}, score={}, daysToDetected={}, confidence={})",
                    bestCandidate.accessionNumber(),
                    bestCandidate.effectiveDate(),
                    detectedDate,
                    eligibleCandidates,
                    String.format("%.4f", bestScore),
                    selectedDaysToDetected,
                    bestCandidate.confidenceScore());
            if (!rejected.isEmpty()) {
                log.debug("Rejected split candidates for detected date {}: {}", detectedDate, String.join(" | ", rejected));
            }
            usedSplitCandidateKeys.add(bestCandidate.accessionNumber() + "|" + bestCandidate.effectiveDate());
            return new SplitDateResolution(bestCandidate.effectiveDate(), true);
        }
        log.info("No credible SEC split-date candidate found for detected date {}. Using fallback detected date. Rejections: {}",
                detectedDate,
                rejected.isEmpty() ? "none-candidates" : String.join(" | ", rejected));
        return new SplitDateResolution(detectedDate, false);
    }

    private boolean isPlausibleDerivedQuarter(
            double derivedQ4,
            List<LocalDate> priorQuarterEnds,
            Map<LocalDate, Double> quarterlyByEndDate) {
        if (priorQuarterEnds.isEmpty()) {
            return true;
        }
        List<Double> priorValues = priorQuarterEnds.stream()
                .map(d -> quarterlyByEndDate.getOrDefault(d, 0.0))
                .filter(v -> v > DIVIDEND_EPSILON)
                .sorted()
                .toList();
        if (priorValues.isEmpty()) {
            return true;
        }
        double median = priorValues.get(priorValues.size() / 2);
        if (median <= DIVIDEND_EPSILON) {
            return true;
        }
        return derivedQ4 <= round4(median * MAX_Q4_RELATIVE_JUMP);
    }

    private boolean isQuarterLengthFact(DividendFact fact) {
        if (fact.startDate == null) return false;
        int days = periodLengthDays(fact);
        return days > 0 && days <= MAX_QUARTERLY_PERIOD_DAYS;
    }

    private boolean isAnnualLengthFact(DividendFact fact) {
        if (fact.startDate == null) return false;
        int days = periodLengthDays(fact);
        return days >= ANNUAL_PERIOD_MIN_DAYS;
    }

    private int formPriority(String form) {
        if ("10-Q".equalsIgnoreCase(form)) return 0;
        if ("8-K".equalsIgnoreCase(form)) return 1;
        if ("10-K".equalsIgnoreCase(form)) return 2;
        return 3;
    }

    private int preferredConceptPriority(String concept) {
        if (PREFERRED_DIVIDEND_CONCEPTS.contains(concept)) {
            return 0;
        }
        return 1;
    }

    private int periodLengthDays(DividendFact fact) {
        if (fact.startDate == null || fact.endDate == null) return Integer.MAX_VALUE;
        return (int) ChronoUnit.DAYS.between(fact.startDate, fact.endDate);
    }

    private boolean isCommonSplitRatio(double rawRatio) {
        for (double common : COMMON_SPLIT_RATIOS) {
            if (Math.abs(rawRatio - common) / common < RATIO_TOLERANCE) return true;
        }
        return false;
    }

    private double nearestCommonSplitRatio(double rawRatio) {
        double best = rawRatio;
        double bestRelErr = Double.MAX_VALUE;
        for (double common : COMMON_SPLIT_RATIOS) {
            double relErr = Math.abs(rawRatio - common) / common;
            if (relErr < bestRelErr) {
                bestRelErr = relErr;
                best = common;
            }
        }
        return bestRelErr < RATIO_TOLERANCE ? best : rawRatio;
    }

    /** Keep only the last entry per date. */
    private void removeDuplicateDates(List<SharesEntry> entries) {
        Map<LocalDate, SharesEntry> byDate = new LinkedHashMap<>();
        for (SharesEntry e : entries) {
            byDate.put(e.date, e);
        }
        entries.clear();
        entries.addAll(byDate.values());
    }

    private JsonNode navigatePath(JsonNode node, String... path) {
        for (String key : path) {
            if (node == null) return null;
            node = node.get(key);
        }
        return node;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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
            int updated) {
        private static DividendDetectionStats empty(int factsParsed) {
            return new DividendDetectionStats(factsParsed, 0, 0, 0, 0, 0, 0, 0);
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
            return out;
        }
    }

    private record AssignmentResult(
            List<DividendEvent> events,
            int recordBasedAssignments,
            int fallbackAssignments) {
    }

    private record UpsertStats(int changed, int inserted, int updated) {
    }

    private record SplitDateResolution(LocalDate effectiveDate, boolean matchedCandidate) {
    }

    private record SpecialMappingResult(LocalDate effectiveDate, boolean recordBased) {
    }

    private record SharesEntry(LocalDate date, long shares) {}
    private record DividendFact(
            LocalDate startDate,
            LocalDate endDate,
            double value,
            String form,
            LocalDate filedDate,
            String concept) {}
    private record DividendEvent(
            LocalDate periodEnd,
            double rawAmount,
            double adjustedAmount,
            int year,
            boolean specialEvent) {}
}

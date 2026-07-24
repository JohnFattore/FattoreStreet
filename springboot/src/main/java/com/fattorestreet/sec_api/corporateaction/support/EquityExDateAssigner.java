package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;

@Component
public class EquityExDateAssigner {

    private static final Logger log = LoggerFactory.getLogger(EquityExDateAssigner.class);
    private static final int MIN_RECORD_DATE_OFFSET_DAYS = 10;
    private static final int MAX_RECORD_DATE_OFFSET_DAYS = 80;
    private static final int MIN_DAYS_FISCAL_TO_EX = 5;
    private static final int MAX_DAYS_FISCAL_TO_EX = 130;
    private static final long QUARTER_CADENCE_DAYS = 91;
    private static final int FALLBACK_PENALTY = 140;
    /** Declaration record dates may trail the fiscal period end by up to a full reporting cycle. */
    private static final int MAX_TUPLE_RECORD_OFFSET_DAYS = 95;
    private static final double TUPLE_AMOUNT_ABS_TOLERANCE = 0.0005;
    private static final double TUPLE_AMOUNT_REL_TOLERANCE = 0.005;
    /** Unmatched declarations promoted to provisional events must clear this tuple confidence. */
    private static final int MIN_PROMOTION_CONFIDENCE = 85;
    /** Promotion targets the 8-K-to-10-Q blind window only; older record dates are left alone. */
    private static final int MAX_PROMOTION_RECORD_AGE_DAYS = 200;
    /** Promoted amounts must sit within this band of the newest regular amount (misparse guard). */
    private static final double MAX_PROMOTION_AMOUNT_RATIO = 3.0;

    private final CorporateActionFilingDateService corporateActionFilingDateService;

    public EquityExDateAssigner(CorporateActionFilingDateService corporateActionFilingDateService) {
        this.corporateActionFilingDateService = corporateActionFilingDateService;
    }

    public EquityCorporateActionService.AssignmentResult assignExDividendDates(
            List<EquityCorporateActionService.DividendEvent> normalized,
            List<CorporateActionFilingDateService.RecordDateCandidate> recordDateCandidates,
            List<CorporateActionFilingDateService.ExDividendDateCandidate> exDividendDirectCandidates,
            List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations,
            List<CorporateAction> knownActions) {
        List<CorporateActionFilingDateService.RecordDateCandidate> sortedCandidates = new ArrayList<>(recordDateCandidates);
        sortedCandidates.sort(Comparator
                .comparing(CorporateActionFilingDateService.RecordDateCandidate::recordDate)
                .thenComparing(CorporateActionFilingDateService.RecordDateCandidate::confidenceScore, Comparator.reverseOrder())
                .thenComparing(CorporateActionFilingDateService.RecordDateCandidate::filingDate));

        List<EquityCorporateActionService.DividendEvent> regularEvents = normalized.stream()
                .filter(e -> !e.specialEvent())
                .sorted(Comparator.comparing(EquityCorporateActionService.DividendEvent::fiscalPeriodEnd))
                .toList();
        List<EquityCorporateActionService.DividendEvent> specialEvents = normalized.stream()
                .filter(EquityCorporateActionService.DividendEvent::specialEvent)
                .sorted(Comparator.comparing(EquityCorporateActionService.DividendEvent::fiscalPeriodEnd))
                .toList();

        List<CorporateAction> knownSplits = knownActions == null ? List.of() : knownActions.stream()
                .filter(a -> a.getActionType() == ActionType.SPLIT && a.getRatio() != null && a.getRatio() > 0)
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .toList();
        List<DividendDeclarationTupleExtractor.DividendDeclaration> tuples =
                declarations == null ? List.of() : declarations;

        List<EquityCorporateActionService.DividendEvent> mapped = new ArrayList<>(normalized.size());
        int tupleMatchedAssignments = 0;
        int directExAssignments = 0;
        int dpAssignments = 0;
        int syntheticAssignments = 0;
        Set<Integer> usedExDirect = new HashSet<>();
        Set<Integer> usedTuples = new HashSet<>();

        List<EquityCorporateActionService.DividendEvent> needsDirectPath = new ArrayList<>();
        for (EquityCorporateActionService.DividendEvent event : regularEvents) {
            Integer tupleIdx = findBestDeclarationIndex(event, tuples, usedTuples, knownSplits);
            if (tupleIdx != null) {
                usedTuples.add(tupleIdx);
                mapped.add(eventFromTuple(event, tuples.get(tupleIdx), false));
                tupleMatchedAssignments++;
            } else {
                needsDirectPath.add(event);
            }
        }

        List<EquityCorporateActionService.DividendEvent> needsRecordPath = new ArrayList<>();
        for (EquityCorporateActionService.DividendEvent event : needsDirectPath) {
            Integer directIdx = findBestDirectExIndex(event, exDividendDirectCandidates, usedExDirect);
            if (directIdx != null) {
                usedExDirect.add(directIdx);
                CorporateActionFilingDateService.ExDividendDateCandidate chosen = exDividendDirectCandidates.get(directIdx);
                mapped.add(new EquityCorporateActionService.DividendEvent(
                        event.fiscalPeriodEnd(),
                        chosen.exDividendDate(),
                        event.rawAmount(),
                        event.adjustedAmount(),
                        event.year(),
                        false,
                        null,
                        null,
                        CorporateAction.EX_DATE_SOURCE_DIRECT_EX_TEXT));
                directExAssignments++;
                log.debug("Direct ex-date {} for fiscal period end {} (from filing {})",
                        chosen.exDividendDate(), event.fiscalPeriodEnd(), chosen.accessionNumber());
            } else {
                needsRecordPath.add(event);
            }
        }

        List<Integer> assignment = optimizeRecordDateAssignment(needsRecordPath, sortedCandidates);
        Set<Integer> usedCandidateIndexes = new HashSet<>();
        LocalDate lastMatchedRecordDate = null;
        for (int i = 0; i < needsRecordPath.size(); i++) {
            EquityCorporateActionService.DividendEvent event = needsRecordPath.get(i);
            int chosen = assignment.get(i);
            if (chosen >= 0) {
                CorporateActionFilingDateService.RecordDateCandidate candidate = sortedCandidates.get(chosen);
                lastMatchedRecordDate = candidate.recordDate();
                usedCandidateIndexes.add(chosen);
                LocalDate effectiveDate = safeComputeExDividendDate(candidate.recordDate(), event.fiscalPeriodEnd());
                mapped.add(new EquityCorporateActionService.DividendEvent(
                        event.fiscalPeriodEnd(),
                        effectiveDate,
                        event.rawAmount(),
                        event.adjustedAmount(),
                        event.year(),
                        false,
                        candidate.recordDate(),
                        null,
                        CorporateAction.EX_DATE_SOURCE_RECORD_DP));
                dpAssignments++;
                continue;
            }
            LocalDate inferred = inferFallbackExDate(event.fiscalPeriodEnd(), lastMatchedRecordDate);
            log.debug("Using inferred ex-date {} for period end {} due to low-confidence record-date match",
                    inferred, event.fiscalPeriodEnd());
            mapped.add(new EquityCorporateActionService.DividendEvent(
                    event.fiscalPeriodEnd(),
                    inferred,
                    event.rawAmount(),
                    event.adjustedAmount(),
                    event.year(),
                    false,
                    null,
                    null,
                    CorporateAction.EX_DATE_SOURCE_SYNTHETIC));
            syntheticAssignments++;
        }

        for (EquityCorporateActionService.DividendEvent special : specialEvents) {
            Integer tupleIdx = findBestDeclarationIndex(special, tuples, usedTuples, knownSplits);
            if (tupleIdx != null) {
                usedTuples.add(tupleIdx);
                mapped.add(eventFromTuple(special, tuples.get(tupleIdx), true));
                tupleMatchedAssignments++;
                continue;
            }
            SpecialMappingResult specialMapping = mapSpecialDividendExDate(special, sortedCandidates, usedCandidateIndexes);
            mapped.add(new EquityCorporateActionService.DividendEvent(
                    special.fiscalPeriodEnd(),
                    specialMapping.effectiveDate(),
                    special.rawAmount(),
                    special.adjustedAmount(),
                    special.year(),
                    true,
                    specialMapping.recordDate(),
                    null,
                    specialMapping.recordBased()
                            ? CorporateAction.EX_DATE_SOURCE_RECORD_DP
                            : CorporateAction.EX_DATE_SOURCE_SYNTHETIC));
            if (specialMapping.recordBased()) {
                dpAssignments++;
            } else {
                syntheticAssignments++;
            }
        }
        int promotedTupleEvents = promoteUnmatchedDeclarations(mapped, regularEvents, tuples, usedTuples);
        mapped.sort(Comparator.comparing(EquityCorporateActionService.DividendEvent::effectiveDate).thenComparing(EquityCorporateActionService.DividendEvent::rawAmount));
        return new EquityCorporateActionService.AssignmentResult(
                mapped, tupleMatchedAssignments, directExAssignments, dpAssignments, syntheticAssignments,
                promotedTupleEvents);
    }

    /**
     * Promotes recent, high-confidence declaration tuples that no XBRL event claimed into
     * provisional dividend events. XBRL per-share facts only appear with the next 10-Q/10-K, so
     * without this a declared dividend is invisible to price adjustment for up to a quarter and
     * the adjusted series shifts retroactively when the fact lands. The provisional event carries
     * tuple provenance, so the later XBRL-grounded re-detection matches and updates it in place.
     */
    private int promoteUnmatchedDeclarations(
            List<EquityCorporateActionService.DividendEvent> mapped,
            List<EquityCorporateActionService.DividendEvent> regularEvents,
            List<DividendDeclarationTupleExtractor.DividendDeclaration> tuples,
            Set<Integer> usedTuples) {
        if (tuples.isEmpty() || regularEvents.isEmpty()) {
            return 0;
        }
        LocalDate newestPeriodEnd = regularEvents.get(regularEvents.size() - 1).fiscalPeriodEnd();
        double newestRegularAmount = regularEvents.get(regularEvents.size() - 1).rawAmount();
        LocalDate oldestPromotableRecordDate = LocalDate.now().minusDays(MAX_PROMOTION_RECORD_AGE_DAYS);
        Map<LocalDate, DividendDeclarationTupleExtractor.DividendDeclaration> bestByRecordDate = new HashMap<>();
        for (int i = 0; i < tuples.size(); i++) {
            if (usedTuples.contains(i)) {
                continue;
            }
            DividendDeclarationTupleExtractor.DividendDeclaration tuple = tuples.get(i);
            if (tuple.recordDate() == null
                    || !tuple.recordDate().isAfter(newestPeriodEnd)
                    || tuple.recordDate().isBefore(oldestPromotableRecordDate)
                    || tuple.confidenceScore() < MIN_PROMOTION_CONFIDENCE) {
                continue;
            }
            if (newestRegularAmount > 0
                    && (tuple.amountPerShare() > newestRegularAmount * MAX_PROMOTION_AMOUNT_RATIO
                    || tuple.amountPerShare() < newestRegularAmount / MAX_PROMOTION_AMOUNT_RATIO)) {
                log.info("Skipping declaration promotion for record date {}: amount {} out of band vs latest regular {}",
                        tuple.recordDate(), tuple.amountPerShare(), newestRegularAmount);
                continue;
            }
            DividendDeclarationTupleExtractor.DividendDeclaration current = bestByRecordDate.get(tuple.recordDate());
            if (current == null || tuple.confidenceScore() > current.confidenceScore()) {
                bestByRecordDate.put(tuple.recordDate(), tuple);
            }
        }
        int promoted = 0;
        for (DividendDeclarationTupleExtractor.DividendDeclaration tuple : bestByRecordDate.values()) {
            LocalDate exDate = tuple.exDate() != null
                    ? tuple.exDate()
                    : safeComputeExDividendDate(tuple.recordDate(), tuple.recordDate());
            log.info("Promoting declaration tuple to provisional dividend: amount {}, record {}, ex {} (filing {})",
                    tuple.amountPerShare(), tuple.recordDate(), exDate, tuple.accessionNumber());
            mapped.add(new EquityCorporateActionService.DividendEvent(
                    tuple.recordDate(),
                    exDate,
                    tuple.amountPerShare(),
                    tuple.amountPerShare(),
                    tuple.recordDate().getYear(),
                    false,
                    tuple.recordDate(),
                    tuple.payableDate(),
                    CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED));
            promoted++;
        }
        return promoted;
    }

    private EquityCorporateActionService.DividendEvent eventFromTuple(
            EquityCorporateActionService.DividendEvent event,
            DividendDeclarationTupleExtractor.DividendDeclaration tuple,
            boolean special) {
        LocalDate exDate = tuple.exDate() != null
                ? tuple.exDate()
                : safeComputeExDividendDate(tuple.recordDate(), event.fiscalPeriodEnd());
        log.debug("Tuple-matched ex-date {} for fiscal period end {} (amount {}, record {}, filing {})",
                exDate, event.fiscalPeriodEnd(), tuple.amountPerShare(), tuple.recordDate(), tuple.accessionNumber());
        return new EquityCorporateActionService.DividendEvent(
                event.fiscalPeriodEnd(),
                exDate,
                event.rawAmount(),
                event.adjustedAmount(),
                event.year(),
                special,
                tuple.recordDate(),
                tuple.payableDate(),
                CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED);
    }

    /**
     * Matches an XBRL event to an unused declaration tuple by amount (declared basis or the
     * split-restated variant) with the record date inside the post-period window. This anchors
     * the ex-date to the actual declaration instead of the cadence prior.
     */
    private Integer findBestDeclarationIndex(
            EquityCorporateActionService.DividendEvent event,
            List<DividendDeclarationTupleExtractor.DividendDeclaration> tuples,
            Set<Integer> usedTuples,
            List<CorporateAction> knownSplits) {
        if (tuples.isEmpty()) {
            return null;
        }
        double futureFactor = futureSplitFactor(event.fiscalPeriodEnd(), knownSplits);
        Integer best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < tuples.size(); i++) {
            if (usedTuples.contains(i)) {
                continue;
            }
            DividendDeclarationTupleExtractor.DividendDeclaration tuple = tuples.get(i);
            if (tuple.recordDate() == null) {
                continue;
            }
            long offset = ChronoUnit.DAYS.between(event.fiscalPeriodEnd(), tuple.recordDate());
            if (offset < 0 || offset > MAX_TUPLE_RECORD_OFFSET_DAYS) {
                continue;
            }
            if (!amountMatchesTuple(event.rawAmount(), tuple.amountPerShare(), futureFactor)) {
                continue;
            }
            double score = Math.abs(offset - 45) - tuple.confidenceScore() / 25.0;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Declared amount matches the XBRL amount as-is, or after undoing future split restatement. */
    private boolean amountMatchesTuple(double rawAmount, double tupleAmount, double futureFactor) {
        if (amountsMatch(rawAmount, tupleAmount)) {
            return true;
        }
        return futureFactor > 0 && futureFactor != 1.0 && amountsMatch(rawAmount / futureFactor, tupleAmount);
    }

    private boolean amountsMatch(double left, double right) {
        double tolerance = Math.max(TUPLE_AMOUNT_ABS_TOLERANCE, right * TUPLE_AMOUNT_REL_TOLERANCE);
        return Math.abs(left - right) <= tolerance;
    }

    /** Product of split price-ratios effective after the period end (restatement factor). */
    private double futureSplitFactor(LocalDate periodEnd, List<CorporateAction> knownSplits) {
        double factor = 1.0;
        for (CorporateAction split : knownSplits) {
            if (split.getEffectiveDate() != null && split.getEffectiveDate().isAfter(periodEnd)) {
                factor *= split.getRatio();
            }
        }
        return factor;
    }

    private Integer findBestDirectExIndex(
            EquityCorporateActionService.DividendEvent event,
            List<CorporateActionFilingDateService.ExDividendDateCandidate> exDirect,
            Set<Integer> used) {
        if (exDirect == null || exDirect.isEmpty()) {
            return null;
        }
        LocalDate fiscal = event.fiscalPeriodEnd();
        Integer best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < exDirect.size(); i++) {
            if (used.contains(i)) {
                continue;
            }
            LocalDate ex = exDirect.get(i).exDividendDate();
            if (ex == null || !ex.isAfter(fiscal.minusDays(1))) {
                continue;
            }
            long gap = ChronoUnit.DAYS.between(fiscal, ex);
            if (gap < MIN_DAYS_FISCAL_TO_EX || gap > MAX_DAYS_FISCAL_TO_EX) {
                continue;
            }
            double score = Math.abs(gap - 45) - exDirect.get(i).confidenceScore() / 25.0;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private SpecialMappingResult mapSpecialDividendExDate(
            EquityCorporateActionService.DividendEvent event,
            List<CorporateActionFilingDateService.RecordDateCandidate> sortedCandidates,
            Set<Integer> usedCandidateIndexes) {
        int bestIndex = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < sortedCandidates.size(); i++) {
            if (usedCandidateIndexes.contains(i)) {
                continue;
            }
            CorporateActionFilingDateService.RecordDateCandidate candidate = sortedCandidates.get(i);
            long dayOffset = ChronoUnit.DAYS.between(event.fiscalPeriodEnd(), candidate.recordDate());
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
            LocalDate recordDate = sortedCandidates.get(bestIndex).recordDate();
            return new SpecialMappingResult(
                    safeComputeExDividendDate(recordDate, event.fiscalPeriodEnd()),
                    true,
                    recordDate);
        }
        LocalDate fallback = inferFallbackExDate(event.fiscalPeriodEnd(), null);
        log.info("Using low-confidence fallback ex-date {} for special dividend period end {}", fallback, event.fiscalPeriodEnd());
        return new SpecialMappingResult(fallback, false, null);
    }

    private List<Integer> optimizeRecordDateAssignment(
            List<EquityCorporateActionService.DividendEvent> events,
            List<CorporateActionFilingDateService.RecordDateCandidate> candidates) {
        int n = events.size();
        int m = candidates.size();
        if (n == 0) {
            return List.of();
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
                    CorporateActionFilingDateService.RecordDateCandidate candidate = candidates.get(j);
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

    private boolean isCandidateEligible(EquityCorporateActionService.DividendEvent event, CorporateActionFilingDateService.RecordDateCandidate candidate) {
        long dayOffset = ChronoUnit.DAYS.between(event.fiscalPeriodEnd(), candidate.recordDate());
        if (dayOffset < MIN_RECORD_DATE_OFFSET_DAYS || dayOffset > MAX_RECORD_DATE_OFFSET_DAYS) {
            return false;
        }
        return candidate.filingDate() == null || !candidate.filingDate().isBefore(event.fiscalPeriodEnd().minusDays(5));
    }

    private double candidateMatchScore(
            EquityCorporateActionService.DividendEvent event,
            CorporateActionFilingDateService.RecordDateCandidate candidate,
            CorporateActionFilingDateService.RecordDateCandidate prevCandidate) {
        long dayOffset = ChronoUnit.DAYS.between(event.fiscalPeriodEnd(), candidate.recordDate());
        double score = Math.abs(dayOffset - 42);
        if (prevCandidate != null) {
            long cadenceGap = ChronoUnit.DAYS.between(prevCandidate.recordDate(), candidate.recordDate());
            score += Math.abs(cadenceGap - QUARTER_CADENCE_DAYS) / 2.0;
        }
        score -= candidate.confidenceScore() / 12.0;
        return score;
    }

    private LocalDate inferFallbackExDate(LocalDate periodEnd, LocalDate lastMatchedRecordDate) {
        long defaultFallbackDays = 42;
        LocalDate inferredRecordDate;
        if (lastMatchedRecordDate != null) {
            inferredRecordDate = lastMatchedRecordDate.plusDays(QUARTER_CADENCE_DAYS);
            if (Math.abs(ChronoUnit.DAYS.between(periodEnd, inferredRecordDate)) > MAX_RECORD_DATE_OFFSET_DAYS) {
                inferredRecordDate = periodEnd.plusDays(defaultFallbackDays);
            }
        } else {
            inferredRecordDate = periodEnd.plusDays(defaultFallbackDays);
        }
        return safeComputeExDividendDate(inferredRecordDate, periodEnd);
    }

    private LocalDate safeComputeExDividendDate(LocalDate recordDate, LocalDate fallbackDate) {
        LocalDate computed = corporateActionFilingDateService.computeExDividendDate(recordDate);
        if (computed != null) {
            return computed;
        }
        return recordDate != null ? recordDate : fallbackDate;
    }

    private record SpecialMappingResult(LocalDate effectiveDate, boolean recordBased, LocalDate recordDate) {
    }
}

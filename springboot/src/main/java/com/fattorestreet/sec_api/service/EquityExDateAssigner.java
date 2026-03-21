package com.fattorestreet.sec_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class EquityExDateAssigner {

    private static final Logger log = LoggerFactory.getLogger(EquityExDateAssigner.class);
    private static final int MIN_RECORD_DATE_OFFSET_DAYS = 3;
    private static final int MAX_RECORD_DATE_OFFSET_DAYS = 120;
    private static final long QUARTER_CADENCE_DAYS = 91;
    private static final int FALLBACK_PENALTY = 140;

    private final DividendRecordDateService dividendRecordDateService;

    public EquityExDateAssigner(DividendRecordDateService dividendRecordDateService) {
        this.dividendRecordDateService = dividendRecordDateService;
    }

    public EquityCorporateActionService.AssignmentResult assignExDividendDates(
            List<EquityCorporateActionService.DividendEvent> normalized,
            List<DividendRecordDateService.RecordDateCandidate> recordDateCandidates) {
        List<DividendRecordDateService.RecordDateCandidate> sortedCandidates = new ArrayList<>(recordDateCandidates);
        sortedCandidates.sort(Comparator
                .comparing(DividendRecordDateService.RecordDateCandidate::recordDate)
                .thenComparing(DividendRecordDateService.RecordDateCandidate::confidenceScore, Comparator.reverseOrder())
                .thenComparing(DividendRecordDateService.RecordDateCandidate::filingDate));

        List<EquityCorporateActionService.DividendEvent> regularEvents = normalized.stream()
                .filter(e -> !e.specialEvent())
                .sorted(Comparator.comparing(EquityCorporateActionService.DividendEvent::periodEnd))
                .toList();
        List<EquityCorporateActionService.DividendEvent> specialEvents = normalized.stream()
                .filter(EquityCorporateActionService.DividendEvent::specialEvent)
                .sorted(Comparator.comparing(EquityCorporateActionService.DividendEvent::periodEnd))
                .toList();

        List<EquityCorporateActionService.DividendEvent> mapped = new ArrayList<>(normalized.size());
        int recordBasedAssignments = 0;
        int fallbackAssignments = 0;
        List<Integer> assignment = optimizeRecordDateAssignment(regularEvents, sortedCandidates);
        Set<Integer> usedCandidateIndexes = new HashSet<>();
        LocalDate lastMatchedRecordDate = null;
        for (int i = 0; i < regularEvents.size(); i++) {
            EquityCorporateActionService.DividendEvent event = regularEvents.get(i);
            int chosen = assignment.get(i);
            if (chosen >= 0) {
                DividendRecordDateService.RecordDateCandidate candidate = sortedCandidates.get(chosen);
                lastMatchedRecordDate = candidate.recordDate();
                usedCandidateIndexes.add(chosen);
                LocalDate effectiveDate = safeComputeExDividendDate(candidate.recordDate(), event.periodEnd());
                mapped.add(new EquityCorporateActionService.DividendEvent(effectiveDate, event.rawAmount(), event.adjustedAmount(), event.year(), false));
                recordBasedAssignments++;
                continue;
            }
            LocalDate inferred = inferFallbackExDate(event.periodEnd(), lastMatchedRecordDate);
            log.debug("Using inferred ex-date {} for period end {} due to low-confidence record-date match",
                    inferred, event.periodEnd());
            mapped.add(new EquityCorporateActionService.DividendEvent(inferred, event.rawAmount(), event.adjustedAmount(), event.year(), false));
            fallbackAssignments++;
        }

        for (EquityCorporateActionService.DividendEvent special : specialEvents) {
            SpecialMappingResult specialMapping = mapSpecialDividendExDate(special, sortedCandidates, usedCandidateIndexes);
            mapped.add(new EquityCorporateActionService.DividendEvent(
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
        mapped.sort(Comparator.comparing(EquityCorporateActionService.DividendEvent::periodEnd).thenComparing(EquityCorporateActionService.DividendEvent::rawAmount));
        return new EquityCorporateActionService.AssignmentResult(mapped, recordBasedAssignments, fallbackAssignments);
    }

    private SpecialMappingResult mapSpecialDividendExDate(
            EquityCorporateActionService.DividendEvent event,
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
        log.info("Using low-confidence fallback ex-date {} for special dividend period end {}", fallback, event.periodEnd());
        return new SpecialMappingResult(fallback, false);
    }

    private List<Integer> optimizeRecordDateAssignment(
            List<EquityCorporateActionService.DividendEvent> events,
            List<DividendRecordDateService.RecordDateCandidate> candidates) {
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

    private boolean isCandidateEligible(EquityCorporateActionService.DividendEvent event, DividendRecordDateService.RecordDateCandidate candidate) {
        long dayOffset = ChronoUnit.DAYS.between(event.periodEnd(), candidate.recordDate());
        if (dayOffset < MIN_RECORD_DATE_OFFSET_DAYS || dayOffset > MAX_RECORD_DATE_OFFSET_DAYS) {
            return false;
        }
        return candidate.filingDate() == null || !candidate.filingDate().isBefore(event.periodEnd().minusDays(5));
    }

    private double candidateMatchScore(
            EquityCorporateActionService.DividendEvent event,
            DividendRecordDateService.RecordDateCandidate candidate,
            DividendRecordDateService.RecordDateCandidate prevCandidate) {
        long dayOffset = ChronoUnit.DAYS.between(event.periodEnd(), candidate.recordDate());
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
        LocalDate computed = dividendRecordDateService.computeExDividendDate(recordDate);
        if (computed != null) {
            return computed;
        }
        return recordDate != null ? recordDate : fallbackDate;
    }

    private record SpecialMappingResult(LocalDate effectiveDate, boolean recordBased) {
    }
}

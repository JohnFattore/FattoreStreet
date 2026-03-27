package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.DividendRecordDateService;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class EquitySplitDetector {

    private static final Logger log = LoggerFactory.getLogger(EquitySplitDetector.class);
    /** Forward split multipliers (new shares / old shares) considered "standard" without extra filing proof. */
    private static final Set<Double> PRIMARY_SPLIT_RATIOS = Set.of(
            2.0, 3.0, 4.0, 5.0, 7.0, 10.0, 20.0, 50.0,
            0.5, 1.0 / 3, 0.25, 0.2, 1.0 / 7, 0.1, 0.05
    );
    /** e.g. 3:2 (1.5), 4:3 — require SEC filing split-date match before persisting. */
    private static final Set<Double> EXTENDED_SPLIT_RATIOS = Set.of(1.5, 4.0 / 3.0);
    private static final double RATIO_TOLERANCE = 0.02;
    private static final int MAX_SPLIT_CANDIDATE_LEAD_DAYS = 260;
    private static final int MAX_SPLIT_CANDIDATE_LAG_DAYS = 60;

    private final DividendRecordDateService dividendRecordDateService;
    private final CorporateActionRepository corporateActionRepository;

    public EquitySplitDetector(
            DividendRecordDateService dividendRecordDateService,
            CorporateActionRepository corporateActionRepository) {
        this.dividendRecordDateService = dividendRecordDateService;
        this.corporateActionRepository = corporateActionRepository;
    }

    public EquityCorporateActionService.SplitDetectionStats detectSplits(String ticker, Long cik, JsonNode root) {
        JsonNode sharesNode = navigatePath(root,
                "facts", "dei", "EntityCommonStockSharesOutstanding", "units", "shares");
        if (sharesNode == null || !sharesNode.isArray()) {
            return new EquityCorporateActionService.SplitDetectionStats(0, 0, 0, 0, 0);
        }

        List<SharesEntry> entries = new ArrayList<>();
        for (JsonNode entry : sharesNode) {
            String form = entry.has("form") ? entry.get("form").asText() : "";
            if (!isRelevantSplitForm(form)) {
                continue;
            }
            if (!entry.has("val") || !entry.has("end")) {
                continue;
            }
            long val = entry.get("val").asLong();
            LocalDate endDate = LocalDate.parse(entry.get("end").asText());
            if (val > 0) {
                entries.add(new SharesEntry(endDate, val));
            }
        }

        entries.sort(Comparator.comparing(SharesEntry::date));
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
            if (!isCommonSplitRatio(rawRatio)) {
                continue;
            }
            double snappedRaw = nearestCommonSplitRatio(rawRatio);
            double splitRatio = 1.0 / snappedRaw;
            SplitDateResolution splitDateResolution = resolveSplitEffectiveDate(prev.date(), curr.date(), splitCandidates, usedSplitCandidateKeys);
            LocalDate effectiveDate = splitDateResolution.effectiveDate();
            if (splitDateResolution.matchedCandidate()) {
                secDateMatches++;
            } else {
                fallbackDetectedDate++;
            }
            if (!isPrimarySplitRatio(snappedRaw) && !splitDateResolution.matchedCandidate()) {
                log.info("[{}] Skipping extended split ratio {} (shares {} -> {}) without SEC filing split-date confirmation",
                        ticker, String.format("%.4f", snappedRaw), prev.shares(), curr.shares());
                continue;
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
                        prev.shares(), curr.shares());
            }
        }
        return new EquityCorporateActionService.SplitDetectionStats(entries.size(), splitCandidates.size(), created, secDateMatches, fallbackDetectedDate);
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

    private boolean isCommonSplitRatio(double rawRatio) {
        return isNearAnyRatio(rawRatio, allSplitRatios());
    }

    private boolean isPrimarySplitRatio(double snappedRaw) {
        return isNearAnyRatio(snappedRaw, PRIMARY_SPLIT_RATIOS);
    }

    private Set<Double> allSplitRatios() {
        java.util.HashSet<Double> out = new java.util.HashSet<>(PRIMARY_SPLIT_RATIOS);
        out.addAll(EXTENDED_SPLIT_RATIOS);
        return out;
    }

    private boolean isNearAnyRatio(double rawRatio, Set<Double> ratios) {
        for (double common : ratios) {
            if (Math.abs(rawRatio - common) / common < RATIO_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private double nearestCommonSplitRatio(double rawRatio) {
        double best = rawRatio;
        double bestRelErr = Double.MAX_VALUE;
        for (double common : allSplitRatios()) {
            double relErr = Math.abs(rawRatio - common) / common;
            if (relErr < bestRelErr) {
                bestRelErr = relErr;
                best = common;
            }
        }
        return bestRelErr < RATIO_TOLERANCE ? best : rawRatio;
    }

    private void removeDuplicateDates(List<SharesEntry> entries) {
        Map<LocalDate, SharesEntry> byDate = new LinkedHashMap<>();
        for (SharesEntry e : entries) {
            byDate.put(e.date(), e);
        }
        entries.clear();
        entries.addAll(byDate.values());
    }

    private JsonNode navigatePath(JsonNode node, String... path) {
        for (String key : path) {
            if (node == null) {
                return null;
            }
            node = node.get(key);
        }
        return node;
    }

    private boolean isRelevantSplitForm(String form) {
        if (form == null || form.isBlank()) {
            return false;
        }
        String normalized = form.trim().toUpperCase(java.util.Locale.US);
        if (normalized.equals("10-K")
                || normalized.equals("10-K/A")
                || normalized.equals("10-Q")
                || normalized.equals("10-Q/A")) {
            return true;
        }
        if (normalized.equals("8-K")
                || normalized.equals("8-K/A")
                || normalized.equals("6-K")
                || normalized.equals("20-F")
                || normalized.equals("40-F")) {
            return true;
        }
        return normalized.endsWith("/A");
    }

    private record SplitDateResolution(LocalDate effectiveDate, boolean matchedCandidate) {
    }

    private record SharesEntry(LocalDate date, long shares) {
    }
}

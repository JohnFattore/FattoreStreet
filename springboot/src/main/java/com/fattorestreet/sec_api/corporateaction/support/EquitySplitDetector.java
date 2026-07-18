package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import tools.jackson.databind.JsonNode;
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
    private static final int MAX_SHARES_ENTRY_GAP_DAYS = 400;
    /** Padding around the share-fact bracket when searching the price series for the split break. */
    private static final int PRICE_SEARCH_PAD_DAYS = 10;
    /** Warn when the price break and the SEC filing split date disagree by more than this. */
    private static final int BREAK_FILING_DISAGREEMENT_WARN_DAYS = 7;
    /** A same-ratio split within this window of the new date is re-dated, not duplicated. */
    private static final int RECONCILE_WINDOW_DAYS = 90;
    private static final double RECONCILE_RATIO_TOLERANCE = 0.01;
    /** A scan break within this window of a persisted split is considered already explained. */
    private static final int EXPLAINED_BREAK_WINDOW_DAYS = 7;
    /** A filing split-date candidate within this window confirms a price-only break. */
    private static final int PRICE_ONLY_FILING_WINDOW_DAYS = 14;
    /** Price-only breaks at least this large (or the reverse equivalent) persist without filing proof. */
    private static final double SOLE_SOURCE_MIN_MULTIPLIER = 5.0;
    private static final double CONFIDENCE_PRICE_BREAK = 100;
    private static final double CONFIDENCE_FILING_TEXT = 70;
    private static final double CONFIDENCE_SHARE_FACT = 40;
    private static final double CONFIDENCE_PRICE_ONLY_CONFIRMED = 90;
    private static final double CONFIDENCE_PRICE_ONLY_SOLE = 60;

    private final CorporateActionFilingDateService corporateActionFilingDateService;
    private final CorporateActionRepository corporateActionRepository;
    private final SplitPriceCorroborator splitPriceCorroborator;

    public EquitySplitDetector(
            CorporateActionFilingDateService corporateActionFilingDateService,
            CorporateActionRepository corporateActionRepository,
            SplitPriceCorroborator splitPriceCorroborator) {
        this.corporateActionFilingDateService = corporateActionFilingDateService;
        this.corporateActionRepository = corporateActionRepository;
        this.splitPriceCorroborator = splitPriceCorroborator;
    }

    public EquityCorporateActionService.SplitDetectionStats detectSplits(String ticker, Long cik, JsonNode root) {
        JsonNode sharesNode = navigatePath(root,
                "facts", "dei", "EntityCommonStockSharesOutstanding", "units", "shares");
        if (sharesNode == null || !sharesNode.isArray()) {
            return new EquityCorporateActionService.SplitDetectionStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
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
        List<CorporateActionFilingDateService.SplitDateCandidate> splitCandidates = corporateActionFilingDateService.fetchSplitEffectiveDates(cik);
        Set<String> usedSplitCandidateKeys = new HashSet<>();
        SplitPriceCorroborator.PriceSeries prices = splitPriceCorroborator.load(ticker);
        List<CorporateAction> existingSplits = new ArrayList<>();
        for (CorporateAction action : corporateActionRepository.findByTicker(ticker)) {
            if (action.getActionType() == ActionType.SPLIT) {
                existingSplits.add(action);
            }
        }
        int secDateMatches = 0;
        int fallbackDetectedDate = 0;
        int priceCorroborated = 0;
        int priceSnapped = 0;
        int priceRejected = 0;
        log.info("[{}] Split detection inputs: {} shares rows, {} SEC split-date candidates, {} price rows",
                ticker, entries.size(), splitCandidates.size(), prices.dates().size());

        int created = 0;
        for (int i = 1; i < entries.size(); i++) {
            SharesEntry prev = entries.get(i - 1);
            SharesEntry curr = entries.get(i);
            long gapDays = ChronoUnit.DAYS.between(prev.date(), curr.date());
            if (gapDays > MAX_SHARES_ENTRY_GAP_DAYS) {
                log.debug("[{}] Skipping shares pair {} -> {}: gap {} days exceeds maximum {}",
                        ticker, prev.date(), curr.date(), gapDays, MAX_SHARES_ENTRY_GAP_DAYS);
                continue;
            }
            double rawRatio = (double) curr.shares / prev.shares;
            if (!isCommonSplitRatio(rawRatio)) {
                continue;
            }
            double snappedRaw = nearestCommonSplitRatio(rawRatio);
            double splitRatio = 1.0 / snappedRaw;
            SplitDateResolution splitDateResolution = resolveSplitEffectiveDate(prev.date(), curr.date(), splitCandidates, usedSplitCandidateKeys);
            if (splitDateResolution.matchedCandidate()) {
                secDateMatches++;
            } else {
                fallbackDetectedDate++;
            }

            LocalDate windowStart = prev.date().minusDays(PRICE_SEARCH_PAD_DAYS);
            LocalDate windowEnd = curr.date().plusDays(PRICE_SEARCH_PAD_DAYS);
            if (splitDateResolution.matchedCandidate()) {
                LocalDate candidateDate = splitDateResolution.effectiveDate();
                if (candidateDate.minusDays(PRICE_SEARCH_PAD_DAYS).isBefore(windowStart)) {
                    windowStart = candidateDate.minusDays(PRICE_SEARCH_PAD_DAYS);
                }
                if (candidateDate.plusDays(PRICE_SEARCH_PAD_DAYS).isAfter(windowEnd)) {
                    windowEnd = candidateDate.plusDays(PRICE_SEARCH_PAD_DAYS);
                }
            }
            java.util.Optional<SplitPriceCorroborator.Corroboration> corroboration =
                    splitPriceCorroborator.corroborate(prices, snappedRaw, windowStart, windowEnd);

            LocalDate effectiveDate;
            String exDateSource;
            double confidence;
            if (corroboration.isPresent()) {
                priceCorroborated++;
                effectiveDate = corroboration.get().breakDate();
                exDateSource = CorporateAction.EX_DATE_SOURCE_PRICE_BREAK;
                confidence = CONFIDENCE_PRICE_BREAK;
                if (!effectiveDate.equals(splitDateResolution.effectiveDate())) {
                    priceSnapped++;
                }
                if (splitDateResolution.matchedCandidate()
                        && Math.abs(ChronoUnit.DAYS.between(splitDateResolution.effectiveDate(), effectiveDate)) > BREAK_FILING_DISAGREEMENT_WARN_DAYS) {
                    log.warn("[{}] Price break {} disagrees with SEC filing split date {} by more than {} days; keeping the price break",
                            ticker, effectiveDate, splitDateResolution.effectiveDate(), BREAK_FILING_DISAGREEMENT_WARN_DAYS);
                }
            } else if (splitPriceCorroborator.windowFullyCovered(prices, windowStart, windowEnd)) {
                // Full price coverage with no matching break: the share-count jump is not a split.
                priceRejected++;
                log.warn("[{}] Rejecting split candidate ratio {} (shares {} -> {}): no {}x price break in fully covered window {}..{}",
                        ticker, String.format("%.4f", splitRatio), prev.shares(), curr.shares(),
                        String.format("%.2f", snappedRaw), windowStart, windowEnd);
                continue;
            } else {
                if (!isPrimarySplitRatio(snappedRaw) && !splitDateResolution.matchedCandidate()) {
                    log.info("[{}] Skipping extended split ratio {} (shares {} -> {}) without SEC filing split-date confirmation",
                            ticker, String.format("%.4f", snappedRaw), prev.shares(), curr.shares());
                    continue;
                }
                effectiveDate = splitDateResolution.effectiveDate();
                exDateSource = splitDateResolution.matchedCandidate()
                        ? CorporateAction.EX_DATE_SOURCE_FILING_TEXT
                        : CorporateAction.EX_DATE_SOURCE_SHARE_FACT;
                confidence = splitDateResolution.matchedCandidate() ? CONFIDENCE_FILING_TEXT : CONFIDENCE_SHARE_FACT;
            }

            if (persistOrReconcile(ticker, existingSplits, splitRatio, effectiveDate,
                    CorporateAction.SourceType.SEC_EQUITY_XBRL, exDateSource, confidence,
                    "shares " + prev.shares() + " -> " + curr.shares())) {
                created++;
            }
        }

        PriceOnlyScanResult priceOnly = detectPriceOnlySplits(ticker, prices, existingSplits, splitCandidates);
        created += priceOnly.created();
        return new EquityCorporateActionService.SplitDetectionStats(
                entries.size(), splitCandidates.size(), created, secDateMatches, fallbackDetectedDate,
                priceCorroborated, priceSnapped, priceRejected,
                priceOnly.created(), priceOnly.unconfirmed());
    }

    /**
     * Persists a new split or reconciles it onto an existing same-ratio row within
     * {@link #RECONCILE_WINDOW_DAYS}. Re-dating updates in place — inserting at the new date would
     * leave both rows live (the unique constraint is per-date) and double-adjust the series. An
     * existing row's date only moves when the new resolution is at least as well grounded.
     *
     * @return true when a new row was inserted
     */
    private boolean persistOrReconcile(
            String ticker,
            List<CorporateAction> existingSplits,
            double splitRatio,
            LocalDate effectiveDate,
            CorporateAction.SourceType sourceType,
            String exDateSource,
            double confidence,
            String evidence) {
        CorporateAction existing = findReconcilableSplit(existingSplits, splitRatio, effectiveDate);
        if (existing != null) {
            double existingConfidence = existing.getConfidenceScore() != null ? existing.getConfidenceScore() : -1;
            boolean dateChanged = !effectiveDate.equals(existing.getEffectiveDate());
            if (confidence < existingConfidence || (!dateChanged && confidence == existingConfidence)) {
                return false;
            }
            if (dateChanged) {
                log.info("[{}] Re-dating split ratio {} from {} to {} ({} -> {})",
                        ticker, String.format("%.4f", splitRatio),
                        existing.getEffectiveDate(), effectiveDate,
                        existing.getExDateSource(), exDateSource);
                existing.setEffectiveDate(effectiveDate);
            }
            existing.setExDateSource(exDateSource);
            existing.setConfidenceScore(confidence);
            corporateActionRepository.save(existing);
            return false;
        }
        CorporateAction action = new CorporateAction();
        action.setTicker(ticker);
        action.setActionType(ActionType.SPLIT);
        action.setEffectiveDate(effectiveDate);
        action.setRatio(splitRatio);
        action.setSourceType(sourceType);
        action.setExDateSource(exDateSource);
        action.setConfidenceScore(confidence);
        corporateActionRepository.save(action);
        existingSplits.add(action);
        log.info("[{}] Detected split on {}: ratio {} ({}, dateSource={})",
                ticker, effectiveDate, String.format("%.4f", splitRatio), evidence, exDateSource);
        return true;
    }

    private CorporateAction findReconcilableSplit(
            List<CorporateAction> existingSplits, double splitRatio, LocalDate effectiveDate) {
        CorporateAction best = null;
        long bestDistance = Long.MAX_VALUE;
        for (CorporateAction action : existingSplits) {
            if (action.getRatio() == null || action.getEffectiveDate() == null) {
                continue;
            }
            if (Math.abs(action.getRatio() - splitRatio) / splitRatio >= RECONCILE_RATIO_TOLERANCE) {
                continue;
            }
            long distance = Math.abs(ChronoUnit.DAYS.between(action.getEffectiveDate(), effectiveDate));
            if (distance <= RECONCILE_WINDOW_DAYS && distance < bestDistance) {
                best = action;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Scans the raw price series for split-like overnight breaks the XBRL share counts missed.
     * Mid-size multipliers need a SEC filing split-date candidate nearby; only very large
     * persistent moves (≥{@link #SOLE_SOURCE_MIN_MULTIPLIER}x) persist on price evidence alone.
     */
    private PriceOnlyScanResult detectPriceOnlySplits(
            String ticker,
            SplitPriceCorroborator.PriceSeries prices,
            List<CorporateAction> existingSplits,
            List<CorporateActionFilingDateService.SplitDateCandidate> splitCandidates) {
        int created = 0;
        int unconfirmed = 0;
        for (SplitPriceCorroborator.Corroboration breakCandidate : splitPriceCorroborator.scanForSplitLikeBreaks(prices, null)) {
            if (hasSplitNear(existingSplits, breakCandidate.breakDate())) {
                continue;
            }
            double multiplier = breakCandidate.snappedMultiplier();
            double splitRatio = 1.0 / multiplier;
            boolean soleSourceEligible = multiplier >= SOLE_SOURCE_MIN_MULTIPLIER
                    || multiplier <= 1.0 / SOLE_SOURCE_MIN_MULTIPLIER;
            CorporateActionFilingDateService.SplitDateCandidate filingCandidate =
                    findFilingCandidateNear(splitCandidates, breakCandidate.breakDate());
            if (filingCandidate == null && !soleSourceEligible) {
                unconfirmed++;
                log.info("[{}] Unconfirmed price-only split break on {} (observed {}x, snapped {}x): no SEC filing candidate within {} days",
                        ticker, breakCandidate.breakDate(),
                        String.format("%.3f", breakCandidate.observedMultiplier()),
                        String.format("%.2f", multiplier), PRICE_ONLY_FILING_WINDOW_DAYS);
                continue;
            }
            double confidence = filingCandidate != null ? CONFIDENCE_PRICE_ONLY_CONFIRMED : CONFIDENCE_PRICE_ONLY_SOLE;
            CorporateAction action = new CorporateAction();
            action.setTicker(ticker);
            action.setActionType(ActionType.SPLIT);
            action.setEffectiveDate(breakCandidate.breakDate());
            action.setRatio(splitRatio);
            action.setSourceType(CorporateAction.SourceType.SEC_PRICE_CORROBORATED);
            action.setExDateSource(CorporateAction.EX_DATE_SOURCE_PRICE_BREAK);
            action.setConfidenceScore(confidence);
            if (filingCandidate != null) {
                action.setAccessionNumber(filingCandidate.accessionNumber());
            }
            corporateActionRepository.save(action);
            existingSplits.add(action);
            created++;
            log.info("[{}] Detected price-only split on {}: ratio {} (observed {}x, filingCandidate={})",
                    ticker, breakCandidate.breakDate(), String.format("%.4f", splitRatio),
                    String.format("%.3f", breakCandidate.observedMultiplier()),
                    filingCandidate != null ? filingCandidate.accessionNumber() : "none");
        }
        return new PriceOnlyScanResult(created, unconfirmed);
    }

    private boolean hasSplitNear(List<CorporateAction> existingSplits, LocalDate date) {
        for (CorporateAction action : existingSplits) {
            if (action.getEffectiveDate() != null
                    && Math.abs(ChronoUnit.DAYS.between(action.getEffectiveDate(), date)) <= EXPLAINED_BREAK_WINDOW_DAYS) {
                return true;
            }
        }
        return false;
    }

    private CorporateActionFilingDateService.SplitDateCandidate findFilingCandidateNear(
            List<CorporateActionFilingDateService.SplitDateCandidate> splitCandidates, LocalDate date) {
        CorporateActionFilingDateService.SplitDateCandidate best = null;
        long bestDistance = Long.MAX_VALUE;
        for (CorporateActionFilingDateService.SplitDateCandidate candidate : splitCandidates) {
            long distance = Math.abs(ChronoUnit.DAYS.between(candidate.effectiveDate(), date));
            if (distance <= PRICE_ONLY_FILING_WINDOW_DAYS && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private record PriceOnlyScanResult(int created, int unconfirmed) {
    }

    private SplitDateResolution resolveSplitEffectiveDate(
            LocalDate prevDate,
            LocalDate detectedDate,
            List<CorporateActionFilingDateService.SplitDateCandidate> splitCandidates,
            Set<String> usedSplitCandidateKeys) {
        CorporateActionFilingDateService.SplitDateCandidate bestCandidate = null;
        double bestScore = Double.MAX_VALUE;
        int eligibleCandidates = 0;
        List<String> rejected = new ArrayList<>();
        for (CorporateActionFilingDateService.SplitDateCandidate candidate : splitCandidates) {
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
            SharesEntry existing = byDate.get(e.date());
            if (existing == null || e.shares() > existing.shares()) {
                byDate.put(e.date(), e);
            }
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

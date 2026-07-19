package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class EquityDividendUpserter {

    private static final Logger log = LoggerFactory.getLogger(EquityDividendUpserter.class);
    private static final double DIVIDEND_EPSILON = 0.000001d;

    private final CorporateActionRepository corporateActionRepository;

    public EquityDividendUpserter(CorporateActionRepository corporateActionRepository) {
        this.corporateActionRepository = corporateActionRepository;
    }

    public EquityCorporateActionService.UpsertStats upsertDividendEvents(
            String ticker,
            List<EquityCorporateActionService.DividendEvent> detectedEvents) {
        return upsertDividendEvents(ticker, detectedEvents, false);
    }

    /**
     * @param degradedScan true when the filing scan had enough fetch failures that missing
     *                     declarations are not evidence of absence; suppresses synthetic-dated
     *                     inserts and pruning so a transient SEC outage cannot degrade stored data
     */
    public EquityCorporateActionService.UpsertStats upsertDividendEvents(
            String ticker,
            List<EquityCorporateActionService.DividendEvent> detectedEvents,
            boolean degradedScan) {
        List<CorporateAction> existing = corporateActionRepository.findByTicker(ticker).stream()
                .filter(a -> a.getActionType() == ActionType.DIVIDEND)
                .sorted(Comparator
                        .comparing(CorporateAction::getEffectiveDate)
                        .thenComparing(CorporateAction::getRatio))
                .toList();
        List<EquityCorporateActionService.DividendEvent> sortedDetected = dedupeDividendEvents(ticker, detectedEvents);
        int changed = 0;
        int inserted = 0;
        int updated = 0;
        Set<CorporateAction> usedActions = Collections.newSetFromMap(new IdentityHashMap<>());
        List<CorporateAction> mutableExisting = new ArrayList<>(existing);
        for (EquityCorporateActionService.DividendEvent target : sortedDetected) {
            CorporateAction matched = findExactDividendMatch(mutableExisting, target, usedActions);
            if (matched == null) {
                matched = findYearScopedDividendMatch(mutableExisting, target, usedActions);
            }
            if (matched != null) {
                usedActions.add(matched);
                // A weaker-grounded re-detection (e.g. synthetic fallback after a transient filing
                // fetch failure) must not move a date that was anchored to an actual declaration.
                boolean allowDateMove = exDateSourceRank(target.exDateSource()) >= exDateSourceRank(matched.getExDateSource());
                boolean dateChanged = allowDateMove && !matched.getEffectiveDate().equals(target.effectiveDate());
                boolean ratioChanged = Math.abs(matched.getRatio() - target.adjustedAmount()) > DIVIDEND_EPSILON;
                boolean rawChanged = !roughlyEqual(matched.getRawDividend(), target.rawAmount());
                boolean adjustedChanged = !roughlyEqual(matched.getAdjustedDividend(), target.adjustedAmount());
                boolean provenanceChanged = allowDateMove && provenanceDiffers(matched, target);
                if (dateChanged || ratioChanged || rawChanged || adjustedChanged || provenanceChanged) {
                    if (allowDateMove) {
                        matched.setEffectiveDate(target.effectiveDate());
                        applyProvenance(matched, target);
                    }
                    matched.setRatio(target.adjustedAmount());
                    matched.setRawDividend(target.rawAmount());
                    matched.setAdjustedDividend(target.adjustedAmount());
                    corporateActionRepository.save(matched);
                    changed++;
                    updated++;
                }
                continue;
            }
            if (hasExactSignature(mutableExisting, target)) {
                log.debug("[{}] Skipping exact duplicate dividend event on {} raw={} adjusted={}",
                        ticker,
                        target.effectiveDate(),
                        String.format(Locale.US, "%.4f", target.rawAmount()),
                        String.format(Locale.US, "%.4f", target.adjustedAmount()));
                continue;
            }
            if (degradedScan && exDateSourceRank(target.exDateSource()) <= 1) {
                log.warn("[{}] Degraded filing scan: skipping synthetic-dated dividend insert on {} raw={}",
                        ticker, target.effectiveDate(), String.format(Locale.US, "%.4f", target.rawAmount()));
                continue;
            }
            List<CorporateAction> sameDateExisting = corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(
                    ticker, ActionType.DIVIDEND, target.effectiveDate());
            if (hasExactSignature(sameDateExisting, target)) {
                continue;
            }
            CorporateAction action = new CorporateAction();
            action.setTicker(ticker);
            action.setActionType(ActionType.DIVIDEND);
            action.setEffectiveDate(target.effectiveDate());
            action.setRatio(target.adjustedAmount());
            action.setRawDividend(target.rawAmount());
            action.setAdjustedDividend(target.adjustedAmount());
            action.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
            applyProvenance(action, target);
            CorporateAction saved;
            try {
                saved = corporateActionRepository.save(action);
            } catch (DataIntegrityViolationException e) {
                List<CorporateAction> refreshedSameDate = corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(
                        ticker, ActionType.DIVIDEND, target.effectiveDate());
                if (!hasExactSignature(refreshedSameDate, target)) {
                    log.warn("[{}] Skipping conflicting dividend insert on {} raw={} adjusted={} due to unique constraint: {}",
                            ticker,
                            target.effectiveDate(),
                            String.format(Locale.US, "%.4f", target.rawAmount()),
                            String.format(Locale.US, "%.4f", target.adjustedAmount()),
                            e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
                }
                continue;
            }
            mutableExisting.add(saved);
            usedActions.add(saved);
            changed++;
            inserted++;
        }
        int pruned = 0;
        if (!sortedDetected.isEmpty() && !degradedScan) {
            int minYear = sortedDetected.stream().mapToInt(EquityCorporateActionService.DividendEvent::year).min().orElse(Integer.MAX_VALUE);
            int maxYear = sortedDetected.stream().mapToInt(EquityCorporateActionService.DividendEvent::year).max().orElse(Integer.MIN_VALUE);
            for (CorporateAction action : existing) {
                if (usedActions.contains(action)) {
                    continue;
                }
                if (action.getSourceType() != CorporateAction.SourceType.SEC_EQUITY_XBRL) {
                    continue;
                }
                int year = action.getEffectiveDate().getYear();
                if (year < minYear || year > maxYear) {
                    continue;
                }
                if (exDateSourceRank(action.getExDateSource()) >= 3) {
                    // Deleting a declaration-anchored row and re-inserting a synthetic-dated one
                    // would bypass the rank guard above; anchored orphans need manual review.
                    log.warn("[{}] Not pruning declaration-anchored dividend record: {} raw={} source={}",
                            ticker, action.getEffectiveDate(), action.getRawDividend(), action.getExDateSource());
                    continue;
                }
                log.info("[{}] Pruning orphaned SEC dividend record: {} raw={}", ticker, action.getEffectiveDate(), action.getRawDividend());
                corporateActionRepository.delete(action);
                pruned++;
            }
        } else if (degradedScan && !sortedDetected.isEmpty()) {
            log.warn("[{}] Degraded filing scan: skipping orphan pruning this run", ticker);
        }
        if (pruned > 0) {
            changed += pruned;
        }
        return new EquityCorporateActionService.UpsertStats(changed, inserted, updated, pruned);
    }

    private List<EquityCorporateActionService.DividendEvent> dedupeDividendEvents(
            String ticker,
            List<EquityCorporateActionService.DividendEvent> events) {
        Map<String, EquityCorporateActionService.DividendEvent> deduped = new LinkedHashMap<>();
        Map<java.time.LocalDate, Integer> keptByDate = new HashMap<>();
        for (EquityCorporateActionService.DividendEvent event : events) {
            String key = event.effectiveDate() + "|"
                    + String.format(Locale.US, "%.4f", event.rawAmount()) + "|"
                    + String.format(Locale.US, "%.4f", event.adjustedAmount());
            if (deduped.containsKey(key)) {
                continue;
            }
            int sameDateCount = keptByDate.getOrDefault(event.effectiveDate(), 0);
            if (sameDateCount > 0) {
                log.info("[{}] Keeping additional same-date dividend event on {} raw={} adjusted={} (existing same-date events={})",
                        ticker,
                        event.effectiveDate(),
                        String.format(Locale.US, "%.4f", event.rawAmount()),
                        String.format(Locale.US, "%.4f", event.adjustedAmount()),
                        sameDateCount);
            }
            deduped.put(key, event);
            keptByDate.put(event.effectiveDate(), sameDateCount + 1);
        }
        return deduped.values().stream()
                .sorted(Comparator.comparing(EquityCorporateActionService.DividendEvent::effectiveDate).thenComparing(EquityCorporateActionService.DividendEvent::rawAmount))
                .toList();
    }

    private CorporateAction findExactDividendMatch(
            List<CorporateAction> existing,
            EquityCorporateActionService.DividendEvent target,
            Set<CorporateAction> usedActions) {
        for (CorporateAction action : existing) {
            if (usedActions.contains(action)) {
                continue;
            }
            if (!action.getEffectiveDate().equals(target.effectiveDate())) {
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

    private CorporateAction findYearScopedDividendMatch(
            List<CorporateAction> existing,
            EquityCorporateActionService.DividendEvent target,
            Set<CorporateAction> usedActions) {
        CorporateAction best = null;
        double bestScore = Double.MAX_VALUE;
        for (CorporateAction action : existing) {
            if (usedActions.contains(action)) {
                continue;
            }
            if (action.getEffectiveDate().getYear() != target.year()
                    && action.getEffectiveDate().getYear() != target.effectiveDate().getYear()) {
                continue;
            }
            long dayDistance = Math.abs(ChronoUnit.DAYS.between(action.getEffectiveDate(), target.effectiveDate()));
            if (dayDistance > 35) {
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

    private boolean hasExactSignature(List<CorporateAction> existing, EquityCorporateActionService.DividendEvent target) {
        for (CorporateAction action : existing) {
            if (action.getActionType() != ActionType.DIVIDEND) {
                continue;
            }
            if (!action.getEffectiveDate().equals(target.effectiveDate())) {
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

    private boolean roughlyEqual(Double left, double right) {
        if (left == null) {
            return false;
        }
        return Math.abs(left - right) <= DIVIDEND_EPSILON;
    }

    private void applyProvenance(CorporateAction action, EquityCorporateActionService.DividendEvent target) {
        action.setRecordDate(target.recordDate());
        action.setPayDate(target.payDate());
        action.setExDateSource(target.exDateSource());
        action.setConfidenceScore(confidenceForExDateSource(target.exDateSource()));
    }

    private boolean provenanceDiffers(CorporateAction action, EquityCorporateActionService.DividendEvent target) {
        return !java.util.Objects.equals(action.getExDateSource(), target.exDateSource())
                || !java.util.Objects.equals(action.getRecordDate(), target.recordDate())
                || !java.util.Objects.equals(action.getPayDate(), target.payDate());
    }

    /** Higher rank = better-grounded ex-date; only equal-or-better sources may move a stored date. */
    private static int exDateSourceRank(String exDateSource) {
        if (exDateSource == null) {
            return 0;
        }
        return switch (exDateSource) {
            case CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED -> 4;
            case CorporateAction.EX_DATE_SOURCE_DIRECT_EX_TEXT -> 3;
            case CorporateAction.EX_DATE_SOURCE_RECORD_DP -> 2;
            case CorporateAction.EX_DATE_SOURCE_SYNTHETIC -> 1;
            default -> 0;
        };
    }

    private static Double confidenceForExDateSource(String exDateSource) {
        if (exDateSource == null) {
            return null;
        }
        return switch (exDateSource) {
            case CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED -> 95.0;
            case CorporateAction.EX_DATE_SOURCE_DIRECT_EX_TEXT -> 90.0;
            case CorporateAction.EX_DATE_SOURCE_RECORD_DP -> 60.0;
            case CorporateAction.EX_DATE_SOURCE_SYNTHETIC -> 10.0;
            default -> null;
        };
    }
}

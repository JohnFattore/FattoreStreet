package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;

@Component
public class EquityDividendNormalizer {

    private static final int MAX_QUARTERLY_PERIOD_DAYS = 120;
    private static final int ANNUAL_PERIOD_MIN_DAYS = 250;
    private static final double DIVIDEND_EPSILON = 0.000001d;
    private static final double MAX_Q4_RELATIVE_JUMP = 2.5;

    public List<EquityCorporateActionService.DividendEvent> normalizeDividendFacts(
            List<EquityCorporateActionService.DividendFact> facts) {
        if (facts.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, List<EquityCorporateActionService.DividendFact>> byEndDate = facts.stream()
                .collect(Collectors.groupingBy(EquityCorporateActionService.DividendFact::endDate));

        Map<LocalDate, Double> quarterlyByEndDate = new HashMap<>();
        List<EquityCorporateActionService.DividendEvent> specialCandidates = new ArrayList<>();
        for (Map.Entry<LocalDate, List<EquityCorporateActionService.DividendFact>> entry : byEndDate.entrySet()) {
            LocalDate endDate = entry.getKey();
            List<EquityCorporateActionService.DividendFact> rows = entry.getValue();

            List<EquityCorporateActionService.DividendFact> quarterRows = rows.stream()
                    .filter(this::isQuarterLengthFact)
                    .toList();

            EquityCorporateActionService.DividendFact selected = null;
            if (!quarterRows.isEmpty()) {
                selected = quarterRows.stream().sorted(Comparator
                        .comparingInt(this::periodLengthDays)
                        .thenComparingInt((EquityCorporateActionService.DividendFact d) -> formPriority(d.form()))
                        .thenComparing((EquityCorporateActionService.DividendFact d) -> d.filedDate(), Comparator.nullsLast(Comparator.naturalOrder())))
                        .findFirst()
                        .orElse(quarterRows.get(0));
            } else {
                Set<Double> intermediateAmounts = rows.stream()
                        .filter(this::isIntermediateLengthFact)
                        .map(f -> round4(f.value()))
                        .collect(Collectors.toSet());
                List<EquityCorporateActionService.DividendFact> nonAnnualRows = rows.stream()
                        .filter(f -> !isAnnualLengthFact(f) && !isIntermediateLengthFact(f))
                        .filter(f -> f.startDate() != null
                                || intermediateAmounts.isEmpty()
                                || !intermediateAmounts.contains(round4(f.value())))
                        .toList();
                if (!nonAnnualRows.isEmpty()) {
                    selected = nonAnnualRows.stream()
                            .sorted(Comparator
                                    .comparingInt((EquityCorporateActionService.DividendFact d) -> d.startDate() == null ? 1 : 0)
                                    .thenComparingInt((EquityCorporateActionService.DividendFact d) -> preferredConceptPriority(d.concept()))
                                    .thenComparingInt((EquityCorporateActionService.DividendFact d) -> formPriority(d.form()))
                                    .thenComparing((EquityCorporateActionService.DividendFact d) -> d.filedDate(), Comparator.nullsLast(Comparator.naturalOrder())))
                            .findFirst()
                            .orElse(nonAnnualRows.get(0));
                }
            }

            if (selected != null) {
                double regularAmount = round4(selected.value());
                quarterlyByEndDate.put(endDate, regularAmount);
                for (EquityCorporateActionService.DividendFact row : rows) {
                    if (isAnnualLengthFact(row) || isIntermediateLengthFact(row)) {
                        continue;
                    }
                    double amount = round4(row.value());
                    if (Math.abs(amount - regularAmount) <= DIVIDEND_EPSILON) {
                        continue;
                    }
                    boolean muchLargerThanRegular = amount >= round4(regularAmount * 2.8) && (amount - regularAmount) >= 0.75;
                    boolean likelySpecialDisclosure = "8-K".equalsIgnoreCase(row.form()) && amount > regularAmount + 0.25;
                    if (muchLargerThanRegular || likelySpecialDisclosure) {
                        specialCandidates.add(new EquityCorporateActionService.DividendEvent(endDate, null, amount, amount, endDate.getYear(), true));
                    }
                }
            }
        }

        List<EquityCorporateActionService.DividendFact> annualFacts = facts.stream()
                .filter(this::isAnnualLengthFact)
                .sorted(Comparator
                        .comparing(EquityCorporateActionService.DividendFact::endDate)
                        .thenComparing((EquityCorporateActionService.DividendFact d) -> d.filedDate(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<LocalDate, List<EquityCorporateActionService.DividendFact>> annualsByEndDate = new LinkedHashMap<>();
        for (EquityCorporateActionService.DividendFact annual : annualFacts) {
            annualsByEndDate.computeIfAbsent(annual.endDate(), k -> new ArrayList<>()).add(annual);
        }

        for (Map.Entry<LocalDate, List<EquityCorporateActionService.DividendFact>> annualEntry : annualsByEndDate.entrySet()) {
            LocalDate fyEnd = annualEntry.getKey();
            for (EquityCorporateActionService.DividendFact annual : annualEntry.getValue()) {
                if (annual.startDate() == null) {
                    continue;
                }
                LocalDate fyStart = annual.startDate();

                List<LocalDate> priorQuarterEnds = quarterlyByEndDate.keySet().stream()
                        .filter(d -> d.isAfter(fyStart.minusDays(1)) && d.isBefore(fyEnd))
                        .sorted()
                        .toList();
                double sumPrior = priorQuarterEnds.stream().mapToDouble(d -> quarterlyByEndDate.getOrDefault(d, 0.0)).sum();
                double derivedQ4 = round4(annual.value() - sumPrior);
                if (derivedQ4 <= DIVIDEND_EPSILON) {
                    continue;
                }
                if (!isPlausibleDerivedQuarter(derivedQ4, priorQuarterEnds, quarterlyByEndDate)) {
                    continue;
                }

                Double existingAtFyEnd = quarterlyByEndDate.get(fyEnd);
                if (existingAtFyEnd == null) {
                    quarterlyByEndDate.put(fyEnd, derivedQ4);
                } else {
                    boolean looksCumulative = existingAtFyEnd > derivedQ4 + 0.02;
                    if (looksCumulative) {
                        quarterlyByEndDate.put(fyEnd, derivedQ4);
                    }
                }
                break;
            }
        }

        List<EquityCorporateActionService.DividendEvent> regularEvents = quarterlyByEndDate.entrySet().stream()
                .filter(e -> e.getValue() > DIVIDEND_EPSILON)
                .map(e -> new EquityCorporateActionService.DividendEvent(e.getKey(), null, round4(e.getValue()), round4(e.getValue()), e.getKey().getYear(), false))
                .sorted(Comparator.comparing(EquityCorporateActionService.DividendEvent::fiscalPeriodEnd))
                .toList();
        if (specialCandidates.isEmpty()) {
            return regularEvents;
        }
        Set<String> regularKeys = regularEvents.stream()
                .map(e -> e.fiscalPeriodEnd() + "|" + String.format(Locale.US, "%.4f", e.rawAmount()))
                .collect(Collectors.toSet());
        Map<String, EquityCorporateActionService.DividendEvent> dedupedSpecials = new LinkedHashMap<>();
        for (EquityCorporateActionService.DividendEvent special : specialCandidates) {
            String key = special.fiscalPeriodEnd() + "|" + String.format(Locale.US, "%.4f", special.rawAmount());
            if (!regularKeys.contains(key)) {
                dedupedSpecials.putIfAbsent(key, special);
            }
        }
        List<EquityCorporateActionService.DividendEvent> combined = new ArrayList<>(regularEvents);
        combined.addAll(dedupedSpecials.values());
        combined.sort(Comparator
                .comparing(EquityCorporateActionService.DividendEvent::fiscalPeriodEnd)
                .thenComparingDouble(EquityCorporateActionService.DividendEvent::rawAmount));
        return combined;
    }

    public List<EquityCorporateActionService.DividendEvent> adjustDividendsForFutureSplits(
            List<EquityCorporateActionService.DividendEvent> events,
            List<CorporateAction> actions) {
        List<CorporateAction> splits = actions.stream()
                .filter(a -> a.getActionType() == ActionType.SPLIT)
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .toList();
        if (splits.isEmpty()) {
            return events;
        }
        List<CorporateAction> snappedSplits = splits.stream()
                .map(this::snapSplitAction)
                .toList();
        Map<LocalDate, LocalDate> alreadyAdjustedCutoffs = inferAlreadyAdjustedPreSplitCutoffs(events, snappedSplits);
        List<EquityCorporateActionService.DividendEvent> adjusted = new ArrayList<>(events.size());
        for (EquityCorporateActionService.DividendEvent event : events) {
            LocalDate splitAnchor = event.exDividendDate() != null ? event.exDividendDate() : event.fiscalPeriodEnd();
            double factor = 1.0;
            // Product of split price-ratios the issuer already baked into the reported amount
            // (restated facts). rawAmount must be un-restated by this so rawDividend stays on the
            // raw price scale that PriceAdjustmentService divides by at the ex-date.
            double restatedFactor = 1.0;
            for (CorporateAction split : snappedSplits) {
                if (split.getEffectiveDate().isAfter(splitAnchor)) {
                    LocalDate cutoff = alreadyAdjustedCutoffs.get(split.getEffectiveDate());
                    if (cutoff != null && !splitAnchor.isBefore(cutoff)) {
                        restatedFactor *= split.getRatio();
                        continue;
                    }
                    factor *= split.getRatio();
                }
            }
            double rawOnPriceScale = restatedFactor != 1.0
                    ? round4(event.rawAmount() / restatedFactor)
                    : event.rawAmount();
            adjusted.add(new EquityCorporateActionService.DividendEvent(
                    event.fiscalPeriodEnd(),
                    event.exDividendDate(),
                    rawOnPriceScale,
                    round4(event.rawAmount() * factor),
                    event.year(),
                    event.specialEvent(),
                    event.recordDate(),
                    event.payDate(),
                    event.exDateSource()));
        }
        return adjusted;
    }

    private Map<LocalDate, LocalDate> inferAlreadyAdjustedPreSplitCutoffs(
            List<EquityCorporateActionService.DividendEvent> events,
            List<CorporateAction> snappedSplits) {
        Map<LocalDate, LocalDate> cutoffs = new HashMap<>();
        List<EquityCorporateActionService.DividendEvent> sortedEvents = events.stream()
                .sorted(Comparator.comparing(e -> e.exDividendDate() != null ? e.exDividendDate() : e.fiscalPeriodEnd()))
                .toList();
        for (CorporateAction split : snappedSplits) {
            LocalDate splitDate = split.getEffectiveDate();
            EquityCorporateActionService.DividendEvent firstPostSplit = sortedEvents.stream()
                    .filter(e -> {
                        LocalDate anchor = e.exDividendDate() != null ? e.exDividendDate() : e.fiscalPeriodEnd();
                        return !anchor.isBefore(splitDate);
                    })
                    .findFirst()
                    .orElse(null);
            if (firstPostSplit == null) {
                continue;
            }
            List<EquityCorporateActionService.DividendEvent> preSplit = sortedEvents.stream()
                    .filter(e -> {
                        LocalDate anchor = e.exDividendDate() != null ? e.exDividendDate() : e.fiscalPeriodEnd();
                        return anchor.isBefore(splitDate);
                    })
                    .sorted(Comparator.comparing((EquityCorporateActionService.DividendEvent e) ->
                            e.exDividendDate() != null ? e.exDividendDate() : e.fiscalPeriodEnd()).reversed())
                    .toList();
            LocalDate cutoff = null;
            double anchor = firstPostSplit.rawAmount();
            for (EquityCorporateActionService.DividendEvent event : preSplit) {
                LocalDate eventAnchor = event.exDividendDate() != null ? event.exDividendDate() : event.fiscalPeriodEnd();
                if (roughlySameScale(event.rawAmount(), anchor, 0.30)) {
                    cutoff = eventAnchor;
                    anchor = (anchor + event.rawAmount()) / 2.0;
                    continue;
                }
                if (cutoff != null && event.rawAmount() >= anchor * 2.5) {
                    continue;
                }
                if (cutoff != null) {
                    break;
                }
            }
            if (cutoff != null) {
                cutoffs.put(splitDate, cutoff);
            }
        }
        return cutoffs;
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
        out.setRatio(1.0 / snappedRaw);
        return out;
    }

    private boolean roughlySameScale(double left, double right, double tolerance) {
        if (left <= 0 || right <= 0) {
            return false;
        }
        double rel = Math.abs(left - right) / Math.max(Math.abs(right), DIVIDEND_EPSILON);
        return rel <= tolerance;
    }

    private boolean isQuarterLengthFact(EquityCorporateActionService.DividendFact fact) {
        if (fact.startDate() == null) {
            return false;
        }
        int days = periodLengthDays(fact);
        return days > 0 && days <= MAX_QUARTERLY_PERIOD_DAYS;
    }

    private boolean isAnnualLengthFact(EquityCorporateActionService.DividendFact fact) {
        if (fact.startDate() == null) {
            return false;
        }
        int days = periodLengthDays(fact);
        return days >= ANNUAL_PERIOD_MIN_DAYS;
    }

    private boolean isIntermediateLengthFact(EquityCorporateActionService.DividendFact fact) {
        if (fact.startDate() == null) {
            return false;
        }
        int days = periodLengthDays(fact);
        return days > MAX_QUARTERLY_PERIOD_DAYS && days < ANNUAL_PERIOD_MIN_DAYS;
    }

    private int formPriority(String form) {
        if ("10-Q".equalsIgnoreCase(form)) {
            return 0;
        }
        if ("8-K".equalsIgnoreCase(form)) {
            return 1;
        }
        if ("10-K".equalsIgnoreCase(form)) {
            return 2;
        }
        return 3;
    }

    private int preferredConceptPriority(String concept) {
        return "CommonStockDividendsPerShareDeclared".equals(concept)
                || "CommonStockDividendsPerShareCashPaid".equals(concept)
                || "CommonStockDividendsPerShareDeclaredAndPaid".equals(concept)
                || "DividendsPaidPerShare".equals(concept) ? 0 : 1;
    }

    private int periodLengthDays(EquityCorporateActionService.DividendFact fact) {
        if (fact.startDate() == null || fact.endDate() == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(fact.startDate(), fact.endDate());
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

    private boolean isCommonSplitRatio(double rawRatio) {
        for (double c : splitSnapRatios()) {
            if (Math.abs(rawRatio - c) / c < 0.02) {
                return true;
            }
        }
        return false;
    }

    private double nearestCommonSplitRatio(double rawRatio) {
        Set<Double> common = splitSnapRatios();
        double best = rawRatio;
        double bestRelErr = Double.MAX_VALUE;
        for (double c : common) {
            double relErr = Math.abs(rawRatio - c) / c;
            if (relErr < bestRelErr) {
                bestRelErr = relErr;
                best = c;
            }
        }
        return bestRelErr < 0.02 ? best : rawRatio;
    }

    /** Matches {@link com.fattorestreet.sec_api.corporateaction.support.EquitySplitDetector} primary+extended ratios for snapping. */
    private static Set<Double> splitSnapRatios() {
        return Set.of(
                2.0, 3.0, 4.0, 5.0, 7.0, 10.0, 20.0, 50.0,
                1.5, 4.0 / 3.0,
                0.5, 1.0 / 3, 0.25, 0.2, 1.0 / 7, 0.1, 0.05);
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}

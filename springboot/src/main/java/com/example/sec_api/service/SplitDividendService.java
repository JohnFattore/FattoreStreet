package com.example.sec_api.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.sec_api.model.CorporateAction;
import com.example.sec_api.model.CorporateAction.ActionType;
import com.example.sec_api.repository.CorporateActionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SplitDividendService {

    private static final Logger log = LoggerFactory.getLogger(SplitDividendService.class);
    private static final Set<Double> COMMON_SPLIT_RATIOS = Set.of(
            2.0, 3.0, 4.0, 5.0, 7.0, 10.0, 20.0, 50.0,
            0.5, 1.0 / 3, 0.25, 0.2, 1.0 / 7, 0.1, 0.05
    );
    private static final double RATIO_TOLERANCE = 0.02;

    private final WebService webService;
    private final DividendRecordDateService dividendRecordDateService;
    private final CorporateActionRepository corporateActionRepository;
    private final ObjectMapper mapper;

    public SplitDividendService(WebService webService,
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
        JsonNode root;
        try {
            String json = webService.fetchFinancials(cik);
            root = mapper.readTree(json);
        } catch (Exception e) {
            log.warn("[{}] Failed to fetch SEC facts for CIK {}: {}", ticker, cik, e.getMessage());
            return 0;
        }

        int created = 0;
        created += detectSplits(ticker, root);
        created += detectDividends(ticker, cik, root);
        return created;
    }

    private int detectSplits(String ticker, JsonNode root) {
        JsonNode sharesNode = navigatePath(root,
                "facts", "dei", "EntityCommonStockSharesOutstanding", "units", "shares");
        if (sharesNode == null || !sharesNode.isArray()) return 0;

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

        int created = 0;
        for (int i = 1; i < entries.size(); i++) {
            SharesEntry prev = entries.get(i - 1);
            SharesEntry curr = entries.get(i);
            double rawRatio = (double) curr.shares / prev.shares;

            if (isCommonSplitRatio(rawRatio)) {
                double snappedRaw = nearestCommonSplitRatio(rawRatio);
                double splitRatio = 1.0 / snappedRaw;
                LocalDate effectiveDate = curr.date;

                if (!corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                        ticker, ActionType.SPLIT, effectiveDate)) {
                    CorporateAction action = new CorporateAction();
                    action.setTicker(ticker);
                    action.setActionType(ActionType.SPLIT);
                    action.setEffectiveDate(effectiveDate);
                    action.setRatio(splitRatio);
                    corporateActionRepository.save(action);
                    created++;
                    log.info("[{}] Detected split on {}: ratio {} (shares {} -> {})",
                            ticker, effectiveDate, String.format("%.4f", splitRatio),
                            prev.shares, curr.shares);
                }
            }
        }
        return created;
    }

    private static final int MAX_QUARTERLY_PERIOD_DAYS = 120;
    private static final int MIN_RECORD_DATE_OFFSET_DAYS = 3;
    private static final int MAX_RECORD_DATE_OFFSET_DAYS = 120;
    private static final int ANNUAL_PERIOD_MIN_DAYS = 250;
    private static final double DIVIDEND_EPSILON = 0.000001d;

    private int detectDividends(String ticker, Long cik, JsonNode root) {
        JsonNode divNode = navigatePath(root,
                "facts", "us-gaap", "CommonStockDividendsPerShareDeclared", "units", "USD/shares");
        if (divNode == null || !divNode.isArray()) return 0;

        List<DividendFact> facts = parseDividendFacts(divNode);
        List<DividendEvent> normalized = normalizeDividendFacts(facts);
        if (normalized.isEmpty()) return 0;

        List<DividendRecordDateService.RecordDateCandidate> recordDates = dividendRecordDateService.fetchDividendRecordDates(cik);
        List<DividendEvent> detectedEvents = assignExDividendDates(normalized, recordDates);
        List<CorporateAction> splitActions = corporateActionRepository.findByTicker(ticker).stream()
                .filter(a -> a.getActionType() == ActionType.SPLIT)
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .collect(Collectors.toList());
        List<DividendEvent> splitAdjustedEvents = adjustDividendsForFutureSplits(detectedEvents, splitActions);

        int created = upsertDividendEvents(ticker, splitAdjustedEvents);
        if (created > 0) {
            log.info("[{}] Detected/Reconciled {} dividend entries", ticker, created);
        }
        return created;
    }

    private List<DividendFact> parseDividendFacts(JsonNode divNode) {
        List<DividendFact> allFacts = new ArrayList<>();
        for (JsonNode entry : divNode) {
            String form = entry.has("form") ? entry.get("form").asText() : "";
            if (!form.equals("10-K") && !form.equals("10-Q") && !form.equals("8-K")) continue;
            if (!entry.has("val") || !entry.has("end")) continue;

            LocalDate endDate = LocalDate.parse(entry.get("end").asText());
            LocalDate startDate = null;
            if (entry.has("start")) {
                startDate = LocalDate.parse(entry.get("start").asText());
            }

            double amount = entry.get("val").asDouble();
            if (amount <= 0) continue;

            LocalDate filedDate = null;
            if (entry.has("filed")) {
                filedDate = LocalDate.parse(entry.get("filed").asText());
            }

            allFacts.add(new DividendFact(startDate, endDate, amount, form, filedDate));
        }
        return allFacts;
    }

    private List<DividendEvent> normalizeDividendFacts(List<DividendFact> facts) {
        Map<LocalDate, List<DividendFact>> byEndDate = facts.stream()
                .collect(Collectors.groupingBy(DividendFact::endDate));

        Map<LocalDate, Double> quarterlyByEndDate = new HashMap<>();
        for (Map.Entry<LocalDate, List<DividendFact>> entry : byEndDate.entrySet()) {
            LocalDate endDate = entry.getKey();
            List<DividendFact> rows = entry.getValue();

            List<DividendFact> quarterRows = rows.stream()
                    .filter(this::isQuarterLengthFact)
                    .collect(Collectors.toList());

            DividendFact selected;
            if (!quarterRows.isEmpty()) {
                // Prefer shortest quarter-like window, then latest filing.
                selected = quarterRows.stream().sorted(Comparator
                        .comparingInt(this::periodLengthDays)
                        .thenComparingInt((DividendFact d) -> formPriority(d.form))
                        .thenComparing((DividendFact d) -> d.filedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                        .findFirst()
                        .orElse(quarterRows.get(0));
            } else {
                // Fallback when only cumulative rows exist for a period end.
                selected = rows.stream()
                        .sorted(Comparator
                                .comparingInt((DividendFact d) -> formPriority(d.form))
                                .thenComparing((DividendFact d) -> d.filedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                        .findFirst()
                        .orElse(rows.get(0));
            }

            quarterlyByEndDate.put(endDate, round4(selected.value));
        }

        // Derive missing or obviously cumulative fiscal-Q4 using annual cumulative 10-K facts.
        List<DividendFact> annualFacts = facts.stream()
                .filter(this::isAnnualLengthFact)
                .sorted(Comparator
                        .comparing(DividendFact::endDate)
                        .thenComparing((DividendFact d) -> d.filedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        for (DividendFact annual : annualFacts) {
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

        return quarterlyByEndDate.entrySet().stream()
                .filter(e -> e.getValue() > DIVIDEND_EPSILON)
                .map(e -> new DividendEvent(e.getKey(), round4(e.getValue()), e.getKey().getYear()))
                .sorted(Comparator.comparing(DividendEvent::periodEnd))
                .collect(Collectors.toList());
    }

    private List<DividendEvent> assignExDividendDates(List<DividendEvent> normalized, List<DividendRecordDateService.RecordDateCandidate> recordDateCandidates) {
        List<DividendEvent> mapped = new ArrayList<>();
        Set<String> usedRecordKeys = new HashSet<>();
        List<DividendRecordDateService.RecordDateCandidate> sortedCandidates = new ArrayList<>(recordDateCandidates);
        sortedCandidates.sort(Comparator
                .comparing(DividendRecordDateService.RecordDateCandidate::recordDate)
                .thenComparing(DividendRecordDateService.RecordDateCandidate::filingDate));
        LocalDate lastMatchedRecordDate = null;

        for (DividendEvent event : normalized) {
            DividendRecordDateService.RecordDateCandidate matchedCandidate = null;
            long bestScore = Long.MAX_VALUE;
            for (DividendRecordDateService.RecordDateCandidate candidate : sortedCandidates) {
                String key = candidate.accessionNumber() + "|" + candidate.recordDate();
                if (usedRecordKeys.contains(key)) {
                    continue;
                }
                if (lastMatchedRecordDate != null && candidate.recordDate().isBefore(lastMatchedRecordDate)) {
                    continue;
                }
                long dayOffset = ChronoUnit.DAYS.between(event.periodEnd, candidate.recordDate());
                if (dayOffset < MIN_RECORD_DATE_OFFSET_DAYS || dayOffset > MAX_RECORD_DATE_OFFSET_DAYS) {
                    continue;
                }
                if (candidate.filingDate() != null && candidate.filingDate().isBefore(event.periodEnd.minusDays(5))) {
                    continue;
                }
                long score = Math.abs(dayOffset - 42);
                if (matchedCandidate == null || score < bestScore
                        || (score == bestScore && candidate.recordDate().isBefore(matchedCandidate.recordDate()))) {
                    matchedCandidate = candidate;
                    bestScore = score;
                }
            }

            LocalDate effectiveDate;
            if (matchedCandidate != null) {
                String key = matchedCandidate.accessionNumber() + "|" + matchedCandidate.recordDate();
                usedRecordKeys.add(key);
                lastMatchedRecordDate = matchedCandidate.recordDate();
                effectiveDate = dividendRecordDateService.computeExDividendDate(matchedCandidate.recordDate());
            } else {
                // Fallback keeps ingestion robust when a record date cannot be parsed from 8-K text.
                effectiveDate = event.periodEnd;
            }

            mapped.add(new DividendEvent(effectiveDate, event.amount, event.year));
        }
        return mapped;
    }

    private int upsertDividendEvents(String ticker, List<DividendEvent> detectedEvents) {
        List<CorporateAction> existing = corporateActionRepository.findByTicker(ticker).stream()
                .filter(a -> a.getActionType() == ActionType.DIVIDEND)
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .collect(Collectors.toList());

        Map<Integer, List<CorporateAction>> existingByYear = existing.stream()
                .collect(Collectors.groupingBy(a -> a.getEffectiveDate().getYear(), TreeMap::new, Collectors.toList()));

        Map<Integer, List<DividendEvent>> detectedByYear = detectedEvents.stream()
                .collect(Collectors.groupingBy(DividendEvent::year, TreeMap::new, Collectors.toList()));

        int changed = 0;
        for (Map.Entry<Integer, List<DividendEvent>> entry : detectedByYear.entrySet()) {
            int year = entry.getKey();
            List<DividendEvent> targetYear = entry.getValue();
            targetYear.sort(Comparator.comparing(DividendEvent::periodEnd));

            List<CorporateAction> currentYear = existingByYear.getOrDefault(year, new ArrayList<>());
            currentYear.sort(Comparator.comparing(CorporateAction::getEffectiveDate));

            int overlap = Math.min(currentYear.size(), targetYear.size());
            for (int i = 0; i < overlap; i++) {
                CorporateAction existingAction = currentYear.get(i);
                DividendEvent target = targetYear.get(i);
                if (!existingAction.getEffectiveDate().equals(target.periodEnd)) {
                    Optional<CorporateAction> conflicting = corporateActionRepository
                            .findByTickerAndActionTypeAndEffectiveDate(ticker, ActionType.DIVIDEND, target.periodEnd);
                    if (conflicting == null) {
                        conflicting = Optional.empty();
                    }
                    if (conflicting.isPresent() && !Objects.equals(conflicting.get().getId(), existingAction.getId())) {
                        CorporateAction existingAtTargetDate = conflicting.get();
                        if (Math.abs(existingAtTargetDate.getRatio() - target.amount) > DIVIDEND_EPSILON) {
                            existingAtTargetDate.setRatio(target.amount);
                            corporateActionRepository.save(existingAtTargetDate);
                            changed++;
                        }
                        continue;
                    }
                }
                boolean dateChanged = !existingAction.getEffectiveDate().equals(target.periodEnd);
                boolean ratioChanged = Math.abs(existingAction.getRatio() - target.amount) > DIVIDEND_EPSILON;
                if (dateChanged || ratioChanged) {
                    existingAction.setEffectiveDate(target.periodEnd);
                    existingAction.setRatio(target.amount);
                    corporateActionRepository.save(existingAction);
                    changed++;
                }
            }

            for (int i = overlap; i < targetYear.size(); i++) {
                DividendEvent target = targetYear.get(i);
                if (!corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                        ticker, ActionType.DIVIDEND, target.periodEnd)) {
                    CorporateAction action = new CorporateAction();
                    action.setTicker(ticker);
                    action.setActionType(ActionType.DIVIDEND);
                    action.setEffectiveDate(target.periodEnd);
                    action.setRatio(target.amount);
                    corporateActionRepository.save(action);
                    changed++;
                }
            }
        }
        return changed;
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
            adjusted.add(new DividendEvent(event.periodEnd, round4(event.amount * factor), event.year));
        }
        return adjusted;
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
        out.setRatio(round4(1.0 / snappedRaw));
        return out;
    }

    private boolean isLater(LocalDate candidate, LocalDate baseline) {
        if (candidate == null) return false;
        if (baseline == null) return true;
        return candidate.isAfter(baseline);
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

    private record SharesEntry(LocalDate date, long shares) {}
    private record DividendFact(LocalDate startDate, LocalDate endDate, double value, String form, LocalDate filedDate) {}
    private record DividendEvent(LocalDate periodEnd, double amount, int year) {}
}

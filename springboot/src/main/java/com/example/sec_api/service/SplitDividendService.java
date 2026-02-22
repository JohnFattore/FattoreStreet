package com.example.sec_api.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
            2.0, 3.0, 4.0, 5.0, 10.0, 20.0, 50.0,
            0.5, 1.0 / 3, 0.25, 0.1, 0.05
    );
    private static final double RATIO_TOLERANCE = 0.02;

    private final WebService webService;
    private final CorporateActionRepository corporateActionRepository;
    private final ObjectMapper mapper;

    public SplitDividendService(WebService webService,
                                CorporateActionRepository corporateActionRepository,
                                ObjectMapper mapper) {
        this.webService = webService;
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
        created += detectDividends(ticker, root);
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
                double splitRatio = (double) prev.shares / curr.shares;
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

    private static final int MAX_QUARTERLY_PERIOD_DAYS = 100;

    private int detectDividends(String ticker, JsonNode root) {
        JsonNode divNode = navigatePath(root,
                "facts", "us-gaap", "CommonStockDividendsPerShareDeclared", "units", "USD/shares");
        if (divNode == null || !divNode.isArray()) return 0;

        int created = 0;
        for (JsonNode entry : divNode) {
            String form = entry.has("form") ? entry.get("form").asText() : "";
            if (!form.equals("10-K") && !form.equals("10-Q")) continue;
            if (!entry.has("val") || !entry.has("end")) continue;

            if (form.equals("10-K")) {
                if (!entry.has("start")) continue;
                LocalDate start = LocalDate.parse(entry.get("start").asText());
                LocalDate end = LocalDate.parse(entry.get("end").asText());
                if (ChronoUnit.DAYS.between(start, end) > MAX_QUARTERLY_PERIOD_DAYS) continue;
            }

            double amount = entry.get("val").asDouble();
            if (amount <= 0) continue;

            LocalDate endDate = LocalDate.parse(entry.get("end").asText());

            if (!corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                    ticker, ActionType.DIVIDEND, endDate)) {
                CorporateAction action = new CorporateAction();
                action.setTicker(ticker);
                action.setActionType(ActionType.DIVIDEND);
                action.setEffectiveDate(endDate);
                action.setRatio(amount);
                corporateActionRepository.save(action);
                created++;
            }
        }

        if (created > 0) {
            log.info("[{}] Detected {} new dividend entries", ticker, created);
        }
        return created;
    }

    private boolean isCommonSplitRatio(double rawRatio) {
        for (double common : COMMON_SPLIT_RATIOS) {
            if (Math.abs(rawRatio - common) / common < RATIO_TOLERANCE) return true;
        }
        return false;
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

    private record SharesEntry(LocalDate date, long shares) {}
}

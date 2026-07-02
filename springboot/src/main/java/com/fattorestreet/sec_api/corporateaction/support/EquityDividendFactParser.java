package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Component
public class EquityDividendFactParser {

    private static final Logger log = LoggerFactory.getLogger(EquityDividendFactParser.class);
    private static final Set<String> PREFERRED_DIVIDEND_CONCEPTS = Set.of(
            "CommonStockDividendsPerShareDeclared",
            "CommonStockDividendsPerShareCashPaid",
            "CommonStockDividendsPerShareDeclaredAndPaid",
            "DividendsPaidPerShare"
    );

    public List<EquityCorporateActionService.DividendFact> parseDividendFacts(JsonNode root, String ticker) {
        JsonNode usGaapNode = navigatePath(root, "facts", "us-gaap");
        if (usGaapNode == null || !usGaapNode.isObject()) {
            return Collections.emptyList();
        }

        List<EquityCorporateActionService.DividendFact> allFacts = new ArrayList<>();
        Set<String> consumedConcepts = new TreeSet<>();
        Iterator<Map.Entry<String, JsonNode>> fields = usGaapNode.properties().iterator();
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
            Iterator<Map.Entry<String, JsonNode>> unitFields = units.properties().iterator();
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
                    EquityCorporateActionService.DividendFact parsed = parseDividendFactRow(factRow, conceptName);
                    if (parsed != null) {
                        allFacts.add(parsed);
                    }
                }
            }
        }

        if (allFacts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, EquityCorporateActionService.DividendFact> deduped = new LinkedHashMap<>();
        for (EquityCorporateActionService.DividendFact fact : allFacts) {
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

    private EquityCorporateActionService.DividendFact parseDividendFactRow(JsonNode entry, String conceptName) {
        String form = entry.has("form") ? entry.get("form").asText() : "";
        if (!isRelevantDividendForm(form)) {
            return null;
        }
        if (!entry.has("val") || !entry.has("end")) {
            return null;
        }
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
        if (amount <= 0) {
            return null;
        }
        LocalDate filedDate = null;
        if (entry.has("filed")) {
            try {
                filedDate = LocalDate.parse(entry.get("filed").asText());
            } catch (Exception ignored) {
                filedDate = null;
            }
        }
        return new EquityCorporateActionService.DividendFact(startDate, endDate, amount, form, filedDate, conceptName);
    }

    private boolean isRelevantDividendForm(String form) {
        if (form == null || form.isBlank()) {
            return false;
        }
        String normalized = form.trim().toUpperCase(Locale.US);
        if (normalized.equals("10-Q")
                || normalized.equals("10-Q/A")
                || normalized.equals("10-K")
                || normalized.equals("10-K/A")
                || normalized.equals("8-K")
                || normalized.equals("8-K/A")) {
            return true;
        }
        if (normalized.equals("DEF 14A")
                || normalized.equals("DEFA14A")
                || normalized.equals("6-K")
                || normalized.equals("20-F")
                || normalized.equals("40-F")) {
            return true;
        }
        return normalized.endsWith("/A");
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

    private JsonNode navigatePath(JsonNode node, String... path) {
        for (String key : path) {
            if (node == null) {
                return null;
            }
            node = node.get(key);
        }
        return node;
    }
}

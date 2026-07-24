package com.fattorestreet.sec_api.fundamentals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Quarter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EdgarService {

    private static final Logger log = LoggerFactory.getLogger(EdgarService.class);

    private final WebService webService;
    private final QuarterService quarterService;
    private final com.fattorestreet.sec_api.repository.AssetRepository assetRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, List<String>> FIELD_TO_TAGS = Map.ofEntries(
            Map.entry("revenues",
                    List.of("RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "SalesRevenueNet",
                            "SalesRevenueGoodsNet", "SalesRevenueServicesNet")),
            Map.entry("netIncomeLoss",
                    List.of("NetIncomeLoss", "NetIncomeLossAvailableToCommonStockholdersBasic", "NetIncomeLossNet",
                            "ProfitLoss")),
            Map.entry("operatingIncomeLoss", List.of("OperatingIncomeLoss")),
            Map.entry("grossProfit", List.of("GrossProfit")),
            Map.entry("earningsPerShareBasic", List.of("EarningsPerShareBasic")),
            Map.entry("earningsPerShareDiluted", List.of("EarningsPerShareDiluted")),
            Map.entry("assets", List.of("Assets")),
            Map.entry("liabilities", List.of("Liabilities")),
            Map.entry("stockholdersEquity", List.of("StockholdersEquity", "CommonStockholdersEquity")),
            Map.entry("cashAndCashEquivalentsAtCarryingValue",
                    List.of("CashAndCashEquivalentsAtCarryingValue", "CashAndCashEquivalents")),
            Map.entry("accountsReceivableNetCurrent", List.of("AccountsReceivableNetCurrent")),
            Map.entry("inventoryNet", List.of("InventoryNet", "InventoryFinishedGoods")),
            Map.entry("netCashProvidedByUsedInOperatingActivities",
                    List.of("NetCashProvidedByUsedInOperatingActivities",
                            "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations")),
            Map.entry("paymentsOfDividends",
                    List.of("PaymentsOfDividends", "PaymentsOfDividendsCommonStock",
                            "PaymentsOfDividendsMinorityInterest")),
            Map.entry("paymentsForRepurchaseOfCommonStock", List.of("PaymentsForRepurchaseOfCommonStock")));

    private static final Set<String> STOCK_FIELDS = Set.of("assets", "liabilities", "stockholdersEquity",
            "cashAndCashEquivalentsAtCarryingValue", "accountsReceivableNetCurrent", "inventoryNet");

    private static class FrameConcept {
        String taxonomy;
        List<String> tags;
        String unit;
        String fieldName;
        boolean isInstant;

        FrameConcept(String taxonomy, List<String> tags, String unit, String fieldName, boolean isInstant) {
            this.taxonomy = taxonomy;
            this.tags = tags;
            this.unit = unit;
            this.fieldName = fieldName;
            this.isInstant = isInstant;
        }
    }

    private static final List<FrameConcept> FRAME_CONCEPTS = List.of(
            new FrameConcept("us-gaap",
                    List.of("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax",
                            "SalesRevenueNet", "SalesRevenueGoodsNet", "SalesRevenueServicesNet"),
                    "USD", "revenues", false),
            new FrameConcept("us-gaap",
                    List.of("NetIncomeLoss", "NetIncomeLossAvailableToCommonStockholdersBasic",
                            "NetIncomeLossNet", "ProfitLoss"),
                    "USD", "netIncomeLoss", false),
            new FrameConcept("us-gaap", List.of("OperatingIncomeLoss"), "USD", "operatingIncomeLoss", false),
            new FrameConcept("us-gaap", List.of("GrossProfit"), "USD", "grossProfit", false),
            new FrameConcept("us-gaap", List.of("EarningsPerShareBasic"), "USD-per-shares", "earningsPerShareBasic",
                    false),
            new FrameConcept("us-gaap", List.of("EarningsPerShareDiluted"), "USD-per-shares",
                    "earningsPerShareDiluted", false),
            new FrameConcept("us-gaap", List.of("Assets"), "USD", "assets", true),
            new FrameConcept("us-gaap", List.of("Liabilities"), "USD", "liabilities", true),
            new FrameConcept("us-gaap", List.of("StockholdersEquity", "CommonStockholdersEquity"), "USD",
                    "stockholdersEquity", true),
            new FrameConcept("us-gaap",
                    List.of("CashAndCashEquivalentsAtCarryingValue", "CashAndCashEquivalents"),
                    "USD", "cashAndCashEquivalentsAtCarryingValue", true),
            new FrameConcept("us-gaap", List.of("AccountsReceivableNetCurrent"), "USD",
                    "accountsReceivableNetCurrent", true),
            new FrameConcept("us-gaap", List.of("InventoryNet", "InventoryFinishedGoods"), "USD", "inventoryNet",
                    true),
            new FrameConcept("us-gaap",
                    List.of("NetCashProvidedByUsedInOperatingActivities",
                            "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"),
                    "USD", "netCashProvidedByUsedInOperatingActivities", false),
            new FrameConcept("us-gaap",
                    List.of("PaymentsOfDividends", "PaymentsOfDividendsCommonStock",
                            "PaymentsOfDividendsMinorityInterest"),
                    "USD", "paymentsOfDividends", false),
            new FrameConcept("us-gaap", List.of("PaymentsForRepurchaseOfCommonStock"), "USD",
                    "paymentsForRepurchaseOfCommonStock", false));

    private static final class AnnualData {
        LocalDate periodStart;
        LocalDate periodEnd;
        Map<String, Number> fields = new HashMap<>();
    }

    public EdgarService(WebService webService, QuarterService quarterService,
            com.fattorestreet.sec_api.repository.AssetRepository assetRepository) {
        this.webService = webService;
        this.quarterService = quarterService;
        this.assetRepository = assetRepository;
    }

    // --- Public entry point ---

    public Map<String, Object> syncFramesFull() throws Exception {
        Map<Long, Asset> assetMap = loadAssetMap();
        long fundsSkipped = assetRepository.countByIsFund(true);
        int currentYear = LocalDate.now().getYear();

        // Phase 1: Collect all quarterly frames (2009 to present)
        Map<String, Quarter> collected = new HashMap<>();
        for (int y = 2009; y <= currentYear; y++) {
            for (int q = 1; q <= 4; q++) {
                collectFrames("CY" + y + "Q" + q, assetMap, collected);
            }
        }

        // Phase 2: Fetch annual frames and derive missing quarters
        for (int y = 2009; y <= currentYear; y++) {
            Map<Long, AnnualData> annualTotals = collectAnnualTotals(y, assetMap);
            deriveFromAnnualTotals(collected, annualTotals, assetMap);
        }

        // Phase 2.5: Derive missing balance sheet fields
        for (Quarter q : collected.values()) {
            deriveBalanceSheetFields(q);
        }

        // Phase 3: Persist everything
        persistCollected(collected);

        return Map.of("equitiesProcessed", assetMap.size(), "fundsSkipped", fundsSkipped);
    }

    // --- Private implementation ---

    private Map<Long, Asset> loadAssetMap() {
        List<Asset> allAssets = assetRepository.findByIsFund(false);
        return allAssets.stream().collect(Collectors.toMap(Asset::getCik, a -> a));
    }

    /**
     * Collects quarterly frame data for a single period (e.g. "CY2024Q3") into
     * the provided map. Does not persist -- caller is responsible for that.
     */
    private void collectFrames(String period, Map<Long, Asset> assetMap, Map<String, Quarter> collected) {
        for (FrameConcept fc : FRAME_CONCEPTS) {
            for (String tag : fc.tags) {
                try {
                    String framePeriod = fc.isInstant ? period + "I" : period;
                    String json = webService.fetchXbrlFrames(fc.taxonomy, tag, fc.unit, framePeriod);
                    JsonNode root = mapper.readTree(json);
                    JsonNode dataNode = root.get("data");
                    if (dataNode == null || !dataNode.isArray())
                        continue;

                    for (JsonNode node : dataNode) {
                        Long cik = node.get("cik").asLong();
                        Asset asset = assetMap.get(cik);
                        if (asset == null)
                            continue;

                        String startStr = node.has("start") ? node.get("start").asText() : null;
                        String endStr = node.get("end").asText();
                        LocalDate start = startStr != null ? LocalDate.parse(startStr) : LocalDate.parse(endStr);
                        LocalDate end = LocalDate.parse(endStr);

                        // Strict filtering for ~3 month quarters (80 to 100 days) for flow concepts
                        if (!fc.isInstant) {
                            long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                            if (daysDiff < 80 || daysDiff > 100)
                                continue;
                        }

                        int year = 0;
                        int qtr = 0;
                        if (node.has("fy")) {
                            year = node.get("fy").asInt();
                        } else if (period.startsWith("CY")) {
                            year = Integer.parseInt(period.substring(2, 6));
                        }
                        if (node.has("fp")) {
                            String fp = node.get("fp").asText();
                            if (fp.equalsIgnoreCase("Q1"))
                                qtr = 1;
                            else if (fp.equalsIgnoreCase("Q2"))
                                qtr = 2;
                            else if (fp.equalsIgnoreCase("Q3"))
                                qtr = 3;
                            else if (fp.equalsIgnoreCase("FY") || fp.equalsIgnoreCase("Q4"))
                                qtr = 4;
                        } else if (period.length() >= 8) {
                            try {
                                qtr = Integer.parseInt(period.substring(7, 8));
                            } catch (Exception e) {
                                // ignore
                            }
                        }

                        JsonNode valNode = node.get("val");
                        Object value = valNode.isNumber()
                                ? (valNode.isFloatingPointNumber() ? valNode.asDouble() : valNode.asLong())
                                : null;

                        // Skip if year/quarter unknown
                        if (year == 0 || qtr == 0)
                            continue;

                        // Merge into collected map keyed by cik|year|quarter
                        String key = cik + "|" + year + "|" + qtr;
                        Quarter quarter = collected.get(key);
                        if (quarter == null) {
                            quarter = new Quarter();
                            quarter.setAsset(asset);
                            quarter.setYear(year);
                            quarter.setQuarter(qtr);
                            quarter.setPeriodStart(start);
                            quarter.setPeriodEnd(end);
                            collected.put(key, quarter);
                        } else {
                            // Widen the period range
                            if (start.isBefore(quarter.getPeriodStart()))
                                quarter.setPeriodStart(start);
                            if (end.isAfter(quarter.getPeriodEnd()))
                                quarter.setPeriodEnd(end);
                        }
                        // Only set if this CIK's quarter doesn't already have a value for this field
                        if (getQuarterField(quarter, fc.fieldName) == null) {
                            setQuarterField(quarter, fc.fieldName, value);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error collecting concept {} ({}) for {}: {}", fc.fieldName, tag, period,
                            e.getMessage());
                }
            }
        }
    }

    /**
     * Fetches annual frame data (CY{year}) for all flow concepts and returns
     * per-CIK annual totals including fiscal year period dates.
     */
    private Map<Long, AnnualData> collectAnnualTotals(int year, Map<Long, Asset> assetMap) {
        Map<Long, AnnualData> annualMap = new HashMap<>();
        for (FrameConcept fc : FRAME_CONCEPTS) {
            if (fc.isInstant)
                continue; // Only flow concepts need annual derivation
            for (String tag : fc.tags) {
                try {
                    String framePeriod = "CY" + year;
                    String json = webService.fetchXbrlFrames(fc.taxonomy, tag, fc.unit, framePeriod);
                    JsonNode root = mapper.readTree(json);
                    JsonNode dataNode = root.get("data");
                    if (dataNode == null || !dataNode.isArray())
                        continue;

                    for (JsonNode node : dataNode) {
                        Long cik = node.get("cik").asLong();
                        if (!assetMap.containsKey(cik))
                            continue;

                        String startStr = node.has("start") ? node.get("start").asText() : null;
                        String endStr = node.get("end").asText();
                        if (startStr == null)
                            continue; // Flow concepts must have a start date

                        LocalDate start = LocalDate.parse(startStr);
                        LocalDate end = LocalDate.parse(endStr);
                        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                        if (daysDiff < 350 || daysDiff > 380)
                            continue; // Only accept ~12-month annual periods

                        JsonNode valNode = node.get("val");
                        if (valNode == null || !valNode.isNumber())
                            continue;

                        Number value = valNode.isFloatingPointNumber() ? valNode.asDouble() : valNode.asLong();

                        AnnualData data = annualMap.get(cik);
                        if (data == null) {
                            data = new AnnualData();
                            data.periodStart = start;
                            data.periodEnd = end;
                            annualMap.put(cik, data);
                        } else {
                            if (start.isBefore(data.periodStart))
                                data.periodStart = start;
                            if (end.isAfter(data.periodEnd))
                                data.periodEnd = end;
                        }
                        // Only set if this CIK doesn't already have a value for this field
                        if (!data.fields.containsKey(fc.fieldName)) {
                            data.fields.put(fc.fieldName, value);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error collecting annual {} ({}) for CY{}: {}", fc.fieldName, tag, year,
                            e.getMessage());
                }
            }
        }
        return annualMap;
    }

    private static final Set<String> NON_ADDITIVE_FIELDS = Set.of(
            "earningsPerShareBasic", "earningsPerShareDiluted");

    /**
     * Derives missing quarterly values from annual totals using date-range-based
     * quarter matching. The annual period (e.g. 2024-07-01 to 2025-06-30) is split
     * into 4 calendar-quarter slots, and each slot is looked up in the collected map
     * by its calendar year and quarter. This correctly handles companies with
     * non-calendar fiscal years (e.g. MSFT ending June, AAPL ending September).
     *
     * EPS fields are skipped because they are not additive across quarters.
     */
    private void deriveFromAnnualTotals(Map<String, Quarter> collected, Map<Long, AnnualData> annualTotals,
            Map<Long, Asset> assetMap) {
        for (Map.Entry<Long, AnnualData> entry : annualTotals.entrySet()) {
            Long cik = entry.getKey();
            AnnualData annual = entry.getValue();
            Asset asset = assetMap.get(cik);
            if (asset == null)
                continue;

            // Split the annual period into 4 calendar-quarter slots
            LocalDate annualStart = annual.periodStart;
            LocalDate[] slotStarts = new LocalDate[4];
            LocalDate[] slotEnds = new LocalDate[4];
            for (int i = 0; i < 4; i++) {
                slotStarts[i] = annualStart.plusMonths(i * 3);
                slotEnds[i] = annualStart.plusMonths((i + 1) * 3).minusDays(1);
            }
            slotEnds[3] = annual.periodEnd;

            // Look up collected quarters by calendar year/quarter of each slot
            String[] keys = new String[4];
            Quarter[] quarters = new Quarter[4];
            for (int i = 0; i < 4; i++) {
                int calYear = slotEnds[i].getYear();
                int calQtr = (slotEnds[i].getMonthValue() - 1) / 3 + 1;
                keys[i] = cik + "|" + calYear + "|" + calQtr;
                quarters[i] = collected.get(keys[i]);
            }

            for (FrameConcept fc : FRAME_CONCEPTS) {
                if (fc.isInstant)
                    continue;
                if (NON_ADDITIVE_FIELDS.contains(fc.fieldName))
                    continue;

                Number annualVal = annual.fields.get(fc.fieldName);
                if (annualVal == null)
                    continue;

                int missingIndex = -1;
                int missingCount = 0;
                double sum = 0;
                for (int i = 0; i < 4; i++) {
                    Number qVal = quarters[i] != null ? getQuarterField(quarters[i], fc.fieldName) : null;
                    if (qVal == null) {
                        missingCount++;
                        missingIndex = i;
                    } else {
                        sum += qVal.doubleValue();
                    }
                }

                if (missingCount != 1)
                    continue;

                double derivedVal = annualVal.doubleValue() - sum;

                if (quarters[missingIndex] == null) {
                    int calYear = slotEnds[missingIndex].getYear();
                    int calQtr = (slotEnds[missingIndex].getMonthValue() - 1) / 3 + 1;
                    Quarter derived = new Quarter();
                    derived.setAsset(asset);
                    derived.setYear(calYear);
                    derived.setQuarter(calQtr);
                    derived.setPeriodStart(slotStarts[missingIndex]);
                    derived.setPeriodEnd(slotEnds[missingIndex]);
                    collected.put(keys[missingIndex], derived);
                    quarters[missingIndex] = derived;
                }

                setQuarterField(quarters[missingIndex], fc.fieldName, derivedVal);
            }
        }
    }

    /**
     * Persists all collected quarters to the database, grouped by (year, quarter).
     */
    private void persistCollected(Map<String, Quarter> collected) {
        Map<String, List<Quarter>> groups = collected.values().stream()
                .collect(Collectors.groupingBy(q -> q.getYear() + "|" + q.getQuarter()));
        for (Map.Entry<String, List<Quarter>> entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            int year = Integer.parseInt(parts[0]);
            int qtr = Integer.parseInt(parts[1]);
            quarterService.batchUpsertQuarters(year, qtr, entry.getValue());
        }
    }

    private void setQuarterField(Quarter q, String field, Object val) {
        if (val == null || !(val instanceof Number num))
            return;
        switch (field) {
            case "revenues":
                q.setRevenues(num.longValue());
                break;
            case "netIncomeLoss":
                q.setNetIncomeLoss(num.longValue());
                break;
            case "operatingIncomeLoss":
                q.setOperatingIncomeLoss(num.longValue());
                break;
            case "grossProfit":
                q.setGrossProfit(num.longValue());
                break;
            case "earningsPerShareBasic":
                q.setEarningsPerShareBasic(num.doubleValue());
                break;
            case "earningsPerShareDiluted":
                q.setEarningsPerShareDiluted(num.doubleValue());
                break;
            case "assets":
                q.setAssets(num.longValue());
                break;
            case "liabilities":
                q.setLiabilities(num.longValue());
                break;
            case "stockholdersEquity":
                q.setStockholdersEquity(num.longValue());
                break;
            case "cashAndCashEquivalentsAtCarryingValue":
                q.setCashAndCashEquivalentsAtCarryingValue(num.longValue());
                break;
            case "accountsReceivableNetCurrent":
                q.setAccountsReceivableNetCurrent(num.longValue());
                break;
            case "inventoryNet":
                q.setInventoryNet(num.longValue());
                break;
            case "netCashProvidedByUsedInOperatingActivities":
                q.setNetCashProvidedByUsedInOperatingActivities(num.longValue());
                break;
            case "paymentsOfDividends":
                q.setPaymentsOfDividends(num.longValue());
                break;
            case "paymentsForRepurchaseOfCommonStock":
                q.setPaymentsForRepurchaseOfCommonStock(num.longValue());
                break;
            default:
                // field is not one this method maps onto Quarter; ignore it
                break;
        }
    }

    private Number getQuarterField(Quarter q, String field) {
        return switch (field) {
            case "revenues" -> q.getRevenues();
            case "netIncomeLoss" -> q.getNetIncomeLoss();
            case "operatingIncomeLoss" -> q.getOperatingIncomeLoss();
            case "grossProfit" -> q.getGrossProfit();
            case "earningsPerShareBasic" -> q.getEarningsPerShareBasic();
            case "earningsPerShareDiluted" -> q.getEarningsPerShareDiluted();
            case "netCashProvidedByUsedInOperatingActivities" ->
                    q.getNetCashProvidedByUsedInOperatingActivities();
            case "paymentsOfDividends" -> q.getPaymentsOfDividends();
            case "paymentsForRepurchaseOfCommonStock" -> q.getPaymentsForRepurchaseOfCommonStock();
            default -> null;
        };
    }

    private void deriveBalanceSheetFields(Quarter q) {
        Long a = q.getAssets();
        Long l = q.getLiabilities();
        Long e = q.getStockholdersEquity();

        int present = (a != null ? 1 : 0) + (l != null ? 1 : 0) + (e != null ? 1 : 0);
        if (present != 2)
            return;

        if (a == null)
            q.setAssets(l + e);
        else if (l == null)
            q.setLiabilities(a - e);
        else
            q.setStockholdersEquity(a - l);
    }

    public void updateFinancials(Asset asset) throws Exception {
        String json = webService.fetchFinancials(asset.getCik());
        JsonNode root = mapper.readTree(json);

        Map<String, Map<String, FactData>> quarterData = getQuarterlyFacts(FIELD_TO_TAGS, root);
        List<Quarter> quarters = new ArrayList<>();

        for (Map.Entry<String, Map<String, FactData>> entry : quarterData.entrySet()) {
            Map<String, FactData> facts = entry.getValue();

            // Extract dates from identifying facts
            LocalDate periodStart = null;
            LocalDate periodEnd = null;
            Integer fy = null;
            String fp = null;

            // Try to find dates from stock fields or flow fields
            for (FactData fd : facts.values()) {
                if (fd.startDate != null && fd.endDate != null) {
                    periodStart = fd.startDate;
                    periodEnd = fd.endDate;
                }
                if (fd.fy != 0) {
                    fy = fd.fy;
                }
                if (fd.fp != null) {
                    fp = fd.fp;
                }
                if (periodStart != null && periodEnd != null && fy != null && fp != null) {
                    break;
                }
            }

            if (periodStart == null || periodEnd == null)
                continue;

            // Strict filtering for ~3 month quarters (80 to 100 days)
            long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd);
            if (daysDiff < 80)
                continue;

            Quarter quarter = new Quarter();
            quarter.setAsset(asset);
            quarter.setPeriodStart(periodStart);
            quarter.setPeriodEnd(periodEnd);

            if (fy != null) {
                quarter.setYear(fy);
            }
            if (fp != null) {
                if (fp.equalsIgnoreCase("Q1"))
                    quarter.setQuarter(1);
                else if (fp.equalsIgnoreCase("Q2"))
                    quarter.setQuarter(2);
                else if (fp.equalsIgnoreCase("Q3"))
                    quarter.setQuarter(3);
                else if (fp.equalsIgnoreCase("FY") || fp.equalsIgnoreCase("Q4"))
                    quarter.setQuarter(4);
            }

            // Populate fields
            quarter.setRevenues(getLong(facts, "revenues"));
            quarter.setNetIncomeLoss(getLong(facts, "netIncomeLoss"));
            quarter.setOperatingIncomeLoss(getLong(facts, "operatingIncomeLoss"));
            quarter.setGrossProfit(getLong(facts, "grossProfit"));
            quarter.setEarningsPerShareBasic(getDouble(facts, "earningsPerShareBasic"));
            quarter.setEarningsPerShareDiluted(getDouble(facts, "earningsPerShareDiluted"));

            quarter.setAssets(getLong(facts, "assets"));
            quarter.setLiabilities(getLong(facts, "liabilities"));
            quarter.setStockholdersEquity(getLong(facts, "stockholdersEquity"));
            quarter.setCashAndCashEquivalentsAtCarryingValue(getLong(facts, "cashAndCashEquivalentsAtCarryingValue"));
            quarter.setAccountsReceivableNetCurrent(getLong(facts, "accountsReceivableNetCurrent"));
            quarter.setInventoryNet(getLong(facts, "inventoryNet"));

            quarter.setNetCashProvidedByUsedInOperatingActivities(
                    getLong(facts, "netCashProvidedByUsedInOperatingActivities"));
            quarter.setPaymentsOfDividends(getLong(facts, "paymentsOfDividends"));
            quarter.setPaymentsForRepurchaseOfCommonStock(getLong(facts, "paymentsForRepurchaseOfCommonStock"));

            deriveBalanceSheetFields(quarter);

            quarters.add(quarter);
        }
        quarterService.updateAssetQuarters(asset, quarters);
    }

    private Long getLong(Map<String, FactData> facts, String key) {
        return facts.containsKey(key) ? facts.get(key).asLong() : null;
    }

    private Double getDouble(Map<String, FactData> facts, String key) {
        return facts.containsKey(key) ? facts.get(key).asDouble() : null;
    }

    private static class FactData {
        Object value;
        LocalDate startDate;
        LocalDate endDate;
        int fy;
        String fp;

        FactData(Object value, LocalDate startDate, LocalDate endDate, int fy, String fp) {
            this.value = value;
            this.startDate = startDate;
            this.endDate = endDate;
            this.fy = fy;
            this.fp = fp;
        }

        double asDouble() {
            if (value instanceof Number)
                return ((Number) value).doubleValue();
            return 0.0;
        }

        long asLong() {
            if (value instanceof Number)
                return ((Number) value).longValue();
            return 0L;
        }
    }

    private Map<String, Map<String, FactData>> getQuarterlyFacts(Map<String, List<String>> fieldToTags,
            JsonNode root) {
        Map<String, Map<String, FactData>> quarterData = new HashMap<>();
        JsonNode factsNode = root.get("facts");
        if (factsNode == null)
            return quarterData;

        JsonNode usGaap = factsNode.get("us-gaap");
        JsonNode dei = factsNode.get("dei");

        for (Map.Entry<String, List<String>> fieldEntry : fieldToTags.entrySet()) {
            String fieldName = fieldEntry.getKey();
            List<String> tags = fieldEntry.getValue();
            boolean isStock = STOCK_FIELDS.contains(fieldName);

            // Grouping: end date string -> duration -> FactData
            Map<String, Map<Integer, FactData>> dataPoints = new HashMap<>();

            for (String tag : tags) {
                JsonNode conceptNode = (usGaap != null && usGaap.has(tag)) ? usGaap.get(tag)
                        : (dei != null && dei.has(tag)) ? dei.get(tag) : null;

                if (conceptNode == null)
                    continue;

                JsonNode units = conceptNode.get("units");
                if (units == null)
                    continue;

                // Iterate over all units to find matches
                Iterator<Map.Entry<String, JsonNode>> fields = units.properties().iterator();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> unitEntry = fields.next();
                    JsonNode unitNode = unitEntry.getValue();

                    for (JsonNode entry : unitNode) {
                        if (!entry.has("end"))
                            continue;

                        String endStr = entry.get("end").asText();
                        LocalDate endDate = LocalDate.parse(endStr);
                        int fy = entry.has("fy") ? entry.get("fy").asInt() : 0;
                        String fp = entry.has("fp") ? entry.get("fp").asText() : null;

                        LocalDate startDate = null;
                        int durationMonths = 0; // 0 for stock/instant

                        if (entry.has("start")) {
                            try {
                                startDate = LocalDate.parse(entry.get("start").asText());
                                long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);

                                // Approximate duration logic for bucketing
                                if (days > 80 && days < 100)
                                    durationMonths = 3;
                                else if (days > 170 && days < 190)
                                    durationMonths = 6;
                                else if (days > 260 && days < 280)
                                    durationMonths = 9;
                                else if (days > 350 && days < 375)
                                    durationMonths = 12;
                                else
                                    durationMonths = (int) (days / 30);
                            } catch (Exception ignored) {
                            }
                        }

                        if (isStock) {
                            if (startDate == null)
                                startDate = endDate;
                            durationMonths = 0;
                        } else {
                            if (startDate == null)
                                continue; // Flow metrics must have start date
                        }

                        JsonNode valNode = entry.get("val");
                        Object value = valNode.isNumber()
                                ? (valNode.isFloatingPointNumber() ? valNode.asDouble() : valNode.asLong())
                                : valNode.asText();

                        // Key by End Date for grouping similar durations
                        dataPoints.computeIfAbsent(endStr, k -> new HashMap<>()).putIfAbsent(durationMonths,
                                new FactData(value, startDate, endDate, fy, fp));
                    }
                }
            }

            // Process collected points
            for (Map.Entry<String, Map<Integer, FactData>> dateEntry : dataPoints.entrySet()) {
                Map<Integer, FactData> durations = dateEntry.getValue();

                FactData selectedData = null;
                if (isStock) {
                    // Take the snapshot (duration 0 usually, or whatever is there)
                    selectedData = durations.values().stream().findFirst().orElse(null);
                } else {
                    if (durations.containsKey(3)) {
                        selectedData = durations.get(3);
                    } else {
                        // Derive Q3 from YTD9 - YTD6, etc.
                        for (int d : new int[] { 6, 9, 12 }) {
                            if (durations.containsKey(d)) {
                                FactData currentYtd = durations.get(d);
                                for (Map.Entry<String, Map<Integer, FactData>> otherEntry : dataPoints.entrySet()) {
                                    Map<Integer, FactData> otherDurations = otherEntry.getValue();
                                    if (otherDurations.containsKey(d - 3)) {
                                        FactData prevCandidate = otherDurations.get(d - 3);
                                        // Must share same start date
                                        if (prevCandidate.startDate.equals(currentYtd.startDate)) {
                                            // Found valid predecessor
                                            double currentVal = currentYtd.asDouble();
                                            double prevVal = prevCandidate.asDouble();

                                            LocalDate derivedStart = prevCandidate.endDate.plusDays(1);

                                            selectedData = new FactData(
                                                    currentVal - prevVal,
                                                    derivedStart,
                                                    currentYtd.endDate, currentYtd.fy, currentYtd.fp);
                                            break;
                                        }
                                    }
                                }
                                if (selectedData != null)
                                    break;
                            }
                        }
                    }
                }

                if (selectedData != null) {
                    String periodKey = selectedData.startDate.toString() + "|" + selectedData.endDate.toString();
                    quarterData.computeIfAbsent(periodKey, k -> new HashMap<>()).putIfAbsent(fieldName, selectedData);
                }
            }
        }
        return quarterData;
    }
}

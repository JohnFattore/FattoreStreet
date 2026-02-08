package com.example.sec_api.service;

import com.example.sec_api.model.Asset;
import com.example.sec_api.model.Quarter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class EdgarService {

    private final WebService webService;
    private final QuarterService quarterService;
    private final com.example.sec_api.repository.AssetRepository assetRepository;
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
        String tag;
        String unit;
        String fieldName;
        boolean isInstant;

        FrameConcept(String taxonomy, String tag, String unit, String fieldName, boolean isInstant) {
            this.taxonomy = taxonomy;
            this.tag = tag;
            this.unit = unit;
            this.fieldName = fieldName;
            this.isInstant = isInstant;
        }
    }

    private static final List<FrameConcept> FRAME_CONCEPTS = List.of(
            new FrameConcept("us-gaap", "Revenues", "USD", "revenues", false),
            new FrameConcept("us-gaap", "NetIncomeLoss", "USD", "netIncomeLoss", false),
            new FrameConcept("us-gaap", "OperatingIncomeLoss", "USD", "operatingIncomeLoss", false),
            new FrameConcept("us-gaap", "GrossProfit", "USD", "grossProfit", false),
            new FrameConcept("us-gaap", "EarningsPerShareBasic", "USD-per-shares", "earningsPerShareBasic", false),
            new FrameConcept("us-gaap", "EarningsPerShareDiluted", "USD-per-shares", "earningsPerShareDiluted", false),
            new FrameConcept("us-gaap", "Assets", "USD", "assets", true),
            new FrameConcept("us-gaap", "Liabilities", "USD", "liabilities", true),
            new FrameConcept("us-gaap", "StockholdersEquity", "USD", "stockholdersEquity", true),
            new FrameConcept("us-gaap", "CashAndCashEquivalentsAtCarryingValue", "USD",
                    "cashAndCashEquivalentsAtCarryingValue", true),
            new FrameConcept("us-gaap", "AccountsReceivableNetCurrent", "USD", "accountsReceivableNetCurrent", true),
            new FrameConcept("us-gaap", "InventoryNet", "USD", "inventoryNet", true),
            new FrameConcept("us-gaap", "NetCashProvidedByUsedInOperatingActivities", "USD",
                    "netCashProvidedByUsedInOperatingActivities", false),
            new FrameConcept("us-gaap", "PaymentsOfDividends", "USD", "paymentsOfDividends", false),
            new FrameConcept("us-gaap", "PaymentsForRepurchaseOfCommonStock", "USD",
                    "paymentsForRepurchaseOfCommonStock", false));

    public EdgarService(WebService webService, QuarterService quarterService,
            com.example.sec_api.repository.AssetRepository assetRepository) {
        this.webService = webService;
        this.quarterService = quarterService;
        this.assetRepository = assetRepository;
    }

    public void syncSnp500FramesByYear(int year) throws Exception {
        for (int q = 1; q <= 4; q++) {
            syncSnp500Frames("CY" + year + "Q" + q);
        }
    }

    public void syncSnp500FramesByYearRange(int startYear, int endYear) throws Exception {
        for (int y = startYear; y <= endYear; y++) {
            syncSnp500FramesByYear(y);
        }
    }

    public void syncSnp500FramesFull() throws Exception {
        int currentYear = LocalDate.now().getYear();
        syncSnp500FramesByYearRange(2009, currentYear);
    }

    public void syncSnp500Frames(String period) throws Exception {
        List<String> snp500Tickers = webService.getSnP500List();
        List<Asset> snp500Assets = assetRepository.findByTickersIn(snp500Tickers);
        Map<Long, Asset> assetMap = snp500Assets.stream().collect(Collectors.toMap(Asset::getCik, a -> a));

        for (FrameConcept fc : FRAME_CONCEPTS) {
            try {
                String framePeriod = fc.isInstant ? period + "I" : period;
                String json = webService.fetchXbrlFrames(fc.taxonomy, fc.tag, fc.unit, framePeriod);
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

                    Quarter quarter = new Quarter();
                    quarter.setAsset(asset);
                    quarter.setPeriodStart(start);
                    quarter.setPeriodEnd(end);

                    int year = 0;
                    int qtr = 0;
                    if (node.has("fy")) {
                        year = node.get("fy").asInt();
                    } else if (period != null && period.startsWith("CY")) {
                        year = Integer.parseInt(period.substring(2, 6));
                    }
                    quarter.setYear(year != 0 ? year : null);

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
                    } else if (period != null && period.length() >= 8) {
                        try {
                            qtr = Integer.parseInt(period.substring(7, 8));
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    quarter.setQuarter(qtr != 0 ? qtr : null);

                    JsonNode valNode = node.get("val");
                    Object value = valNode.isNumber()
                            ? (valNode.isFloatingPointNumber() ? valNode.asDouble() : valNode.asLong())
                            : null;

                    setQuarterField(quarter, fc.fieldName, value);
                    quarterService.createOrUpdateQuarter(quarter);
                }
            } catch (Exception e) {
                System.err.println("Error syncing concept " + fc.tag + " for " + period + ": " + e.getMessage());
            }
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
        }
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
                Iterator<Map.Entry<String, JsonNode>> fields = units.fields();
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

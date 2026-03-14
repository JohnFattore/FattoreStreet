package com.fattorestreet.sec_api.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fattorestreet.sec_api.service.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.service.AssetService;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.service.ListingService;
import com.fattorestreet.sec_api.model.Quarter;
import com.fattorestreet.sec_api.repository.QuarterRepository;
import com.fattorestreet.sec_api.service.EdgarService;
import com.fattorestreet.sec_api.service.FinancialService;
import com.fattorestreet.sec_api.service.PriceService;
import com.fattorestreet.sec_api.service.PriceAdjustmentService;
import com.fattorestreet.sec_api.service.IexHistService;
import com.fattorestreet.sec_api.service.EtfIdentityService;
import com.fattorestreet.sec_api.service.FilingSummaryService;
import com.fattorestreet.sec_api.model.FilingSummary;
import com.fattorestreet.sec_api.repository.FilingSummaryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

@Validated
@RestController
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final WebService webService;
    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final ListingService listingService;
    private final QuarterRepository quarterRepository;
    private final EdgarService edgarService;
    private final FinancialService financialService;
    private final PriceService priceService;
    private final PriceAdjustmentService priceAdjustmentService;
    private final IexHistService iexHistService;
    private final EtfIdentityService etfIdentityService;
    private final FilingSummaryService filingSummaryService;
    private final FilingSummaryRepository filingSummaryRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Value("${ADMIN_API_KEY:spike}")
    private String adminApiKey;

    @org.springframework.beans.factory.annotation.Value("${iex.data.dir:./data/iex_prices}")
    private String iexDataDir;

    public MainController(WebService webService, AssetService assetService, AssetRepository assetRepository,
            ListingService listingService,
            QuarterRepository quarterRepository,
            EdgarService edgarService, FinancialService financialService,
            PriceService priceService, PriceAdjustmentService priceAdjustmentService,
            IexHistService iexHistService, EtfIdentityService etfIdentityService, FilingSummaryService filingSummaryService,
            FilingSummaryRepository filingSummaryRepository,
            CorporateActionRepository corporateActionRepository) {
        this.webService = webService;
        this.assetService = assetService;
        this.assetRepository = assetRepository;
        this.listingService = listingService;
        this.quarterRepository = quarterRepository;
        this.edgarService = edgarService;
        this.financialService = financialService;
        this.priceService = priceService;
        this.priceAdjustmentService = priceAdjustmentService;
        this.iexHistService = iexHistService;
        this.etfIdentityService = etfIdentityService;
        this.filingSummaryService = filingSummaryService;
        this.filingSummaryRepository = filingSummaryRepository;
        this.corporateActionRepository = corporateActionRepository;
    }

    @GetMapping("/admin/asset-load")
    public ResponseEntity<?> assetLoad(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam(defaultValue = "false") boolean overwriteExisting) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        long startTime = System.currentTimeMillis();
        try {
            Map<Integer, Map<String, String>> secTickers = webService.fetchSecTickers();
            Map<String, SecTickerRow> tickerRows = new LinkedHashMap<>();
            for (Map<String, String> secTicker : secTickers.values()) {
                String ticker = secTicker.get("ticker");
                Long cik = parseCik(secTicker.get("cik_str"));
                if (ticker == null || ticker.isBlank() || cik == null) {
                    continue;
                }
                tickerRows.put(ticker, new SecTickerRow(ticker, cik, secTicker.get("title"), false));
            }

            List<SecTickerRow> secFundRows = parseSecMutualFundTickers(webService.fetchSecMutualFundTickers());
            for (SecTickerRow secFundRow : secFundRows) {
                SecTickerRow existing = tickerRows.get(secFundRow.ticker());
                if (existing == null) {
                    tickerRows.put(secFundRow.ticker(), secFundRow);
                    continue;
                }
                String mergedTitle = (existing.title() != null && !existing.title().isBlank())
                        ? existing.title()
                        : secFundRow.title();
                tickerRows.put(
                        secFundRow.ticker(),
                        new SecTickerRow(secFundRow.ticker(), secFundRow.cik(), mergedTitle, existing.isFund() || secFundRow.isFund()));
            }

            int equityCount = 0;
            int fundCount = 0;
            for (SecTickerRow secTickerRow : tickerRows.values()) {
                Asset asset = new Asset();
                String ticker = secTickerRow.ticker();
                boolean isFund = secTickerRow.isFund();
                asset.setCik(secTickerRow.cik());
                asset.setIsFund(isFund);
                asset = assetService.createOrUpdateAsset(asset);
                Listing listing = new Listing();
                listing.setTicker(ticker);
                listing.setTitle(
                        secTickerRow.title() != null && !secTickerRow.title().isBlank()
                                ? secTickerRow.title()
                                : ticker);
                listing.setAsset(asset);
                listingService.createOrUpdateListing(listing);
                if (isFund) {
                    fundCount++;
                } else {
                    equityCount++;
                }
            }
            Map<String, Object> result = etfIdentityService.enrichFundListingIdentities(overwriteExisting);
            long elapsed = System.currentTimeMillis() - startTime;
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            String duration = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "." + (elapsed % 1000) / 100 + "s";
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("equitiesLoaded", equityCount);
            response.put("fundsLoaded", fundCount);
            response.put("tickersLoaded", equityCount + fundCount);
            response.put("etfIdentityOverwriteExisting", overwriteExisting);
            response.put("etfIdentityEnrichment", result);
            response.put("duration", duration);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during admin asset load and ETF enrichment", e);
            return ResponseEntity.internalServerError().body("Error during admin asset load: " + e.getMessage());
        }
    }

    @GetMapping("/admin/test")
    public ResponseEntity<?> test(@org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key) throws Exception {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        String json = null;
        Long cik = 320193L;
        json = webService.fetchFinancials(cik);
        JsonNode root = mapper.readTree(json);
        JsonNode facts = null;
        facts = root.get("facts").get("us-gaap").get("RevenueFromContractWithCustomerExcludingAssessedTax").get("units")
                .get("USD");
        return ResponseEntity.ok(facts.toString());
    }

    @GetMapping("/quarters")
    public ResponseEntity quarters(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker) {
        Asset asset = assetRepository.findByListings_Ticker(ticker);
        if (asset == null) {
            return ResponseEntity.notFound().build();
        }
        List<Quarter> quarters = quarterRepository.findByAsset(asset);
        List<Map<String, Object>> quarterOutput = new ArrayList<>();
        for (Quarter q : quarters) {
            Map<String, Object> qm = new HashMap<>();
            qm.put("year", q.getYear());
            qm.put("quarter", q.getQuarter());
            qm.put("periodStart", q.getPeriodStart());
            qm.put("periodEnd", q.getPeriodEnd());

            // Income Statement
            qm.put("revenues", q.getRevenues());
            qm.put("netIncomeLoss", q.getNetIncomeLoss());
            qm.put("operatingIncomeLoss", q.getOperatingIncomeLoss());
            qm.put("grossProfit", q.getGrossProfit());
            qm.put("epsBasic", q.getEarningsPerShareBasic());
            qm.put("epsDiluted", q.getEarningsPerShareDiluted());

            // Balance Sheet
            qm.put("assets", q.getAssets());
            qm.put("liabilities", q.getLiabilities());
            qm.put("equity", q.getStockholdersEquity());
            qm.put("cash", q.getCashAndCashEquivalentsAtCarryingValue());
            qm.put("receivables", q.getAccountsReceivableNetCurrent());
            qm.put("inventory", q.getInventoryNet());

            // Cash Flow
            qm.put("ocf", q.getNetCashProvidedByUsedInOperatingActivities());
            qm.put("dividends", q.getPaymentsOfDividends());
            qm.put("buybacks", q.getPaymentsForRepurchaseOfCommonStock());

            quarterOutput.add(qm);
        }
        Map<String, Object> response = Map.of(
                "ticker", ticker,
                "cik", asset.getCik().toString(),
                "quarters", quarterOutput);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/company-fact-sheet")
    public ResponseEntity companyFactSheet(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker) {
        Asset asset = assetRepository.findByListings_Ticker(ticker);
        if (asset == null) {
            return ResponseEntity.notFound().build();
        }
        List<Quarter> quarters = quarterRepository.findByAsset(asset);
        Map<String, Object> metrics = financialService.calculateMetrics(quarters);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker);
        response.put("cik", asset.getCik().toString());

        if (!metrics.isEmpty()) {
            response.put("ttmNetIncome", formatNumber(metrics.get("ttmNetIncome")));
            response.put("ttmRevenue", formatNumber(metrics.get("ttmRevenue")));
            response.put("ttmOperatingCashFlow", formatNumber(metrics.get("ttmOperatingCashFlow")));
            response.put("ttmOperatingIncome", formatNumber(metrics.get("ttmOperatingIncome")));
            response.put("ttmGrossProfit", formatNumber(metrics.get("ttmGrossProfit")));

            response.put("ttmNetIncomeYoY", formatPercent(metrics.get("ttmNetIncomeYoY")));
            response.put("ttmRevenueYoY", formatPercent(metrics.get("ttmRevenueYoY")));

            // Balance Sheet
            response.put("latestAssets", formatNumber(metrics.get("latestAssets")));
            response.put("latestLiabilities", formatNumber(metrics.get("latestLiabilities")));
            response.put("latestEquity", formatNumber(metrics.get("latestEquity")));
            response.put("latestInventory", formatNumber(metrics.get("latestInventory")));
            response.put("latestCash", formatNumber(metrics.get("latestCash")));
            response.put("latestEps", formatNumber(metrics.get("latestEps")));

            // Ratios
            response.put("netMargin", formatPercent(metrics.get("netMargin")));
            response.put("grossMargin", formatPercent(metrics.get("grossMargin")));
            response.put("debtToAssets", formatPercent(metrics.get("debtToAssets")));
            response.put("cashToLiabilities", formatPercent(metrics.get("cashToLiabilities")));
            response.put("roA", formatPercent(metrics.get("roA")));
            response.put("ocfToNetIncome", formatNumber(metrics.get("ocfToNetIncome")));

            if (!quarters.isEmpty()) {
                Quarter latest = quarters.get(0);
                response.put("latestQuarterEnd", latest.getPeriodEnd().toString());
            }
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/prices")
    public ResponseEntity<?> prices(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        List<DailyPrice> prices;
        if (start != null && end != null) {
            prices = priceService.getPrices(ticker, LocalDate.parse(start), LocalDate.parse(end));
        } else if (start != null) {
            prices = priceService.getPrices(ticker, LocalDate.parse(start), LocalDate.now());
        } else {
            prices = priceService.getPrices(ticker);
        }

        List<Map<String, Object>> priceOutput = new ArrayList<>();
        for (DailyPrice p : prices) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("date", p.getTradeDate().toString());
            pm.put("open", p.getOpenPrice());
            pm.put("high", p.getHighPrice());
            pm.put("low", p.getLowPrice());
            pm.put("close", p.getClosePrice());
            pm.put("adjustedOpen", p.getAdjustedOpen());
            pm.put("adjustedHigh", p.getAdjustedHigh());
            pm.put("adjustedLow", p.getAdjustedLow());
            pm.put("adjustedClose", p.getAdjustedClose());
            pm.put("volume", p.getVolume());
            priceOutput.add(pm);
        }

        return ResponseEntity.ok(Map.of("ticker", ticker, "prices", priceOutput));
    }

    @GetMapping("/dividends")
    public ResponseEntity<?> dividends(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker) {
        List<CorporateAction> dividends = corporateActionRepository.findByTicker(ticker).stream()
                .filter(a -> a.getActionType() == CorporateAction.ActionType.DIVIDEND)
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .toList();

        List<Map<String, Object>> dividendOutput = new ArrayList<>();
        for (CorporateAction action : dividends) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", action.getEffectiveDate().toString());
            row.put("rawValue", action.getRawDividend() != null ? action.getRawDividend() : action.getRatio());
            row.put("adjustedValue", action.getAdjustedDividend() != null ? action.getAdjustedDividend() : action.getRatio());
            row.put("value", action.getAdjustedDividend() != null ? action.getAdjustedDividend() : action.getRatio());
            row.put("source", action.getSourceType() != null ? action.getSourceType().name() : null);
            row.put("formType", action.getFormType());
            row.put("accessionNumber", action.getAccessionNumber());
            row.put("recordDate", action.getRecordDate() != null ? action.getRecordDate().toString() : null);
            row.put("payDate", action.getPayDate() != null ? action.getPayDate().toString() : null);
            row.put("confidenceScore", action.getConfidenceScore());
            dividendOutput.add(row);
        }

        return ResponseEntity.ok(Map.of("ticker", ticker, "dividends", dividendOutput));
    }

    @GetMapping("/splits")
    public ResponseEntity<?> splits(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker) {
        List<CorporateAction> splits = corporateActionRepository.findByTicker(ticker).stream()
                .filter(a -> a.getActionType() == CorporateAction.ActionType.SPLIT)
                .sorted(Comparator.comparing(CorporateAction::getEffectiveDate))
                .toList();

        List<Map<String, Object>> splitOutput = new ArrayList<>();
        for (CorporateAction action : splits) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", action.getEffectiveDate().toString());
            row.put("ratio", action.getRatio());
            row.put("value", action.getRatio());
            row.put("source", action.getSourceType() != null ? action.getSourceType().name() : null);
            row.put("formType", action.getFormType());
            row.put("accessionNumber", action.getAccessionNumber());
            row.put("confidenceScore", action.getConfidenceScore());
            splitOutput.add(row);
        }

        return ResponseEntity.ok(Map.of("ticker", ticker, "splits", splitOutput));
    }

    @GetMapping("/admin/load-prices")
    public ResponseEntity<?> loadPrices(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        try {
            long startTime = System.currentTimeMillis();
            Path dataDir = Paths.get(iexDataDir);
            Map<String, Object> result = priceService.loadAllCsvFiles(dataDir);
            long elapsed = System.currentTimeMillis() - startTime;
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            String duration = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "." + (elapsed % 1000) / 100 + "s";
            String message = "Loaded " + result.get("filesLoaded") + " files with "
                    + result.get("recordsLoaded") + " price records in " + duration + ".";
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Error loading price CSVs", e);
            return ResponseEntity.internalServerError().body("Error loading prices: " + e.getMessage());
        }
    }

    @GetMapping("/admin/load-hist")
    public ResponseEntity<?> loadHist(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam(defaultValue = "252") int days) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result = iexHistService.loadHistData(days);
            long elapsed = System.currentTimeMillis() - startTime;
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            String duration = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "." + (elapsed % 1000) / 100 + "s";
            return ResponseEntity.ok(Map.of(
                    "message", "IEX HIST load complete in " + duration,
                    "processed", result.get("processed"),
                    "skipped", result.get("skipped"),
                    "notAvailable", result.get("notAvailable"),
                    "errors", result.get("errors")
            ));
        } catch (Exception e) {
            log.error("Error loading IEX HIST data", e);
            return ResponseEntity.internalServerError().body("Error loading IEX HIST data: " + e.getMessage());
        }
    }

    @GetMapping("/admin/adjust-prices")
    public ResponseEntity<?> adjustPrices(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam(required = false) String ticker,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean etfOnly,
            @RequestParam(defaultValue = "false") boolean equityOnly,
            @RequestParam(defaultValue = "70") int minConfidence,
            @RequestParam(defaultValue = "false") boolean validateWithYfinance) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result;
            if (ticker != null && !ticker.isBlank()) {
                result = priceAdjustmentService.adjustTicker(
                        ticker,
                        force,
                        etfOnly,
                        equityOnly,
                        minConfidence,
                        validateWithYfinance);
            } else {
                result = priceAdjustmentService.adjustAllTickers(force, etfOnly, equityOnly, minConfidence, validateWithYfinance);
            }
            long elapsed = System.currentTimeMillis() - startTime;
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            String duration = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "." + (elapsed % 1000) / 100 + "s";
            Map<String, Object> response = new LinkedHashMap<>(result);
            response.put("duration", duration);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error adjusting prices", e);
            return ResponseEntity.internalServerError().body("Error adjusting prices: " + e.getMessage());
        }
    }

    @GetMapping("/admin/summarize-filings")
    public ResponseEntity<?> summarizeFilings(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam(required = false) String ticker) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result;
            if (ticker != null && !ticker.isBlank()) {
                Asset asset = assetRepository.findByListings_Ticker(ticker.toUpperCase());
                if (asset == null) {
                    return ResponseEntity.notFound().build();
                }
                result = filingSummaryService.summarizeTicker(ticker.toUpperCase(), asset);
            } else {
                result = filingSummaryService.summarizeAll();
            }
            long elapsed = System.currentTimeMillis() - startTime;
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            String duration = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "." + (elapsed % 1000) / 100 + "s";
            Map<String, Object> response = new LinkedHashMap<>(result);
            response.put("duration", duration);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error summarizing filings", e);
            return ResponseEntity.internalServerError().body("Error summarizing filings: " + e.getMessage());
        }
    }

    @GetMapping("/filing-summaries")
    public ResponseEntity<?> filingSummaries(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker) {
        List<FilingSummary> summaries = filingSummaryRepository.findByTickerOrderByFilingDateDesc(ticker);
        List<Map<String, Object>> output = new ArrayList<>();
        for (FilingSummary fs : summaries) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("filingDate", fs.getFilingDate().toString());
            entry.put("accessionNumber", fs.getAccessionNumber());
            entry.put("summary", fs.getSummary());
            output.add(entry);
        }
        return ResponseEntity.ok(Map.of("ticker", ticker, "summaries", output));
    }

    private String formatNumber(Object val) {
        if (val == null)
            return "0.00";
        if (val instanceof Number) {
            return String.format("%.2f", ((Number) val).doubleValue());
        }
        return val.toString();
    }

    private String formatPercent(Object val) {
        if (val == null)
            return "N/A";
        return String.format("%.2f%%", (Double) val * 100);
    }

    private Long parseCik(String cikValue) {
        if (cikValue == null || cikValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cikValue.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<SecTickerRow> parseSecMutualFundTickers(String rawJson) {
        List<SecTickerRow> rows = new ArrayList<>();
        if (rawJson == null || rawJson.isBlank()) {
            return rows;
        }
        try {
            JsonNode root = mapper.readTree(rawJson);
            if (root == null) {
                return rows;
            }
            for (JsonNode row : extractSecMutualFundRows(root)) {
                String ticker = firstText(row, "ticker", "symbol", "class_ticker", "classTicker");
                Long cik = parseCik(firstText(row, "cik", "cik_str"));
                if (ticker == null || ticker.isBlank() || cik == null) {
                    continue;
                }
                String title = firstText(row, "title", "series_title", "class_title");
                if (title == null || title.isBlank()) {
                    String seriesName = firstText(row, "seriesName", "series_name");
                    String className = firstText(row, "className", "class_name");
                    if (seriesName != null && className != null) {
                        title = seriesName + " - " + className;
                    } else if (seriesName != null) {
                        title = seriesName;
                    } else if (className != null) {
                        title = className;
                    }
                }
                rows.add(new SecTickerRow(ticker, cik, title, true));
            }
        } catch (Exception e) {
            log.warn("Unable to parse SEC mutual fund ticker index: {}", e.getMessage());
        }
        return rows;
    }

    private List<JsonNode> extractSecMutualFundRows(JsonNode root) {
        List<JsonNode> rows = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(rows::add);
            return rows;
        }
        if (!root.isObject()) {
            return rows;
        }

        JsonNode fieldsNode = root.get("fields");
        JsonNode dataNode = root.get("data");
        if (fieldsNode != null && fieldsNode.isArray() && dataNode != null && dataNode.isArray()) {
            List<String> fields = new ArrayList<>();
            for (JsonNode fieldNode : fieldsNode) {
                String field = fieldNode.asText(null);
                fields.add(field == null ? "" : field);
            }
            for (JsonNode dataRow : dataNode) {
                if (dataRow.isObject()) {
                    rows.add(dataRow);
                    continue;
                }
                if (!dataRow.isArray()) {
                    continue;
                }
                com.fasterxml.jackson.databind.node.ObjectNode mappedRow = mapper.createObjectNode();
                int max = Math.min(fields.size(), dataRow.size());
                for (int i = 0; i < max; i++) {
                    String fieldName = fields.get(i);
                    if (fieldName == null || fieldName.isBlank()) {
                        continue;
                    }
                    mappedRow.set(fieldName, dataRow.get(i));
                }
                rows.add(mappedRow);
            }
            return rows;
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = root.fields();
        while (iterator.hasNext()) {
            rows.add(iterator.next().getValue());
        }
        return rows;
    }

    private String firstText(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = value.asText();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private record SecTickerRow(String ticker, Long cik, String title, boolean isFund) {
    }
/*
    @GetMapping("/admin/quarters")
    public ResponseEntity<?> financials(@org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        List<Asset> assets = assetRepository.findByIsFund(false);
        List<String> errors = Collections.synchronizedList(new ArrayList<String>());

        assets.parallelStream().forEach(asset -> {
            try {
                edgarService.updateFinancials(asset);
            } catch (Exception e) {
                errors.add("cik:" + asset.getCik() + " error: " + e.getMessage());
            }
        });
        int fundsSkipped = assetRepository.findByIsFund(true).size();
        String summary = "Processed " + assets.size() + " equities. Skipped " + fundsSkipped + " ETFs/funds.";
        return ResponseEntity.ok(errors.isEmpty() ? summary : summary + "\nErrors:\n" + String.join("\n", errors));
    }
*/
    @GetMapping("/admin/sync-frames")
    public ResponseEntity<?> syncFrames(@org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> report = edgarService.syncFramesFull();
            long elapsed = System.currentTimeMillis() - startTime;
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            String duration = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "." + (elapsed % 1000) / 100 + "s";
            String message = "Synced " + report.get("equitiesProcessed") + " equities (all frames since 2009)"
                    + ". Skipped " + report.get("fundsSkipped") + " ETFs/funds. Completed in " + duration + ".";
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Error syncing frames", e);
            return ResponseEntity.internalServerError().body("Error syncing frames: " + e.getMessage());
        }
    }
}

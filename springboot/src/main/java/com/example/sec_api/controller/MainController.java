package com.example.sec_api.controller;

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
import com.example.sec_api.service.WebService;
import com.example.sec_api.model.Asset;
import com.example.sec_api.model.DailyPrice;
import com.example.sec_api.repository.AssetRepository;
import com.example.sec_api.service.AssetService;
import com.example.sec_api.model.Listing;
import com.example.sec_api.service.ListingService;
import com.example.sec_api.model.Quarter;
import com.example.sec_api.repository.QuarterRepository;
import com.example.sec_api.service.EdgarService;
import com.example.sec_api.service.FinancialService;
import com.example.sec_api.service.PriceService;
import com.example.sec_api.service.PriceAdjustmentService;
import com.example.sec_api.service.IexHistService;
import com.example.sec_api.service.FilingSummaryService;
import com.example.sec_api.model.FilingSummary;
import com.example.sec_api.repository.FilingSummaryRepository;
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
    private final FilingSummaryService filingSummaryService;
    private final FilingSummaryRepository filingSummaryRepository;
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
            IexHistService iexHistService, FilingSummaryService filingSummaryService,
            FilingSummaryRepository filingSummaryRepository) {
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
        this.filingSummaryService = filingSummaryService;
        this.filingSummaryRepository = filingSummaryRepository;
    }

    @GetMapping("/admin/load")
    public ResponseEntity<?> load(@org.springframework.web.bind.annotation.RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        // create dictionary mapping tickers to fund type (equity or fund)
        // nasdaq list first, then others
        Map<String, Boolean> tickerToType = new HashMap<>();
        String csv = webService.fetchNasdaqData("https://www.nasdaqtrader.com/dynamic/symdir/nasdaqlisted.txt");
        String[] rows = csv.split("\\r?\\n");
        for (String row : rows) {
            String[] columns = row.split("\\|");
            if (columns.length > 6) {
                Boolean isFund = "Y".equals(columns[6]);
                tickerToType.put(columns[0], isFund);
            } else {
                log.warn("Skipping malformed NASDAQ row: {}", row);
            }
        }

        csv = webService.fetchNasdaqData("https://www.nasdaqtrader.com/dynamic/symdir/otherlisted.txt");
        rows = csv.split("\\r?\\n");
        for (String row : rows) {
            String[] columns = row.split("\\|");
            if (columns.length > 6) {
                Boolean isFund = "Y".equals(columns[4]);
                tickerToType.put(columns[0], isFund);
            } else {
                log.warn("Skipping malformed other-listed row: {}", row);
            }
        }

        Map<Integer, Map<String, String>> secTickers = webService.fetchSecTickers();
        int equityCount = 0;
        int fundCount = 0;
        for (Map<String, String> secTicker : secTickers.values()) {
            String ticker = secTicker.get("ticker");
            Asset asset = new Asset();
            asset.setCik(Long.parseLong(secTicker.get("cik_str")));
            Boolean isFund = tickerToType.get(ticker);
            if (isFund == null) {
                isFund = false;
            }
            asset.setIsFund(isFund);
            asset = assetService.createOrUpdateAsset(asset);
            Listing listing = new Listing();
            listing.setTicker(ticker);
            listing.setTitle(secTicker.get("title"));
            listing.setAsset(asset);
            listingService.createOrUpdateListing(listing);
            if (isFund) {
                fundCount++;
            } else {
                equityCount++;
            }
        }
        return ResponseEntity.ok("All US tickers loaded: " + equityCount + " equities, " + fundCount + " ETFs/funds.");
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
            @RequestParam(defaultValue = "false") boolean force) {
        if (adminApiKey != null && !adminApiKey.equals(key)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Admin Key");
        }
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result;
            if (ticker != null && !ticker.isBlank()) {
                result = priceAdjustmentService.adjustTicker(ticker);
            } else {
                result = priceAdjustmentService.adjustAllTickers(force);
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

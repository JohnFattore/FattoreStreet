package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.FilingSummary;
import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.model.Quarter;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.FilingSummaryRepository;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;
import com.fattorestreet.sec_api.repository.QuarterRepository;
import com.fattorestreet.sec_api.economic.FredService;
import com.fattorestreet.sec_api.economic.FredService.FredObservation;
import com.fattorestreet.sec_api.fundamentals.FinancialService;
import com.fattorestreet.sec_api.index.IndexMemberApiService;
import com.fattorestreet.sec_api.index.IndexMemberApiService.IndexMemberRow;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
public class PublicController {

    private final AssetRepository assetRepository;
    private final QuarterRepository quarterRepository;
    private final FinancialService financialService;
    private final DailyPriceRepository dailyPriceRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final FilingSummaryRepository filingSummaryRepository;
    private final MarketIndexRepository marketIndexRepository;
    private final IndexMemberApiService indexMemberApiService;
    private final FredService fredService;

    public PublicController(
            AssetRepository assetRepository,
            QuarterRepository quarterRepository,
            FinancialService financialService,
            DailyPriceRepository dailyPriceRepository,
            CorporateActionRepository corporateActionRepository,
            FilingSummaryRepository filingSummaryRepository,
            MarketIndexRepository marketIndexRepository,
            IndexMemberApiService indexMemberApiService,
            FredService fredService
    ) {
        this.assetRepository = assetRepository;
        this.quarterRepository = quarterRepository;
        this.financialService = financialService;
        this.dailyPriceRepository = dailyPriceRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.filingSummaryRepository = filingSummaryRepository;
        this.marketIndexRepository = marketIndexRepository;
        this.indexMemberApiService = indexMemberApiService;
        this.fredService = fredService;
    }

    public record MarketIndexRow(String code, String displayName) {
    }

    public record FredSeriesItem(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z][A-Z0-9]*$", message = "Invalid series id format") String seriesId,
            Boolean computeYoy
    ) {
    }

    @PostMapping("/fred-data")
    public ResponseEntity<?> fredData(@RequestBody List<@Valid FredSeriesItem> series) {
        Map<String, List<FredObservation>> response = new LinkedHashMap<>();
        for (FredSeriesItem item : series) {
            response.put(item.seriesId(), fredService.getSeries(item.seriesId(), Boolean.TRUE.equals(item.computeYoy())));
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/quarters")
    public ResponseEntity<?> quarters(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker
    ) {
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

            qm.put("revenues", q.getRevenues());
            qm.put("netIncomeLoss", q.getNetIncomeLoss());
            qm.put("operatingIncomeLoss", q.getOperatingIncomeLoss());
            qm.put("grossProfit", q.getGrossProfit());
            qm.put("epsBasic", q.getEarningsPerShareBasic());
            qm.put("epsDiluted", q.getEarningsPerShareDiluted());

            qm.put("assets", q.getAssets());
            qm.put("liabilities", q.getLiabilities());
            qm.put("equity", q.getStockholdersEquity());
            qm.put("cash", q.getCashAndCashEquivalentsAtCarryingValue());
            qm.put("receivables", q.getAccountsReceivableNetCurrent());
            qm.put("inventory", q.getInventoryNet());

            qm.put("ocf", q.getNetCashProvidedByUsedInOperatingActivities());
            qm.put("dividends", q.getPaymentsOfDividends());
            qm.put("buybacks", q.getPaymentsForRepurchaseOfCommonStock());

            quarterOutput.add(qm);
        }
        Map<String, Object> response = Map.of(
                "ticker", ticker,
                "cik", asset.getCik().toString(),
                "quarters", quarterOutput
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/company-fact-sheet")
    public ResponseEntity<?> companyFactSheet(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker
    ) {
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

            response.put("latestAssets", formatNumber(metrics.get("latestAssets")));
            response.put("latestLiabilities", formatNumber(metrics.get("latestLiabilities")));
            response.put("latestEquity", formatNumber(metrics.get("latestEquity")));
            response.put("latestInventory", formatNumber(metrics.get("latestInventory")));
            response.put("latestCash", formatNumber(metrics.get("latestCash")));
            response.put("latestEps", formatNumber(metrics.get("latestEps")));

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
            @RequestParam(required = false) String end
    ) {
        List<DailyPrice> prices;
        if (start != null && end != null) {
            prices = dailyPriceRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(ticker, LocalDate.parse(start), LocalDate.parse(end));
        } else if (start != null) {
            prices = dailyPriceRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(ticker, LocalDate.parse(start), LocalDate.now());
        } else {
            prices = dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
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
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker
    ) {
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
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker
    ) {
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

    @GetMapping("/filing-summaries")
    public ResponseEntity<?> filingSummaries(
            @RequestParam @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9.\\-]*$", message = "Invalid ticker format") String ticker
    ) {
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

    @GetMapping("/indexes")
    public List<MarketIndexRow> listIndexes() {
        return marketIndexRepository.findAll().stream()
                .sorted(Comparator.comparing(mi -> mi.getCode() != null ? mi.getCode() : ""))
                .map(mi -> new MarketIndexRow(mi.getCode(), mi.getDisplayName()))
                .toList();
    }

    @GetMapping("/index-members")
    public List<IndexMemberRow> listIndexMembers(@RequestParam(required = false) String code) {
        if (code != null && !code.isBlank()) {
            return indexMemberApiService.listByIndexCode(code.trim());
        }
        return indexMemberApiService.listAll();
    }

    private String formatNumber(Object val) {
        if (val == null) {
            return "0.00";
        }
        if (val instanceof Number) {
            return String.format("%.2f", ((Number) val).doubleValue());
        }
        return val.toString();
    }

    private String formatPercent(Object val) {
        if (val == null) {
            return "N/A";
        }
        return String.format("%.2f%%", (Double) val * 100);
    }
}

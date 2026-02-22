package com.example.sec_api.controller;

import com.example.sec_api.model.Asset;
import com.example.sec_api.model.DailyPrice;
import com.example.sec_api.model.FilingSummary;
import com.example.sec_api.model.Quarter;
import com.example.sec_api.repository.AssetRepository;
import com.example.sec_api.repository.FilingSummaryRepository;
import com.example.sec_api.repository.QuarterRepository;
import com.example.sec_api.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MainController.class)
class MainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private WebService webService;
    @MockitoBean private AssetService assetService;
    @MockitoBean private AssetRepository assetRepository;
    @MockitoBean private ListingService listingService;
    @MockitoBean private QuarterRepository quarterRepository;
    @MockitoBean private EdgarService edgarService;
    @MockitoBean private FinancialService financialService;
    @MockitoBean private PriceService priceService;
    @MockitoBean private PriceAdjustmentService priceAdjustmentService;
    @MockitoBean private IexHistService iexHistService;
    @MockitoBean private FilingSummaryService filingSummaryService;
    @MockitoBean private FilingSummaryRepository filingSummaryRepository;

    private Asset buildAsset(Long cik) {
        Asset a = new Asset();
        a.setId(1L);
        a.setCik(cik);
        return a;
    }

    private Quarter buildQuarter(Asset asset, int year, int qtr, LocalDate end, Long revenue, Long netIncome) {
        Quarter q = new Quarter();
        q.setAsset(asset);
        q.setYear(year);
        q.setQuarter(qtr);
        q.setPeriodStart(end.minusMonths(3));
        q.setPeriodEnd(end);
        q.setRevenues(revenue);
        q.setNetIncomeLoss(netIncome);
        q.setAssets(500L);
        q.setLiabilities(300L);
        q.setStockholdersEquity(200L);
        q.setCashAndCashEquivalentsAtCarryingValue(80L);
        q.setInventoryNet(40L);
        q.setEarningsPerShareBasic(1.50);
        return q;
    }

    // --- /quarters endpoint ---

    @Test
    void quarters_validTicker_returns200WithData() throws Exception {
        Asset asset = buildAsset(320193L);
        Quarter q = buildQuarter(asset, 2024, 4, LocalDate.of(2024, 12, 31), 100L, 25L);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(quarterRepository.findByAsset(asset)).thenReturn(List.of(q));

        mockMvc.perform(get("/quarters").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.cik").value("320193"))
                .andExpect(jsonPath("$.quarters").isArray())
                .andExpect(jsonPath("$.quarters[0].year").value(2024))
                .andExpect(jsonPath("$.quarters[0].revenues").value(100));
    }

    @Test
    void quarters_unknownTicker_returns404() throws Exception {
        when(assetRepository.findByListings_Ticker("ZZZZ")).thenReturn(null);

        mockMvc.perform(get("/quarters").param("ticker", "ZZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void quarters_invalidTickerFormat_throwsConstraintViolation() {
        assertThrows(jakarta.servlet.ServletException.class, () ->
                mockMvc.perform(get("/quarters").param("ticker", "aapl"))
        );
    }

    @Test
    void quarters_missingTicker_returns400() throws Exception {
        mockMvc.perform(get("/quarters"))
                .andExpect(status().isBadRequest());
    }

    // --- /company-fact-sheet endpoint ---

    @Test
    void companyFactSheet_validTicker_returns200WithMetrics() throws Exception {
        Asset asset = buildAsset(320193L);
        Quarter q = buildQuarter(asset, 2024, 4, LocalDate.of(2024, 12, 31), 100L, 25L);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("ttmNetIncome", 78.0);
        metrics.put("ttmRevenue", 355.0);
        metrics.put("ttmOperatingCashFlow", 105.0);
        metrics.put("ttmOperatingIncome", 118.0);
        metrics.put("ttmGrossProfit", 177.0);
        metrics.put("ttmNetIncomeYoY", 0.42);
        metrics.put("ttmRevenueYoY", 0.12);
        metrics.put("latestAssets", 500L);
        metrics.put("latestLiabilities", 300L);
        metrics.put("latestEquity", 200L);
        metrics.put("latestInventory", 40L);
        metrics.put("latestCash", 80L);
        metrics.put("latestEps", 1.50);
        metrics.put("netMargin", 0.2197);
        metrics.put("grossMargin", 0.4986);
        metrics.put("debtToAssets", 0.60);
        metrics.put("cashToLiabilities", 0.2667);
        metrics.put("roA", 0.156);
        metrics.put("ocfToNetIncome", 1.346);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(quarterRepository.findByAsset(asset)).thenReturn(List.of(q));
        when(financialService.calculateMetrics(anyList())).thenReturn(metrics);

        mockMvc.perform(get("/company-fact-sheet").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.cik").value("320193"))
                .andExpect(jsonPath("$.ttmRevenue").exists())
                .andExpect(jsonPath("$.ttmNetIncome").exists())
                .andExpect(jsonPath("$.netMargin").exists())
                .andExpect(jsonPath("$.latestQuarterEnd").value("2024-12-31"));
    }

    @Test
    void companyFactSheet_unknownTicker_returns404() throws Exception {
        when(assetRepository.findByListings_Ticker("ZZZZ")).thenReturn(null);

        mockMvc.perform(get("/company-fact-sheet").param("ticker", "ZZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void companyFactSheet_emptyQuarters_returnsMinimalResponse() throws Exception {
        Asset asset = buildAsset(320193L);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(quarterRepository.findByAsset(asset)).thenReturn(Collections.emptyList());
        when(financialService.calculateMetrics(anyList())).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/company-fact-sheet").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.cik").value("320193"))
                .andExpect(jsonPath("$.ttmRevenue").doesNotExist());
    }

    // --- Admin endpoints ---

    @Test
    void adminLoad_validKey_returns200() throws Exception {
        when(webService.fetchNasdaqData(anyString())).thenReturn("Symbol|Name|Market|Test|Test|Test|N\n");
        when(webService.fetchSecTickers()).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/admin/load").header("X-Admin-Key", "spike"))
                .andExpect(status().isOk());
    }

    @Test
    void adminLoad_invalidKey_returns401() throws Exception {
        mockMvc.perform(get("/admin/load").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminSyncFrames_validKey_returns200() throws Exception {
        when(edgarService.syncFramesFull()).thenReturn(
                Map.of("equitiesProcessed", 100, "fundsSkipped", 50));

        mockMvc.perform(get("/admin/sync-frames").header("X-Admin-Key", "spike"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Synced 100 equities")));
    }

    @Test
    void adminSyncFrames_invalidKey_returns401() throws Exception {
        mockMvc.perform(get("/admin/sync-frames").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    // --- /prices endpoint ---

    @Test
    void prices_validTicker_returns200WithData() throws Exception {
        DailyPrice dp = new DailyPrice();
        dp.setTicker("AAPL");
        dp.setTradeDate(LocalDate.of(2025, 3, 15));
        dp.setOpenPrice(172.5);
        dp.setHighPrice(174.2);
        dp.setLowPrice(171.8);
        dp.setClosePrice(173.9);
        dp.setAdjustedOpen(171.95);
        dp.setAdjustedHigh(173.65);
        dp.setAdjustedLow(171.25);
        dp.setAdjustedClose(173.35);
        dp.setVolume(45230L);

        when(priceService.getPrices("AAPL")).thenReturn(List.of(dp));

        mockMvc.perform(get("/prices").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.prices").isArray())
                .andExpect(jsonPath("$.prices[0].date").value("2025-03-15"))
                .andExpect(jsonPath("$.prices[0].open").value(172.5))
                .andExpect(jsonPath("$.prices[0].close").value(173.9))
                .andExpect(jsonPath("$.prices[0].adjustedOpen").value(171.95))
                .andExpect(jsonPath("$.prices[0].adjustedClose").value(173.35))
                .andExpect(jsonPath("$.prices[0].volume").value(45230));
    }

    @Test
    void prices_withDateRange_returns200() throws Exception {
        when(priceService.getPrices(
                org.mockito.ArgumentMatchers.eq("AAPL"),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/prices")
                        .param("ticker", "AAPL")
                        .param("start", "2025-01-01")
                        .param("end", "2025-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.prices").isArray());
    }

    @Test
    void prices_missingTicker_returns400() throws Exception {
        mockMvc.perform(get("/prices"))
                .andExpect(status().isBadRequest());
    }

    // --- /admin/load-prices endpoint ---

    @Test
    void adminLoadPrices_validKey_returns200() throws Exception {
        when(priceService.loadAllCsvFiles(any())).thenReturn(
                Map.of("filesLoaded", 5, "recordsLoaded", 12000));

        mockMvc.perform(get("/admin/load-prices").header("X-Admin-Key", "spike"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Loaded 5 files")))
                .andExpect(content().string(containsString("12000 price records")));
    }

    @Test
    void adminLoadPrices_invalidKey_returns401() throws Exception {
        mockMvc.perform(get("/admin/load-prices").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    // --- /admin/load-hist endpoint ---

    @Test
    void adminLoadHist_validKey_returns200() throws Exception {
        when(iexHistService.loadHistData(anyInt())).thenReturn(
                Map.of("processed", 5, "skipped", 0, "notAvailable", 0, "errors", 0));

        mockMvc.perform(get("/admin/load-hist").header("X-Admin-Key", "spike"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(5));
    }

    @Test
    void adminLoadHist_invalidKey_returns401() throws Exception {
        mockMvc.perform(get("/admin/load-hist").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoadHist_customDays_passes() throws Exception {
        when(iexHistService.loadHistData(30)).thenReturn(
                Map.of("processed", 3, "skipped", 27, "notAvailable", 0, "errors", 0));

        mockMvc.perform(get("/admin/load-hist")
                        .header("X-Admin-Key", "spike")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.skipped").value(27));
    }

    // --- /admin/adjust-prices endpoint ---

    @Test
    void adminAdjustPrices_validKey_returns200() throws Exception {
        when(priceAdjustmentService.adjustAllTickers(false)).thenReturn(
                Map.of("tickersProcessed", 10, "skippedNoAsset", 5,
                       "totalSplits", 2, "totalDividends", 8, "totalPricesUpdated", 500));

        mockMvc.perform(get("/admin/adjust-prices").header("X-Admin-Key", "spike"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickersProcessed").value(10))
                .andExpect(jsonPath("$.totalSplits").value(2))
                .andExpect(jsonPath("$.totalDividends").value(8));
    }

    @Test
    void adminAdjustPrices_forceTrue_passesForceParam() throws Exception {
        when(priceAdjustmentService.adjustAllTickers(true)).thenReturn(
                Map.of("tickersProcessed", 50, "skippedNoAsset", 0,
                       "totalSplits", 5, "totalDividends", 20, "totalPricesUpdated", 5000));

        mockMvc.perform(get("/admin/adjust-prices")
                        .header("X-Admin-Key", "spike")
                        .param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickersProcessed").value(50));
    }

    @Test
    void adminAdjustPrices_singleTicker_returns200() throws Exception {
        when(priceAdjustmentService.adjustTicker("AAPL")).thenReturn(
                Map.of("ticker", "AAPL", "status", "ok", "newActions", 1,
                       "splits", 1, "dividends", 4, "pricesUpdated", 252));

        mockMvc.perform(get("/admin/adjust-prices")
                        .header("X-Admin-Key", "spike")
                        .param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.splits").value(1))
                .andExpect(jsonPath("$.pricesUpdated").value(252));
    }

    @Test
    void adminAdjustPrices_invalidKey_returns401() throws Exception {
        mockMvc.perform(get("/admin/adjust-prices").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    // --- /admin/summarize-filings endpoint ---

    @Test
    void adminSummarizeFilings_validKey_returns200() throws Exception {
        when(filingSummaryService.summarizeAll()).thenReturn(
                Map.of("filingsSummarized", 5, "assetsWithNoFilings", 10, "errors", 0));

        mockMvc.perform(get("/admin/summarize-filings").header("X-Admin-Key", "spike"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filingsSummarized").value(5));
    }

    @Test
    void adminSummarizeFilings_singleTicker_returns200() throws Exception {
        Asset asset = buildAsset(320193L);
        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(filingSummaryService.summarizeTicker(org.mockito.ArgumentMatchers.eq("AAPL"), org.mockito.ArgumentMatchers.any(Asset.class))).thenReturn(
                Map.of("ticker", "AAPL", "filingsSummarized", 2));

        mockMvc.perform(get("/admin/summarize-filings")
                        .header("X-Admin-Key", "spike")
                        .param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.filingsSummarized").value(2));
    }

    @Test
    void adminSummarizeFilings_invalidKey_returns401() throws Exception {
        mockMvc.perform(get("/admin/summarize-filings").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    // --- /filing-summaries endpoint ---

    @Test
    void filingSummaries_validTicker_returns200() throws Exception {
        FilingSummary fs = new FilingSummary();
        fs.setTicker("AAPL");
        fs.setFilingDate(java.time.LocalDate.of(2024, 10, 31));
        fs.setAccessionNumber("0000320193-24-000123");
        fs.setSummary("Apple reported strong revenue growth driven by iPhone sales.");

        when(filingSummaryRepository.findByTickerOrderByFilingDateDesc("AAPL"))
                .thenReturn(List.of(fs));

        mockMvc.perform(get("/filing-summaries").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.summaries").isArray())
                .andExpect(jsonPath("$.summaries[0].filingDate").value("2024-10-31"))
                .andExpect(jsonPath("$.summaries[0].summary").value(containsString("strong revenue growth")));
    }

    @Test
    void filingSummaries_missingTicker_returns400() throws Exception {
        mockMvc.perform(get("/filing-summaries"))
                .andExpect(status().isBadRequest());
    }
}

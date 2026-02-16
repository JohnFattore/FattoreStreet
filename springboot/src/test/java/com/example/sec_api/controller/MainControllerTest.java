package com.example.sec_api.controller;

import com.example.sec_api.model.Asset;
import com.example.sec_api.model.Quarter;
import com.example.sec_api.repository.AssetRepository;
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
}

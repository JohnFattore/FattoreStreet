package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.index.IndexMemberApiService;
import com.fattorestreet.sec_api.index.IndexMemberApiService.IndexMemberRow;
import com.fattorestreet.sec_api.index.IndexMemberApiService.StockRow;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.FilingSummary;
import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.model.Quarter;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.FilingSummaryRepository;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;
import com.fattorestreet.sec_api.repository.QuarterRepository;
import com.fattorestreet.sec_api.fundamentals.FinancialService;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import org.junit.jupiter.api.Test;
import com.fattorestreet.sec_api.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "SECRET_KEY=test-jwt-signing-secret-32chars-min!") // pragma: allowlist secret
class PublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AssetRepository assetRepository;
    @MockitoBean private QuarterRepository quarterRepository;
    @MockitoBean private FinancialService financialService;
    @MockitoBean private DailyPriceRepository dailyPriceRepository;
    @MockitoBean private CorporateActionRepository corporateActionRepository;
    @MockitoBean private FilingSummaryRepository filingSummaryRepository;
    @MockitoBean private MarketIndexRepository marketIndexRepository;
    @MockitoBean private IndexMemberApiService indexMemberApiService;

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

        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL")).thenReturn(List.of(dp));

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
        when(dailyPriceRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(
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

    // --- /dividends endpoint ---

    @Test
    void dividends_validTicker_returns200WithData() throws Exception {
        CorporateAction d1 = new CorporateAction();
        d1.setTicker("AAPL");
        d1.setActionType(ActionType.DIVIDEND);
        d1.setEffectiveDate(LocalDate.of(2025, 2, 10));
        d1.setRatio(0.25);
        d1.setRawDividend(0.25);
        d1.setAdjustedDividend(0.25);
        d1.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
        d1.setFormType("8-K");
        d1.setAccessionNumber("0000320193-25-000010");
        d1.setRecordDate(LocalDate.of(2025, 2, 10));
        d1.setPayDate(LocalDate.of(2025, 2, 13));
        d1.setConfidenceScore(87.0);

        CorporateAction d2 = new CorporateAction();
        d2.setTicker("AAPL");
        d2.setActionType(ActionType.DIVIDEND);
        d2.setEffectiveDate(LocalDate.of(2025, 5, 12));
        d2.setRatio(0.26);
        d2.setRawDividend(0.065);
        d2.setAdjustedDividend(0.26);

        CorporateAction split = new CorporateAction();
        split.setTicker("AAPL");
        split.setActionType(ActionType.SPLIT);
        split.setEffectiveDate(LocalDate.of(2020, 8, 31));
        split.setRatio(0.25);

        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(d2, split, d1));

        mockMvc.perform(get("/dividends").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.dividends").isArray())
                .andExpect(jsonPath("$.dividends", hasSize(2)))
                .andExpect(jsonPath("$.dividends[0].date").value("2025-02-10"))
                .andExpect(jsonPath("$.dividends[0].rawValue").value(0.25))
                .andExpect(jsonPath("$.dividends[0].adjustedValue").value(0.25))
                .andExpect(jsonPath("$.dividends[0].value").value(0.25))
                .andExpect(jsonPath("$.dividends[0].source").value("SEC_EQUITY_XBRL"))
                .andExpect(jsonPath("$.dividends[0].formType").value("8-K"))
                .andExpect(jsonPath("$.dividends[0].accessionNumber").value("0000320193-25-000010"))
                .andExpect(jsonPath("$.dividends[0].recordDate").value("2025-02-10"))
                .andExpect(jsonPath("$.dividends[0].payDate").value("2025-02-13"))
                .andExpect(jsonPath("$.dividends[0].confidenceScore").value(87.0))
                .andExpect(jsonPath("$.dividends[1].date").value("2025-05-12"))
                .andExpect(jsonPath("$.dividends[1].rawValue").value(0.065))
                .andExpect(jsonPath("$.dividends[1].adjustedValue").value(0.26))
                .andExpect(jsonPath("$.dividends[1].value").value(0.26));
    }

    @Test
    void dividends_missingTicker_returns400() throws Exception {
        mockMvc.perform(get("/dividends"))
                .andExpect(status().isBadRequest());
    }

    // --- /splits endpoint ---

    @Test
    void splits_validTicker_returns200WithData() throws Exception {
        CorporateAction s1 = new CorporateAction();
        s1.setTicker("AAPL");
        s1.setActionType(ActionType.SPLIT);
        s1.setEffectiveDate(LocalDate.of(2014, 6, 9));
        s1.setRatio(0.1428571429);
        s1.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
        s1.setFormType("8-K");
        s1.setAccessionNumber("0000320193-14-000070");
        s1.setConfidenceScore(90.0);

        CorporateAction s2 = new CorporateAction();
        s2.setTicker("AAPL");
        s2.setActionType(ActionType.SPLIT);
        s2.setEffectiveDate(LocalDate.of(2020, 8, 31));
        s2.setRatio(0.25);
        s2.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
        s2.setFormType("8-K");
        s2.setAccessionNumber("0000320193-20-000096");
        s2.setConfidenceScore(93.0);

        CorporateAction dividend = new CorporateAction();
        dividend.setTicker("AAPL");
        dividend.setActionType(ActionType.DIVIDEND);
        dividend.setEffectiveDate(LocalDate.of(2025, 2, 10));
        dividend.setRatio(0.25);

        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(dividend, s2, s1));

        mockMvc.perform(get("/splits").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.splits").isArray())
                .andExpect(jsonPath("$.splits", hasSize(2)))
                .andExpect(jsonPath("$.splits[0].date").value("2014-06-09"))
                .andExpect(jsonPath("$.splits[0].ratio").value(0.1428571429))
                .andExpect(jsonPath("$.splits[0].value").value(0.1428571429))
                .andExpect(jsonPath("$.splits[1].date").value("2020-08-31"))
                .andExpect(jsonPath("$.splits[1].ratio").value(0.25))
                .andExpect(jsonPath("$.splits[1].value").value(0.25));
    }

    @Test
    void splits_missingTicker_returns400() throws Exception {
        mockMvc.perform(get("/splits"))
                .andExpect(status().isBadRequest());
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

    // --- /indexes endpoint ---

    @Test
    void listIndexes_returns200() throws Exception {
        MarketIndex fat50 = new MarketIndex();
        fat50.setCode("FAT50");
        fat50.setDisplayName("Fattore 50");
        MarketIndex fat100 = new MarketIndex();
        fat100.setCode("FAT100");
        fat100.setDisplayName("Fattore 100");
        MarketIndex fat1000 = new MarketIndex();
        fat1000.setCode("FAT1000");
        fat1000.setDisplayName("Fattore 1000");
        when(marketIndexRepository.findAll()).thenReturn(List.of(fat50, fat100, fat1000));

        mockMvc.perform(get("/indexes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("FAT100"))
                .andExpect(jsonPath("$[0].displayName").value("Fattore 100"))
                .andExpect(jsonPath("$[1].code").value("FAT1000"))
                .andExpect(jsonPath("$[1].displayName").value("Fattore 1000"))
                .andExpect(jsonPath("$[2].code").value("FAT50"))
                .andExpect(jsonPath("$[2].displayName").value("Fattore 50"));
    }

    // --- /index-members endpoint ---

    @Test
    void listIndexMembers_returns200() throws Exception {
        StockRow stock = new StockRow(
                "AAPL", "Apple", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ONE, "US", "United States", "DE", "CA", "Common Stock", 1980);
        when(indexMemberApiService.listAll()).thenReturn(List.of(
                new IndexMemberRow(1L, new BigDecimal("5.5"), "Test Index", false, "", stock)
        ));
        mockMvc.perform(get("/index-members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].index").value("Test Index"))
                .andExpect(jsonPath("$[0].stock.ticker").value("AAPL"));
    }

    @Test
    void listIndexMembers_withCode_filters() throws Exception {
        StockRow stock = new StockRow(
                "MSFT", "Microsoft", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ONE, "US", "United States", "WA", "WA", "Common Stock", 1986);
        when(indexMemberApiService.listByIndexCode("FAT50")).thenReturn(List.of(
                new IndexMemberRow(2L, new BigDecimal("3.25"), "Fattore 50", false, "", stock)
        ));
        mockMvc.perform(get("/index-members").param("code", "FAT50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].index").value("Fattore 50"))
                .andExpect(jsonPath("$[0].stock.ticker").value("MSFT"));
    }
}

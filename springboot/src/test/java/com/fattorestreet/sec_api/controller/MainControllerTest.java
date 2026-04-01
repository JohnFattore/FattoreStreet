package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.FilingSummary;
import com.fattorestreet.sec_api.model.Quarter;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.FilingSummaryRepository;
import com.fattorestreet.sec_api.repository.QuarterRepository;
import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.filing.FilingSummaryService;
import com.fattorestreet.sec_api.fundamentals.EdgarService;
import com.fattorestreet.sec_api.fundamentals.FinancialService;
import com.fattorestreet.sec_api.listing.AssetService;
import com.fattorestreet.sec_api.listing.EtfIdentityService;
import com.fattorestreet.sec_api.listing.ListingService;
import com.fattorestreet.sec_api.marketdata.IexHistService;
import com.fattorestreet.sec_api.marketdata.PriceService;
import com.fattorestreet.sec_api.corporateaction.PriceAdjustmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fattorestreet.sec_api.config.SecurityConfig;
import com.fattorestreet.sec_api.testsupport.TestJwtTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminController.class, PublicController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "SECRET_KEY=test-jwt-signing-secret-32chars-min!") // pragma: allowlist secret
class MainControllerTest {

    private static final String JWT_TEST_SECRET = "test-jwt-signing-secret-32chars-min!"; // pragma: allowlist secret

    private static String bearerAdmin() {
        return "Bearer " + TestJwtTokens.accessToken(JWT_TEST_SECRET, 1L);
    }

    private static String bearerNonAdmin() {
        return "Bearer " + TestJwtTokens.accessToken(JWT_TEST_SECRET, 2L);
    }

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
    @MockitoBean private EtfIdentityService etfIdentityService;
    @MockitoBean private FilingSummaryService filingSummaryService;
    @MockitoBean private FilingSummaryRepository filingSummaryRepository;
    @MockitoBean private CorporateActionRepository corporateActionRepository;
    @MockitoBean private com.fattorestreet.sec_api.index.IndexMetricsRefreshService indexMetricsRefreshService;
    @MockitoBean(name = "fattore50IndexRebuildService")
    private com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore50IndexRebuildService;
    @MockitoBean(name = "fattore100IndexRebuildService")
    private com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore100IndexRebuildService;
    @MockitoBean(name = "fattore1000IndexRebuildService")
    private com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore1000IndexRebuildService;

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
    void adminAssetLoad_validKey_returns200() throws Exception {
        when(webService.fetchSecTickers()).thenReturn(Collections.emptyMap());
        when(webService.fetchSecMutualFundTickers()).thenReturn("{}");
        when(etfIdentityService.enrichFundListingIdentities(false)).thenReturn(
                Map.of("fundListingsTotal", 0, "resolved", 0, "unresolved", 0, "updated", 0, "skipped", 0, "secMfTickerRows", 0, "unresolvedTickersSample", List.of()));

        mockMvc.perform(get("/admin/asset-load").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equitiesLoaded").value(0))
                .andExpect(jsonPath("$.fundsLoaded").value(0));
        verify(etfIdentityService).enrichFundListingIdentities(false);
    }

    @Test
    void adminAssetLoad_mutualFundFallbackKeys_loadsTicker() throws Exception {
        when(webService.fetchSecTickers()).thenReturn(Collections.emptyMap());
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "0": {
                    "class_ticker": "VOO",
                    "cik_str": "0000102909",
                    "series_name": "Vanguard 500 Index Fund",
                    "class_name": "ETF Shares"
                  }
                }
                """);
        when(assetService.createOrUpdateAsset(org.mockito.ArgumentMatchers.any(Asset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(etfIdentityService.enrichFundListingIdentities(false)).thenReturn(
                Map.of("fundListingsTotal", 1, "resolved", 1, "unresolved", 0, "updated", 1, "skipped", 0, "secMfTickerRows", 1, "unresolvedTickersSample", List.of()));

        mockMvc.perform(get("/admin/asset-load").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fundsLoaded").value(1))
                .andExpect(jsonPath("$.etfIdentityEnrichment.resolved").value(1));

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetService, atLeastOnce()).createOrUpdateAsset(assetCaptor.capture());
        Asset loadedAsset = assetCaptor.getAllValues().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
        assertTrue(loadedAsset.getIsFund());
        assertEquals(102909L, loadedAsset.getCik());
    }

    @Test
    void adminAssetLoad_mutualFundTabularShape_loadsTicker() throws Exception {
        when(webService.fetchSecTickers()).thenReturn(Collections.emptyMap());
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "fields": ["cik", "seriesId", "classId", "symbol", "series_name", "class_name"],
                  "data": [
                    ["0000102909", "S000001", "C000001", "VOO", "Vanguard 500 Index Fund", "ETF Shares"]
                  ]
                }
                """);
        when(assetService.createOrUpdateAsset(org.mockito.ArgumentMatchers.any(Asset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(etfIdentityService.enrichFundListingIdentities(false)).thenReturn(
                Map.of("fundListingsTotal", 1, "resolved", 1, "unresolved", 0, "updated", 1, "skipped", 0, "secMfTickerRows", 1, "unresolvedTickersSample", List.of()));

        mockMvc.perform(get("/admin/asset-load").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fundsLoaded").value(1));

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetService, atLeastOnce()).createOrUpdateAsset(assetCaptor.capture());
        Asset loadedAsset = assetCaptor.getAllValues().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
        assertTrue(loadedAsset.getIsFund());
        assertEquals(102909L, loadedAsset.getCik());
    }

    @Test
    void adminAssetLoad_mutualFundFieldsDataObjectRows_loadsTicker() throws Exception {
        when(webService.fetchSecTickers()).thenReturn(Collections.emptyMap());
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "fields": ["cik", "class_ticker", "series_name", "class_name"],
                  "data": [
                    {
                      "cik": "0000102909",
                      "class_ticker": "VOO",
                      "series_name": "Vanguard 500 Index Fund",
                      "class_name": "ETF Shares"
                    }
                  ]
                }
                """);
        when(assetService.createOrUpdateAsset(org.mockito.ArgumentMatchers.any(Asset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(etfIdentityService.enrichFundListingIdentities(false)).thenReturn(
                Map.of("fundListingsTotal", 1, "resolved", 1, "unresolved", 0, "updated", 1, "skipped", 0, "secMfTickerRows", 1, "unresolvedTickersSample", List.of()));

        mockMvc.perform(get("/admin/asset-load").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fundsLoaded").value(1));

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetService, atLeastOnce()).createOrUpdateAsset(assetCaptor.capture());
        Asset loadedAsset = assetCaptor.getAllValues().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
        assertTrue(loadedAsset.getIsFund());
        assertEquals(102909L, loadedAsset.getCik());
    }

    @Test
    void adminAssetLoad_noBearer_returns401() throws Exception {
        mockMvc.perform(get("/admin/asset-load"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAssetLoad_nonAdminUserJwt_returns403() throws Exception {
        mockMvc.perform(get("/admin/asset-load").header(HttpHeaders.AUTHORIZATION, bearerNonAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAssetLoad_overwriteExisting_passesTrueToEnrichment() throws Exception {
        when(webService.fetchSecTickers()).thenReturn(Collections.emptyMap());
        when(webService.fetchSecMutualFundTickers()).thenReturn("{}");
        when(etfIdentityService.enrichFundListingIdentities(true)).thenReturn(
                Map.of("fundListingsTotal", 0, "resolved", 0, "unresolved", 0, "updated", 0, "skipped", 0, "secMfTickerRows", 0, "unresolvedTickersSample", List.of()));

        mockMvc.perform(get("/admin/asset-load")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("overwriteExisting", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etfIdentityOverwriteExisting").value(true));

        verify(etfIdentityService).enrichFundListingIdentities(true);
    }

    @Test
    void adminSyncFrames_validKey_returns200() throws Exception {
        when(edgarService.syncFramesFull()).thenReturn(
                Map.of("equitiesProcessed", 100, "fundsSkipped", 50));

        mockMvc.perform(get("/admin/sync-frames").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Synced 100 equities")));
    }

    @Test
    void adminSyncFrames_noBearer_returns401() throws Exception {
        mockMvc.perform(get("/admin/sync-frames"))
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

    // --- /admin/load-hist endpoint ---

    @Test
    void adminLoadHist_validKey_returns200() throws Exception {
        when(iexHistService.loadHistData(anyInt())).thenReturn(
                Map.of("processed", 5, "skipped", 0, "notAvailable", 0, "errors", 0));

        mockMvc.perform(get("/admin/load-hist").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(5));
    }

    @Test
    void adminLoadHist_noBearer_returns401() throws Exception {
        mockMvc.perform(get("/admin/load-hist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoadHist_customDays_passes() throws Exception {
        when(iexHistService.loadHistData(30)).thenReturn(
                Map.of("processed", 3, "skipped", 27, "notAvailable", 0, "errors", 0));

        mockMvc.perform(get("/admin/load-hist")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.skipped").value(27));
    }

    // --- /admin/adjust-prices endpoint ---

    @Test
    void adminAdjustPrices_validKey_returns200() throws Exception {
        when(priceAdjustmentService.adjustAllTickers(false, false, false, false)).thenReturn(
                Map.of("tickersProcessed", 10, "skippedNoAsset", 5,
                       "totalSplits", 2, "totalDividends", 8, "totalPricesUpdated", 500));

        mockMvc.perform(get("/admin/adjust-prices").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickersProcessed").value(10))
                .andExpect(jsonPath("$.totalSplits").value(2))
                .andExpect(jsonPath("$.totalDividends").value(8));
    }

    @Test
    void adminAdjustPrices_forceTrue_passesForceParam() throws Exception {
        when(priceAdjustmentService.adjustAllTickers(true, false, false, false)).thenReturn(
                Map.of("tickersProcessed", 50, "skippedNoAsset", 0,
                       "totalSplits", 5, "totalDividends", 20, "totalPricesUpdated", 5000));

        mockMvc.perform(get("/admin/adjust-prices")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickersProcessed").value(50));
    }

    @Test
    void adminAdjustPrices_etfOnly_passesFlags() throws Exception {
        when(priceAdjustmentService.adjustAllTickers(false, true, false, false)).thenReturn(
                Map.of("tickersProcessed", 3, "skippedNoAsset", 0,
                        "totalSplits", 0, "totalDividends", 6, "totalPricesUpdated", 756));

        mockMvc.perform(get("/admin/adjust-prices")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("etfOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickersProcessed").value(3));
    }

    @Test
    void adminAdjustPrices_singleTicker_returns200() throws Exception {
        when(priceAdjustmentService.adjustTicker("AAPL", false, false, false, false)).thenReturn(
                Map.of("ticker", "AAPL", "status", "ok", "newActions", 1,
                       "splits", 1, "dividends", 4, "pricesUpdated", 252));

        mockMvc.perform(get("/admin/adjust-prices")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.splits").value(1))
                .andExpect(jsonPath("$.pricesUpdated").value(252));
    }

    @Test
    void adminAdjustPrices_singleTicker_passesEtfFlags() throws Exception {
        when(priceAdjustmentService.adjustTicker("VOO", true, true, false, false)).thenReturn(
                Map.of("ticker", "VOO", "status", "ok", "newActions", 2,
                        "splits", 0, "dividends", 2, "pricesUpdated", 400,
                        "etfDiagnostics", Map.of(
                                "saved", 2,
                                "skipReasons", Map.of("identity_mismatch", 1),
                                "sampleSkips", List.of(Map.of("reason", "identity_mismatch", "accession", "0001")))));

        mockMvc.perform(get("/admin/adjust-prices")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("ticker", "VOO")
                        .param("force", "true")
                        .param("etfOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("VOO"))
                .andExpect(jsonPath("$.newActions").value(2))
                .andExpect(jsonPath("$.etfDiagnostics.saved").value(2))
                .andExpect(jsonPath("$.etfDiagnostics.skipReasons.identity_mismatch").value(1));
    }

    @Test
    void adminAdjustPrices_noBearer_returns401() throws Exception {
        mockMvc.perform(get("/admin/adjust-prices"))
                .andExpect(status().isUnauthorized());
    }

    // --- /admin/summarize-filings endpoint ---

    @Test
    void adminSummarizeFilings_validKey_returns200() throws Exception {
        when(filingSummaryService.summarizeAll()).thenReturn(
                Map.of("filingsSummarized", 5, "assetsWithNoFilings", 10, "errors", 0));

        mockMvc.perform(get("/admin/summarize-filings").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
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
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.filingsSummarized").value(2));
    }

    @Test
    void adminSummarizeFilings_noBearer_returns401() throws Exception {
        mockMvc.perform(get("/admin/summarize-filings"))
                .andExpect(status().isUnauthorized());
    }

    // --- /admin/indexes/rebuild ---

    @Test
    void rebuildCapRanked_noCode_rebuildsAllOrdered() throws Exception {
        int y = Year.now().getValue();
        when(fattore50IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT50", y, 50, false, BigDecimal.ONE, List.of("AAPL")));
        when(fattore100IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT100", y, 100, false, BigDecimal.TEN, List.of("MSFT")));
        when(fattore1000IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT1000", y, 1000, false, BigDecimal.valueOf(100), List.of("GOOG")));

        mockMvc.perform(post("/admin/indexes/rebuild").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.rebuilds.length()").value(3))
                .andExpect(jsonPath("$.rebuilds[0].indexCode").value("FAT100"))
                .andExpect(jsonPath("$.rebuilds[1].indexCode").value("FAT1000"))
                .andExpect(jsonPath("$.rebuilds[2].indexCode").value("FAT50"))
                .andExpect(jsonPath("$.duration").exists());

        verify(fattore100IndexRebuildService).rebuild(y);
        verify(fattore1000IndexRebuildService).rebuild(y);
        verify(fattore50IndexRebuildService).rebuild(y);
    }

    @Test
    void rebuildCapRanked_codeFat50_onlyThatService() throws Exception {
        int y = Year.now().getValue();
        when(fattore50IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT50", y, 50, false, BigDecimal.ONE, List.of("AAPL")));

        mockMvc.perform(post("/admin/indexes/rebuild")
                        .param("code", "fat50")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuilds.length()").value(1))
                .andExpect(jsonPath("$.rebuilds[0].indexCode").value("FAT50"));

        verify(fattore50IndexRebuildService).rebuild(y);
        verify(fattore100IndexRebuildService, never()).rebuild(anyInt());
        verify(fattore1000IndexRebuildService, never()).rebuild(anyInt());
    }

    @Test
    void rebuildCapRanked_unknownCode_returns400() throws Exception {
        mockMvc.perform(post("/admin/indexes/rebuild")
                        .param("code", "NOTREAL")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isBadRequest());
        verify(fattore50IndexRebuildService, never()).rebuild(anyInt());
        verify(fattore100IndexRebuildService, never()).rebuild(anyInt());
        verify(fattore1000IndexRebuildService, never()).rebuild(anyInt());
    }

    @Test
    void rebuildCapRanked_noBearer_returns401() throws Exception {
        mockMvc.perform(post("/admin/indexes/rebuild"))
                .andExpect(status().isUnauthorized());
    }

    // --- /admin/indexes/rebuild-fattore-50 ---

    @Test
    void rebuildFattore50_validKey_returns200() throws Exception {
        int y = Year.now().getValue();
        when(fattore50IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT50", y, 50, false, BigDecimal.ONE, List.of("AAPL")));

        mockMvc.perform(post("/admin/indexes/rebuild-fattore-50").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.rebuild.indexCode").value("FAT50"))
                .andExpect(jsonPath("$.rebuild.year").value(y))
                .andExpect(jsonPath("$.rebuild.memberCount").value(50))
                .andExpect(jsonPath("$.duration").exists());
    }

    @Test
    void rebuildFattore50_noBearer_returns401() throws Exception {
        mockMvc.perform(post("/admin/indexes/rebuild-fattore-50"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rebuildFattore50_refreshMetrics_includesRefresh() throws Exception {
        int y = Year.now().getValue();
        when(indexMetricsRefreshService.refreshAllListings(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult(10, 2, List.of("X:no_cik")));
        when(fattore50IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT50", y, 3, true, BigDecimal.TEN, List.of("A", "B", "C")));

        mockMvc.perform(post("/admin/indexes/rebuild-fattore-50")
                        .param("refreshMetrics", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.refresh.processed").value(10))
                .andExpect(jsonPath("$.rebuild.partial").value(true));
    }

    // --- /admin/indexes/rebuild-fattore-100 ---

    @Test
    void rebuildFattore100_validKey_returns200() throws Exception {
        int y = Year.now().getValue();
        when(fattore100IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT100", y, 100, false, BigDecimal.ONE, List.of("MSFT")));

        mockMvc.perform(post("/admin/indexes/rebuild-fattore-100").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.rebuild.indexCode").value("FAT100"))
                .andExpect(jsonPath("$.rebuild.memberCount").value(100))
                .andExpect(jsonPath("$.duration").exists());
    }

    @Test
    void rebuildFattore100_noBearer_returns401() throws Exception {
        mockMvc.perform(post("/admin/indexes/rebuild-fattore-100"))
                .andExpect(status().isUnauthorized());
    }

    // --- /admin/indexes/rebuild-fattore-1000 ---

    @Test
    void rebuildFattore1000_validKey_returns200() throws Exception {
        int y = Year.now().getValue();
        when(fattore1000IndexRebuildService.rebuild(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult(
                        "FAT1000", y, 1000, false, BigDecimal.ONE, List.of("NVDA")));

        mockMvc.perform(post("/admin/indexes/rebuild-fattore-1000").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.rebuild.indexCode").value("FAT1000"))
                .andExpect(jsonPath("$.rebuild.memberCount").value(1000))
                .andExpect(jsonPath("$.duration").exists());
    }

    @Test
    void rebuildFattore1000_noBearer_returns401() throws Exception {
        mockMvc.perform(post("/admin/indexes/rebuild-fattore-1000"))
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

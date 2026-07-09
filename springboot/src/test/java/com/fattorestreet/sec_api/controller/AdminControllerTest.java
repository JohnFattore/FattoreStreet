package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.filing.FilingSummaryService;
import com.fattorestreet.sec_api.fundamentals.EdgarService;
import com.fattorestreet.sec_api.listing.AssetService;
import com.fattorestreet.sec_api.listing.EtfIdentityService;
import com.fattorestreet.sec_api.listing.ListingService;
import com.fattorestreet.sec_api.marketdata.IexHistService;
import com.fattorestreet.sec_api.corporateaction.PriceAdjustmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fattorestreet.sec_api.config.SecurityConfig;
import com.fattorestreet.sec_api.testsupport.TestJwtTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Year;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "SECRET_KEY=test-jwt-signing-secret-32chars-min!") // pragma: allowlist secret
class AdminControllerTest {

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
    @MockitoBean private EdgarService edgarService;
    @MockitoBean private PriceAdjustmentService priceAdjustmentService;
    @MockitoBean private IexHistService iexHistService;
    @MockitoBean private EtfIdentityService etfIdentityService;
    @MockitoBean private FilingSummaryService filingSummaryService;
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

    // --- /admin/asset-load endpoint ---

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

    // --- /admin/sync-frames endpoint ---

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

    // --- /admin/indexes/refresh-stocks endpoint ---

    @Test
    void adminRefreshIndexStocks_defaultScope_usesRussell1000() throws Exception {
        int y = Year.now().getValue();
        when(indexMetricsRefreshService.refreshRussell1000Listings(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult(10, 2, List.of("X:no_cik")));

        mockMvc.perform(post("/admin/indexes/refresh-stocks")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.scope").value("russell1000"))
                .andExpect(jsonPath("$.processed").value(10));

        verify(indexMetricsRefreshService).refreshRussell1000Listings(y);
        verify(indexMetricsRefreshService, never()).refreshAllTickers(anyInt());
    }

    @Test
    void adminRefreshIndexStocks_scopeAll_usesAllTickers() throws Exception {
        int y = Year.now().getValue();
        when(indexMetricsRefreshService.refreshAllTickers(anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult(20, 1, List.of("Y:no_price")));

        mockMvc.perform(post("/admin/indexes/refresh-stocks")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.scope").value("all"))
                .andExpect(jsonPath("$.processed").value(20));

        verify(indexMetricsRefreshService).refreshAllTickers(y);
        verify(indexMetricsRefreshService, never()).refreshRussell1000Listings(anyInt());
    }

    @Test
    void adminRefreshIndexStocks_unknownScope_returns400() throws Exception {
        mockMvc.perform(post("/admin/indexes/refresh-stocks")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("scope", "nope"))
                .andExpect(status().isBadRequest());

        verify(indexMetricsRefreshService, never()).refreshRussell1000Listings(anyInt());
        verify(indexMetricsRefreshService, never()).refreshAllTickers(anyInt());
    }

    @Test
    void adminRefreshIndexStocks_withTicker_routesToSingleTicker() throws Exception {
        int y = Year.now().getValue();
        when(indexMetricsRefreshService.refreshSingleTicker(eq("NFLX"), anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult(1, 0, List.of()));

        mockMvc.perform(post("/admin/indexes/refresh-stocks")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("ticker", "NFLX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(y))
                .andExpect(jsonPath("$.scope").value("ticker:NFLX"))
                .andExpect(jsonPath("$.processed").value(1));

        verify(indexMetricsRefreshService).refreshSingleTicker("NFLX", y);
        verify(indexMetricsRefreshService, never()).refreshRussell1000Listings(anyInt());
        verify(indexMetricsRefreshService, never()).refreshAllTickers(anyInt());
    }

    @Test
    void adminRefreshIndexStocks_tickerOverridesScope() throws Exception {
        int y = Year.now().getValue();
        when(indexMetricsRefreshService.refreshSingleTicker(eq("aapl"), anyInt())).thenReturn(
                new com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult(1, 0, List.of()));

        // scope=all is supplied but ticker should win.
        mockMvc.perform(post("/admin/indexes/refresh-stocks")
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin())
                        .param("scope", "all")
                        .param("ticker", "aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ticker:AAPL"));

        verify(indexMetricsRefreshService).refreshSingleTicker("aapl", y);
        verify(indexMetricsRefreshService, never()).refreshAllTickers(anyInt());
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
        when(indexMetricsRefreshService.refreshRussell1000Listings(anyInt())).thenReturn(
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
}

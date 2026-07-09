package com.fattorestreet.sec_api.index;

import tools.jackson.databind.ObjectMapper;
import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexMetricsRefreshServiceTest {

    private static final int YEAR = 2026;

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingIndexMetricsRepository metricsRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private WebService webService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private IwbHoldingsTickerSet iwbHoldingsTickerSet;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IndexMetricsRefreshService service;

    @BeforeEach
    void setUp() {
        service = new IndexMetricsRefreshService(
                listingRepository, metricsRepository, dailyPriceRepository,
                webService, objectMapper, transactionManager, iwbHoldingsTickerSet);
    }

    // ----- helpers -----

    private static Asset asset(long cik, boolean isFund) {
        Asset a = new Asset();
        a.setCik(cik);
        a.setIsFund(isFund);
        return a;
    }

    private static Listing listing(String ticker, Asset asset) {
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setAsset(asset);
        return l;
    }

    private static DailyPrice price(String ticker, double close, Long volume) {
        DailyPrice p = new DailyPrice();
        p.setTicker(ticker);
        p.setTradeDate(LocalDate.of(2026, 4, 1));
        p.setClosePrice(close);
        p.setAdjustedClose(close);
        p.setVolume(volume);
        return p;
    }

    private static String factsJson(long sharesOutstanding) {
        return "{\"facts\":{\"dei\":{\"EntityCommonStockSharesOutstanding\":{\"units\":{\"shares\":["
                + "{\"end\":\"2026-03-31\",\"val\":" + sharesOutstanding + "}"
                + "]}}}}}";
    }

    private static String factsJsonWithPublicFloat(long sharesOutstanding, long publicFloatUsd, String floatEnd) {
        return "{\"facts\":{\"dei\":{"
                + "\"EntityCommonStockSharesOutstanding\":{\"units\":{\"shares\":["
                + "{\"end\":\"2026-03-31\",\"val\":" + sharesOutstanding + "}]}},"
                + "\"EntityPublicFloat\":{\"units\":{\"USD\":["
                + "{\"end\":\"" + floatEnd + "\",\"val\":" + publicFloatUsd + "}]}}"
                + "}}}";
    }

    private static String emptySubmissionsJson() {
        return "{}";
    }

    private void stubHappyPathSec(long cik, long sharesOutstanding) {
        when(webService.fetchFinancials(cik)).thenReturn(factsJson(sharesOutstanding));
        when(webService.fetchSubmissions(cik)).thenReturn(emptySubmissionsJson());
    }

    // ----- scope filtering -----

    @Test
    void refreshRussell1000Listings_filtersToIwbTickers() {
        Listing aapl = listing("AAPL", asset(320193L, false));
        Listing other = listing("ZZZZ", asset(999L, false));
        when(listingRepository.findAll()).thenReturn(List.of(aapl, other));
        when(iwbHoldingsTickerSet.tickers()).thenReturn(Set.of("AAPL"));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(Optional.of(price("AAPL", 100.0, 1_000_000L)));
        stubHappyPathSec(320193L, 16_000_000_000L);
        when(metricsRepository.findByListingAndYear(aapl, YEAR)).thenReturn(Optional.empty());

        IndexMetricsRefreshService.RefreshResult r = service.refreshRussell1000Listings(YEAR);

        assertEquals(1, r.processed());
        assertEquals(0, r.skipped());
        verify(webService, times(1)).fetchFinancials(320193L);
        verify(webService, never()).fetchFinancials(999L);
    }

    @Test
    void refreshAllTickers_doesNotFilter() {
        Listing aapl = listing("AAPL", asset(320193L, false));
        Listing other = listing("ZZZZ", asset(999L, false));
        when(listingRepository.findAll()).thenReturn(List.of(aapl, other));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(Optional.of(price("AAPL", 100.0, 1_000L)));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("ZZZZ"))
                .thenReturn(Optional.of(price("ZZZZ", 50.0, 500L)));
        stubHappyPathSec(320193L, 1_000L);
        stubHappyPathSec(999L, 2_000L);
        when(metricsRepository.findByListingAndYear(any(Listing.class), anyInt()))
                .thenReturn(Optional.empty());

        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);

        assertEquals(2, r.processed());
        assertEquals(0, r.skipped());
    }

    // ----- single ticker -----

    @Test
    void refreshSingleTicker_happyPath() {
        Listing aapl = listing("AAPL", asset(320193L, false));
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(aapl));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(Optional.of(price("AAPL", 100.0, 1_000L)));
        stubHappyPathSec(320193L, 16_000L);
        when(metricsRepository.findByListingAndYear(aapl, YEAR)).thenReturn(Optional.empty());

        IndexMetricsRefreshService.RefreshResult r = service.refreshSingleTicker("AAPL", YEAR);

        assertEquals(1, r.processed());
        assertEquals(0, r.skipped());
        verify(listingRepository, never()).findAll();
        verify(metricsRepository).save(any());
    }

    @Test
    void refreshSingleTicker_normalizesCaseAndTrim() {
        Listing aapl = listing("AAPL", asset(320193L, false));
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(aapl));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(Optional.of(price("AAPL", 100.0, 1_000L)));
        stubHappyPathSec(320193L, 16_000L);
        when(metricsRepository.findByListingAndYear(aapl, YEAR)).thenReturn(Optional.empty());

        IndexMetricsRefreshService.RefreshResult r = service.refreshSingleTicker("  aapl  ", YEAR);

        assertEquals(1, r.processed());
        verify(listingRepository).findByTicker("AAPL");
    }

    @Test
    void refreshSingleTicker_unknownTicker() {
        when(listingRepository.findByTicker("ZZZZ")).thenReturn(Optional.empty());

        IndexMetricsRefreshService.RefreshResult r = service.refreshSingleTicker("ZZZZ", YEAR);

        assertEquals(0, r.processed());
        assertEquals(1, r.skipped());
        assertTrue(r.skippedTickers().contains("ZZZZ:unknown_ticker"));
        verify(metricsRepository, never()).save(any());
    }

    @Test
    void refreshSingleTicker_blankTicker() {
        IndexMetricsRefreshService.RefreshResult r = service.refreshSingleTicker("   ", YEAR);

        assertEquals(0, r.processed());
        assertEquals(1, r.skipped());
        assertTrue(r.skippedTickers().contains(":blank_ticker"));
        verify(listingRepository, never()).findByTicker(any());
    }

    // ----- skip reasons -----

    @Test
    void skips_missingAsset() {
        Listing l = listing("X", null);
        runSingle(l);
        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);
        assertEquals(0, r.processed());
        assertEquals(1, r.skipped());
        assertTrue(r.skippedTickers().contains("X:missing_asset"));
    }

    @Test
    void skips_fund() {
        Listing l = listing("SPY", asset(884394L, true));
        runSingle(l);
        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);
        assertTrue(r.skippedTickers().contains("SPY:fund"));
    }

    @Test
    void skips_noCik() {
        Asset a = new Asset();
        a.setIsFund(false);
        // cik is null
        runSingle(listing("NOCIK", a));
        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);
        assertTrue(r.skippedTickers().contains("NOCIK:no_cik"));
    }

    @Test
    void skips_noPriceRow() {
        Listing l = listing("NODP", asset(1L, false));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("NODP")).thenReturn(Optional.empty());
        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);
        assertTrue(r.skippedTickers().contains("NODP:no_iex_daily_price"));
    }

    @Test
    void skips_secFetchFailure() {
        Listing l = listing("BAD", asset(42L, false));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("BAD"))
                .thenReturn(Optional.of(price("BAD", 10.0, 100L)));
        when(webService.fetchFinancials(42L)).thenThrow(new RuntimeException("503"));
        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);
        assertTrue(r.skippedTickers().contains("BAD:sec_fetch"));
    }

    @Test
    void skips_noShares() {
        Listing l = listing("EMPTY", asset(7L, false));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("EMPTY"))
                .thenReturn(Optional.of(price("EMPTY", 10.0, 100L)));
        when(webService.fetchFinancials(7L)).thenReturn("{\"facts\":{}}");
        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);
        assertTrue(r.skippedTickers().contains("EMPTY:no_shares"));
    }

    // ----- dual-class cap split -----

    @Test
    void googleClassA_capIsHalved() {
        Listing goog = listing("GOOG", asset(1652044L, false));
        when(listingRepository.findAll()).thenReturn(List.of(goog));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("GOOG"))
                .thenReturn(Optional.of(price("GOOG", 200.0, 1_000L)));
        stubHappyPathSec(1652044L, 1_000_000L); // raw cap = 200 * 1,000,000 = 200,000,000
        when(metricsRepository.findByListingAndYear(goog, YEAR)).thenReturn(Optional.empty());

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        // Halved: 200,000,000 / 2 = 100,000,000
        assertEquals(0, saved.getValue().getMarketCap().compareTo(new BigDecimal("100000000")));
        assertEquals(0, saved.getValue().getFreeFloatMarketCap().compareTo(new BigDecimal("100000000")));
    }

    // ----- market cap uses unadjusted close, not adjustedClose -----

    @Test
    void marketCap_usesClosePriceNotAdjustedClose() {
        // Simulate a recent 2:1 split: latest sharesOutstanding from SEC reflects post-split (2x),
        // but the daily price row's adjustedClose is back-adjusted (halved). Market cap should use
        // the unadjusted closePrice so that shares × price reflects current reality.
        Listing l = listing("SPLT", asset(55L, false));
        DailyPrice dp = new DailyPrice();
        dp.setTicker("SPLT");
        dp.setTradeDate(LocalDate.of(2026, 4, 1));
        dp.setClosePrice(100.0);     // unadjusted current close
        dp.setAdjustedClose(50.0);   // back-adjusted for 2:1 split
        dp.setVolume(1_000L);
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("SPLT")).thenReturn(Optional.of(dp));
        stubHappyPathSec(55L, 2_000L); // post-split shares
        when(metricsRepository.findByListingAndYear(l, YEAR)).thenReturn(Optional.empty());

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        // Correct: 2,000 shares × $100 = $200,000  (NOT 2,000 × $50 = $100,000)
        assertEquals(0, saved.getValue().getMarketCap().compareTo(new BigDecimal("200000")));
        // volumeUsd should use the same unadjusted price: 1,000 × $100 = $100,000
        assertEquals(0, saved.getValue().getVolumeUsd().compareTo(new BigDecimal("100000")));
    }

    @Test
    void marketCap_fallsBackToAdjustedCloseWhenCloseMissing() {
        Listing l = listing("FALLBACK", asset(56L, false));
        DailyPrice dp = new DailyPrice();
        dp.setTicker("FALLBACK");
        dp.setTradeDate(LocalDate.of(2026, 4, 1));
        dp.setClosePrice(null);       // missing
        dp.setAdjustedClose(75.0);
        dp.setVolume(10L);
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("FALLBACK")).thenReturn(Optional.of(dp));
        stubHappyPathSec(56L, 100L);
        when(metricsRepository.findByListingAndYear(l, YEAR)).thenReturn(Optional.empty());

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        assertEquals(0, saved.getValue().getMarketCap().compareTo(new BigDecimal("7500")));
    }

    // ----- free-float clamp -----

    @Test
    void freeFloat_usesAdjustedCloseOnFloatDate_postSplit() {
        // Regression for NFLX: 10:1 split on 2025-11-14, so latest sharesOutstanding from SEC is
        // post-split (~4.22B). The historical price row on the 2025-06-30 float date has the
        // unadjusted close (~$1,300, pre-split) AND a post-split-adjusted close (~$130).
        // Using closePrice gives ff = ($565B / $1,300) / 4.22B ≈ 0.10 (10x too small).
        // Using adjustedClose gives ff = ($565B / $130) / 4.22B ≈ 1.03 → clamps to 1.0.
        long postSplitShares = 4_222_162_150L;
        long publicFloatUsd = 565_657_747_980L;
        LocalDate floatDate = LocalDate.of(2025, 6, 30);

        Listing nflx = listing("NFLX", asset(1065280L, false));
        when(listingRepository.findAll()).thenReturn(List.of(nflx));

        // "Latest" daily price (post-split, ~$130 unadjusted close).
        DailyPrice latest = new DailyPrice();
        latest.setTicker("NFLX");
        latest.setTradeDate(LocalDate.of(2026, 4, 1));
        latest.setClosePrice(130.0);
        latest.setAdjustedClose(130.0);
        latest.setVolume(1_000L);
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("NFLX")).thenReturn(Optional.of(latest));

        // Historical price on the float date: unadjusted $1,300 (pre-split), adjusted $130 (post-split).
        DailyPrice floatRow = new DailyPrice();
        floatRow.setTicker("NFLX");
        floatRow.setTradeDate(floatDate);
        floatRow.setClosePrice(1_300.0);
        floatRow.setAdjustedClose(130.0);
        when(dailyPriceRepository.findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("NFLX"), any(LocalDate.class)))
                .thenReturn(Optional.of(floatRow));

        when(webService.fetchFinancials(1065280L))
                .thenReturn(factsJsonWithPublicFloat(postSplitShares, publicFloatUsd, floatDate.toString()));
        when(webService.fetchSubmissions(1065280L)).thenReturn(emptySubmissionsJson());
        when(metricsRepository.findByListingAndYear(nflx, YEAR)).thenReturn(Optional.empty());

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        // Should clamp to 1.0, not produce ~0.10 from the unadjusted close.
        assertEquals(0, saved.getValue().getFreeFloat().compareTo(BigDecimal.ONE));
    }

    @Test
    void freeFloat_fallsBackToClosePriceWhenAdjustedMissing() {
        long shares = 1_000L;
        long publicFloatUsd = 5_000L;
        LocalDate floatDate = LocalDate.of(2025, 6, 30);

        Listing l = listing("NOADJ", asset(99L, false));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("NOADJ"))
                .thenReturn(Optional.of(price("NOADJ", 10.0, 100L)));

        DailyPrice floatRow = new DailyPrice();
        floatRow.setTicker("NOADJ");
        floatRow.setTradeDate(floatDate);
        floatRow.setClosePrice(10.0);
        floatRow.setAdjustedClose(null); // adjusted not yet computed
        when(dailyPriceRepository.findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("NOADJ"), any(LocalDate.class)))
                .thenReturn(Optional.of(floatRow));

        when(webService.fetchFinancials(99L))
                .thenReturn(factsJsonWithPublicFloat(shares, publicFloatUsd, floatDate.toString()));
        when(webService.fetchSubmissions(99L)).thenReturn(emptySubmissionsJson());
        when(metricsRepository.findByListingAndYear(l, YEAR)).thenReturn(Optional.empty());

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        // floatShares = 5000 / 10 = 500; ff = 500 / 1000 = 0.5
        assertEquals(0, saved.getValue().getFreeFloat().compareTo(new BigDecimal("0.5000000000")));
    }

    @Test
    void freeFloat_isClampedToOne() {
        // public float USD is huge → derived float shares > shares outstanding → must clamp to 1.
        long shares = 1_000L;
        long publicFloat = 1_000_000_000_000L; // implies billions of float shares at $10
        Listing l = listing("CLAMP", asset(11L, false));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("CLAMP"))
                .thenReturn(Optional.of(price("CLAMP", 10.0, 100L)));
        when(dailyPriceRepository.findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
                eq("CLAMP"), any(LocalDate.class)))
                .thenReturn(Optional.of(price("CLAMP", 10.0, 100L)));
        when(webService.fetchFinancials(11L))
                .thenReturn(factsJsonWithPublicFloat(shares, publicFloat, "2026-03-31"));
        when(webService.fetchSubmissions(11L)).thenReturn(emptySubmissionsJson());
        when(metricsRepository.findByListingAndYear(l, YEAR)).thenReturn(Optional.empty());

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        assertEquals(0, saved.getValue().getFreeFloat().compareTo(BigDecimal.ONE));
    }

    // ----- yearIpo / securityType preservation on update -----

    @Test
    void update_preservesYearIpoAndCustomSecurityType() {
        Listing aapl = listing("AAPL", asset(320193L, false));
        when(listingRepository.findAll()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(Optional.of(price("AAPL", 100.0, 1_000L)));
        stubHappyPathSec(320193L, 16_000L);

        ListingIndexMetrics existing = new ListingIndexMetrics();
        existing.setListing(aapl);
        existing.setYear(YEAR);
        existing.setYearIpo(1980);
        existing.setSecurityType("ADR"); // pretend another job set this
        when(metricsRepository.findByListingAndYear(aapl, YEAR)).thenReturn(Optional.of(existing));

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        assertEquals(1980, saved.getValue().getYearIpo());
        assertEquals("ADR", saved.getValue().getSecurityType());
    }

    @Test
    void insert_seedsDefaultsAndAppliesTickerOverride() {
        Listing mlp = listing("EPD", asset(1061219L, false));
        when(listingRepository.findAll()).thenReturn(List.of(mlp));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("EPD"))
                .thenReturn(Optional.of(price("EPD", 30.0, 1_000L)));
        stubHappyPathSec(1061219L, 1_000L);
        when(metricsRepository.findByListingAndYear(mlp, YEAR)).thenReturn(Optional.empty());

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        assertEquals("MLP", saved.getValue().getSecurityType());
        assertEquals(0, saved.getValue().getYearIpo());
    }

    @Test
    void update_reAppliesTickerOverrideForKnownMlp() {
        Listing mlp = listing("EPD", asset(1061219L, false));
        when(listingRepository.findAll()).thenReturn(List.of(mlp));
        when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc("EPD"))
                .thenReturn(Optional.of(price("EPD", 30.0, 1_000L)));
        stubHappyPathSec(1061219L, 1_000L);

        ListingIndexMetrics existing = new ListingIndexMetrics();
        existing.setListing(mlp);
        existing.setYear(YEAR);
        existing.setYearIpo(1998);
        existing.setSecurityType("Common Stock"); // misclassified before override list update
        when(metricsRepository.findByListingAndYear(mlp, YEAR)).thenReturn(Optional.of(existing));

        service.refreshAllTickers(YEAR);

        ArgumentCaptor<ListingIndexMetrics> saved = ArgumentCaptor.forClass(ListingIndexMetrics.class);
        verify(metricsRepository).save(saved.capture());
        assertEquals("MLP", saved.getValue().getSecurityType());
        assertEquals(1998, saved.getValue().getYearIpo());
    }

    // ----- circuit breaker -----

    @Test
    void abortsAfterConsecutiveSecFailures() {
        // 30 listings, all with SEC fetch failures → should abort at 25 and skip remaining 5.
        java.util.List<Listing> listings = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String t = "T" + i;
            Listing l = listing(t, asset(1000L + i, false));
            listings.add(l);
            // lenient: the abort under test stops the loop at 25, leaving the last stubs unused
            org.mockito.Mockito.lenient().when(dailyPriceRepository.findTopByTickerOrderByTradeDateDesc(t))
                    .thenReturn(Optional.of(price(t, 10.0, 100L)));
        }
        when(listingRepository.findAll()).thenReturn(listings);
        when(webService.fetchFinancials(anyLong())).thenThrow(new RuntimeException("503"));

        IndexMetricsRefreshService.RefreshResult r = service.refreshAllTickers(YEAR);

        // Loop breaks once consecutive failure count hits 25; remaining 5 are not even attempted.
        assertEquals(0, r.processed());
        assertEquals(25, r.skipped());
        assertFalse(r.skippedTickers().contains("T29:sec_fetch"));
        verify(metricsRepository, never()).save(any());
    }

    // ----- helpers for tests that share the "single listing only" preamble -----

    private void runSingle(Listing l) {
        when(listingRepository.findAll()).thenReturn(List.of(l));
    }
}

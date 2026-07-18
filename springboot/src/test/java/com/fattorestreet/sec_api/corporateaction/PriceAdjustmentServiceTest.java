package com.fattorestreet.sec_api.corporateaction;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAdjustmentServiceTest {

    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private EquityCorporateActionService equityCorporateActionService;
    @Mock private EtfCorporateActionService etfCorporateActionService;
    @Mock private CorporateActionValidationService corporateActionValidationService;
    @Mock private AdjustedPriceValidationService adjustedPriceValidationService;
    @Mock private ListingRepository listingRepository;
    @InjectMocks private PriceAdjustmentService service;

    private Listing freshListing(String ticker) {
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setTitle(ticker);
        l.setLastSecDetectionAt(LocalDateTime.now());
        return l;
    }

    /** Stubs a listing with a fresh detection stamp so adjustTicker skips the SEC fetch. */
    private void stubFreshListing(String ticker) {
        when(listingRepository.findByTicker(ticker)).thenReturn(Optional.of(freshListing(ticker)));
    }

    private DailyPrice buildPrice(String ticker, LocalDate date, double close) {
        DailyPrice dp = new DailyPrice();
        dp.setTicker(ticker);
        dp.setTradeDate(date);
        dp.setOpenPrice(close - 1);
        dp.setHighPrice(close + 1);
        dp.setLowPrice(close - 2);
        dp.setClosePrice(close);
        return dp;
    }

    private Asset buildAsset(Long cik) {
        Asset a = new Asset();
        a.setId(1L);
        a.setCik(cik);
        a.setIsFund(false);
        return a;
    }

    private Asset buildAssetWithListing(String ticker, Long cik) {
        Asset a = new Asset();
        a.setId(cik);
        a.setCik(cik);
        a.setIsFund(false);
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setTitle(ticker);
        a.setListings(List.of(l));
        return a;
    }

    private EtfCorporateActionService.EtfDetectionReport buildEtfReport(String ticker, Long cik, int saved) {
        EtfCorporateActionService.EtfDetectionReport report =
                new EtfCorporateActionService.EtfDetectionReport(ticker, cik);
        for (int i = 0; i < saved; i++) {
            report.sampleCreated("acc-" + i, "497", "fund497.htm", LocalDate.of(2025, 1, 1), 0.5, 4);
        }
        return report;
    }

    private EquityCorporateActionService.EquityDetectionReport buildEquityReport(String ticker, Long cik, int savedActions) {
        return new EquityCorporateActionService.EquityDetectionReport(
                ticker,
                cik,
                new EquityCorporateActionService.SplitDetectionStats(0, 0, savedActions, 0, 0, 0, 0, 0, 0, 0),
                new EquityCorporateActionService.DividendDetectionStats(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        Map.of(), Map.of(), Map.of()),
                null);
    }

    @Test
    void adjustTicker_noActions_setsAdjustedEqualToRaw() {
        Asset asset = buildAsset(320193L);
        DailyPrice dp = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 150.0);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(equityCorporateActionService.detectAndPersistWithDiagnostics("AAPL", 320193L))
                .thenReturn(buildEquityReport("AAPL", 320193L, 0));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(dp));

        Map<String, Object> result = service.adjustTicker("AAPL");

        assertEquals("ok", result.get("status"));
        assertEquals(150.0, dp.getAdjustedClose());
        assertEquals(149.0, dp.getAdjustedOpen());
        verify(dailyPriceRepository).saveAll(anyList());
    }

    @Test
    void adjustTicker_withSplit_appliesFactor() {
        Asset asset = buildAsset(320193L);
        DailyPrice postSplit = buildPrice("AAPL", LocalDate.of(2025, 1, 15), 50.0);
        DailyPrice splitDay = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 200.0);
        DailyPrice preSplit = buildPrice("AAPL", LocalDate.of(2025, 1, 5), 195.0);

        CorporateAction split = new CorporateAction();
        split.setTicker("AAPL");
        split.setActionType(ActionType.SPLIT);
        split.setEffectiveDate(LocalDate.of(2025, 1, 10));
        split.setRatio(0.25);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(split));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(postSplit, splitDay, preSplit));
        stubFreshListing("AAPL");

        service.adjustTicker("AAPL");

        assertEquals(50.0, postSplit.getAdjustedClose());
        assertEquals(200.0, splitDay.getAdjustedClose());
        assertEquals(48.75, preSplit.getAdjustedClose());
    }

    @Test
    void adjustTicker_noAsset_returnsNoAssetStatus() {
        when(assetRepository.findByListings_Ticker("FAKE")).thenReturn(null);

        Map<String, Object> result = service.adjustTicker("FAKE");

        assertEquals("no_asset", result.get("status"));
        assertEquals(0, result.get("pricesUpdated"));
    }

    @Test
    void adjustTicker_fundTicker_usesEtfDetector() {
        Asset asset = buildAsset(111111L);
        asset.setIsFund(true);
        DailyPrice dp = buildPrice("SPY", LocalDate.of(2025, 1, 10), 500.0);

        when(assetRepository.findByListings_Ticker("SPY")).thenReturn(asset);
        when(etfCorporateActionService.detectAndPersist("SPY", 111111L))
                .thenReturn(buildEtfReport("SPY", 111111L, 0));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("SPY"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("SPY"))
                .thenReturn(List.of(dp));

        Map<String, Object> result = service.adjustTicker("SPY");

        assertEquals("ok", result.get("status"));
        verify(etfCorporateActionService).detectAndPersist("SPY", 111111L);
        verify(equityCorporateActionService, never()).detectAndPersistWithDiagnostics("SPY", 111111L);
        assertNotNull(result.get("etfDiagnostics"));
    }

    @Test
    void adjustTicker_fundTicker_respectsConfidenceThreshold() {
        Asset asset = buildAsset(111111L);
        asset.setIsFund(true);
        DailyPrice dp = buildPrice("VOO", LocalDate.of(2025, 1, 10), 500.0);

        when(assetRepository.findByListings_Ticker("VOO")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("VOO"))
                .thenReturn(Collections.emptyList(), Collections.emptyList());
        when(etfCorporateActionService.detectAndPersist("VOO", 111111L))
                .thenReturn(buildEtfReport("VOO", 111111L, 0));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("VOO"))
                .thenReturn(List.of(dp));

        Map<String, Object> result = service.adjustTicker("VOO");

        assertEquals("ok", result.get("status"));
        verify(etfCorporateActionService).detectAndPersist("VOO", 111111L);
        verify(equityCorporateActionService, never()).detectAndPersistWithDiagnostics(anyString(), anyLong());
    }

    @Test
    void adjustTicker_withDividend_appliesDividendFactor() {
        Asset asset = buildAsset(320193L);
        DailyPrice afterDiv = buildPrice("AAPL", LocalDate.of(2025, 2, 1), 150.0);
        DailyPrice divDay = buildPrice("AAPL", LocalDate.of(2025, 1, 15), 152.0);
        DailyPrice beforeDiv = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 155.0);

        CorporateAction dividend = new CorporateAction();
        dividend.setTicker("AAPL");
        dividend.setActionType(ActionType.DIVIDEND);
        dividend.setEffectiveDate(LocalDate.of(2025, 1, 15));
        dividend.setRatio(1.25);
        dividend.setAdjustedDividend(0.25);
        // Raw cash on ex-date (vs split-forward adjusted); factor uses raw to match prior raw close.
        dividend.setRawDividend(0.0625);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(dividend));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(afterDiv, divDay, beforeDiv));
        stubFreshListing("AAPL");

        service.adjustTicker("AAPL");

        assertEquals(150.0, afterDiv.getAdjustedClose());
        assertEquals(152.0, divDay.getAdjustedClose());
        // 155 * (1 - 0.0625/155) = 154.9375
        assertEquals(154.9375, beforeDiv.getAdjustedClose());
    }

    @Test
    void adjustTicker_withDividend_prefersRawOverAdjustedForFactor() {
        Asset asset = buildAsset(320193L);
        DailyPrice afterDiv = buildPrice("AAPL", LocalDate.of(2025, 2, 1), 150.0);
        DailyPrice divDay = buildPrice("AAPL", LocalDate.of(2025, 1, 15), 152.0);
        DailyPrice beforeDiv = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 155.0);

        CorporateAction dividend = new CorporateAction();
        dividend.setTicker("AAPL");
        dividend.setActionType(ActionType.DIVIDEND);
        dividend.setEffectiveDate(LocalDate.of(2025, 1, 15));
        dividend.setRatio(0.25);
        dividend.setAdjustedDividend(0.25);
        dividend.setRawDividend(1.0);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(dividend));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(afterDiv, divDay, beforeDiv));
        stubFreshListing("AAPL");

        service.adjustTicker("AAPL");

        assertEquals(155.0 * (1.0 - 1.0 / 155.0), beforeDiv.getAdjustedClose(), 1e-9);
    }

    @Test
    void adjustTicker_withDividend_usesPriorTradingCloseAsDenominator() {
        Asset asset = buildAsset(320193L);
        DailyPrice newest = buildPrice("AAPL", LocalDate.of(2025, 1, 20), 10.0);
        DailyPrice exDiv = buildPrice("AAPL", LocalDate.of(2025, 1, 15), 100.0);
        DailyPrice priorTradingDay = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 120.0);

        CorporateAction dividend = new CorporateAction();
        dividend.setTicker("AAPL");
        dividend.setActionType(ActionType.DIVIDEND);
        dividend.setEffectiveDate(LocalDate.of(2025, 1, 15));
        dividend.setRatio(10.0);
        dividend.setAdjustedDividend(10.0);
        dividend.setRawDividend(10.0);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(dividend));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(newest, exDiv, priorTradingDay));
        stubFreshListing("AAPL");

        service.adjustTicker("AAPL");

        assertEquals(10.0, newest.getAdjustedClose());
        assertEquals(100.0, exDiv.getAdjustedClose());
        assertEquals(110.0, priorTradingDay.getAdjustedClose());
    }

    @Test
    void adjustTicker_withTwoSameDayDividends_appliesBothFactors() {
        Asset asset = buildAsset(320193L);
        DailyPrice newest = buildPrice("COST", LocalDate.of(2024, 12, 20), 100.0);
        DailyPrice exDiv = buildPrice("COST", LocalDate.of(2024, 12, 10), 118.0);
        DailyPrice priorTradingDay = buildPrice("COST", LocalDate.of(2024, 12, 9), 120.0);

        CorporateAction regular = new CorporateAction();
        regular.setTicker("COST");
        regular.setActionType(ActionType.DIVIDEND);
        regular.setEffectiveDate(LocalDate.of(2024, 12, 10));
        regular.setRatio(0.5);
        regular.setAdjustedDividend(0.5);
        regular.setRawDividend(0.5);

        CorporateAction special = new CorporateAction();
        special.setTicker("COST");
        special.setActionType(ActionType.DIVIDEND);
        special.setEffectiveDate(LocalDate.of(2024, 12, 10));
        special.setRatio(1.5);
        special.setAdjustedDividend(1.5);
        special.setRawDividend(1.5);

        when(assetRepository.findByListings_Ticker("COST")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("COST"))
                .thenReturn(List.of(regular, special));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("COST"))
                .thenReturn(List.of(newest, exDiv, priorTradingDay));
        stubFreshListing("COST");

        service.adjustTicker("COST");

        assertEquals(100.0, newest.getAdjustedClose());
        assertEquals(118.0, exDiv.getAdjustedClose());
        assertEquals(118.0063, priorTradingDay.getAdjustedClose(), 0.0001);
    }

    @Test
    void adjustTicker_actionOnNonTradingDay_snapsToNextTradeDate() {
        Asset asset = buildAsset(320193L);
        DailyPrice monday = buildPrice("AAPL", LocalDate.of(2025, 1, 13), 50.0);
        DailyPrice friday = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 200.0);

        CorporateAction split = new CorporateAction();
        split.setTicker("AAPL");
        split.setActionType(ActionType.SPLIT);
        // Saturday: no matching price row; must apply on Monday instead of being dropped.
        split.setEffectiveDate(LocalDate.of(2025, 1, 11));
        split.setRatio(0.25);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(split));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(monday, friday));
        stubFreshListing("AAPL");

        Map<String, Object> result = service.adjustTicker("AAPL");

        assertEquals(50.0, monday.getAdjustedClose());
        assertEquals(50.0, friday.getAdjustedClose());
        assertEquals(1, result.get("snappedActions"));
    }

    @Test
    void adjustTicker_actionAfterNewestPrice_isIgnoredForNow() {
        Asset asset = buildAsset(320193L);
        DailyPrice dp = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 200.0);

        CorporateAction split = new CorporateAction();
        split.setTicker("AAPL");
        split.setActionType(ActionType.SPLIT);
        split.setEffectiveDate(LocalDate.of(2025, 1, 20));
        split.setRatio(0.25);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(split));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(dp));
        stubFreshListing("AAPL");

        Map<String, Object> result = service.adjustTicker("AAPL");

        assertEquals(200.0, dp.getAdjustedClose());
        assertEquals(0, result.get("snappedActions"));
    }

    @Test
    void adjustTicker_validateWithYfinance_includesValidationReport() {
        Asset asset = buildAsset(320193L);
        DailyPrice dp = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 150.0);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(equityCorporateActionService.detectAndPersistWithDiagnostics("AAPL", 320193L))
                .thenReturn(buildEquityReport("AAPL", 320193L, 0));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(dp));
        CorporateActionValidationService.ValidationReport validationReport =
                new CorporateActionValidationService.ValidationReport(
                        "AAPL", 2, 2, 0, 0, 0, 0, List.of());
        when(corporateActionValidationService.validateTicker("AAPL", LocalDate.of(2016, 1, 1)))
                .thenReturn(validationReport);
        when(adjustedPriceValidationService.validateTicker("AAPL", LocalDate.of(2016, 1, 1)))
                .thenReturn(AdjustedPriceValidationService.PriceValidationReport.empty("AAPL", "no_reference_data"));

        Map<String, Object> result = service.adjustTicker("AAPL", new PriceAdjustmentService.AdjustmentOptions(false, false, false, true));

        assertEquals("ok", result.get("status"));
        assertNotNull(result.get("validationReport"));
        assertNotNull(result.get("priceValidationReport"));
        verify(corporateActionValidationService).validateTicker("AAPL", LocalDate.of(2016, 1, 1));
        verify(adjustedPriceValidationService).validateTicker("AAPL", LocalDate.of(2016, 1, 1));
    }

    @Test
    void adjustAllTickers_processesMultiple() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);
        Asset msft = buildAssetWithListing("MSFT", 789019L);

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("AAPL", "MSFT"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(Collections.emptyList());
        when(assetRepository.findAllWithListings()).thenReturn(List.of(aapl, msft));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL", "MSFT"));
        when(equityCorporateActionService.detectAndPersistWithDiagnostics(anyString(), anyLong()))
                .thenReturn(buildEquityReport("X", 1L, 0));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc(anyString()))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc(anyString()))
                .thenReturn(List.of(buildPrice("X", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(2, result.get("tickersProcessed"));
        assertEquals(0, result.get("skippedNoAsset"));
        assertNotNull(result.get("etfDiagnosticsSummary"));
    }

    @Test
    void adjustAllTickers_skipsSecFetchWhenActionsExistAndDetectionFresh() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);
        aapl.getListings().get(0).setLastSecDetectionAt(LocalDateTime.now());

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("AAPL"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(assetRepository.findAllWithListings()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(buildPrice("AAPL", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(1, result.get("tickersProcessed"));
        verify(equityCorporateActionService, never()).detectAndPersistWithDiagnostics(anyString(), anyLong());
    }

    @Test
    void adjustAllTickers_forceTrue_refetchesSec() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(Collections.emptyList());
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(assetRepository.findAllWithListings()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(equityCorporateActionService.detectAndPersistWithDiagnostics("AAPL", 320193L))
                .thenReturn(buildEquityReport("AAPL", 320193L, 0));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(buildPrice("AAPL", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(new PriceAdjustmentService.AdjustmentOptions(true, false, false, false));

        assertEquals(1, result.get("tickersProcessed"));
        verify(equityCorporateActionService).detectAndPersistWithDiagnostics("AAPL", 320193L);
    }

    @Test
    void adjustAllTickers_staleDetectionIsRescheduledAndStamped() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);
        Listing listing = aapl.getListings().get(0);
        listing.setLastSecDetectionAt(LocalDateTime.now().minusDays(
                PriceAdjustmentService.SEC_REDETECTION_INTERVAL_DAYS + 1));

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(Collections.emptyList());
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(assetRepository.findAllWithListings()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(equityCorporateActionService.detectAndPersistWithDiagnostics("AAPL", 320193L))
                .thenReturn(buildEquityReport("AAPL", 320193L, 0));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(buildPrice("AAPL", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(1, result.get("scheduledDetections"));
        verify(equityCorporateActionService).detectAndPersistWithDiagnostics("AAPL", 320193L);
        verify(listingRepository).save(listing);
        assertNotNull(listing.getLastSecDetectionAt());
        assertTrue(listing.getLastSecDetectionAt().isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void adjustAllTickers_capLimitsScheduledDetectionsPerRun() {
        // 3 tickers all never detected: cap = ceil(3/7) = 1, so only one is re-detected
        // this run; the rest drain on subsequent runs (oldest first).
        Asset a = buildAssetWithListing("AAA", 1L);
        Asset b = buildAssetWithListing("BBB", 2L);
        Asset c = buildAssetWithListing("CCC", 3L);

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(Collections.emptyList());
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAA", "BBB", "CCC"));
        when(assetRepository.findAllWithListings()).thenReturn(List.of(a, b, c));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAA", "BBB", "CCC"));
        when(equityCorporateActionService.detectAndPersistWithDiagnostics(anyString(), anyLong()))
                .thenReturn(buildEquityReport("X", 1L, 0));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc(anyString()))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc(anyString()))
                .thenReturn(List.of(buildPrice("X", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(1, result.get("scheduledDetections"));
        verify(equityCorporateActionService, times(1)).detectAndPersistWithDiagnostics(anyString(), anyLong());
        verify(listingRepository, times(1)).save(any(Listing.class));
    }

    @Test
    void adjustAllTickers_priceJumpTriggersImmediateRedetection() {
        // Fresh stamp and existing actions would normally skip the SEC fetch, but the
        // -75% overnight move (split signature) forces same-run re-detection.
        Asset aapl = buildAssetWithListing("AAPL", 320193L);
        aapl.getListings().get(0).setLastSecDetectionAt(LocalDateTime.now());

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("AAPL"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(assetRepository.findAllWithListings()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(dailyPriceRepository.findTop2ByTickerOrderByTradeDateDesc("AAPL")).thenReturn(List.of(
                buildPrice("AAPL", LocalDate.of(2025, 1, 3), 50.0),
                buildPrice("AAPL", LocalDate.of(2025, 1, 2), 200.0)));
        when(equityCorporateActionService.detectAndPersistWithDiagnostics("AAPL", 320193L))
                .thenReturn(buildEquityReport("AAPL", 320193L, 1));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(buildPrice("AAPL", LocalDate.of(2025, 1, 3), 50.0)));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(1, result.get("jumpTriggeredDetections"));
        verify(equityCorporateActionService).detectAndPersistWithDiagnostics("AAPL", 320193L);
    }

    @Test
    void adjustAllTickers_normalMoveDoesNotTriggerRedetection() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);
        aapl.getListings().get(0).setLastSecDetectionAt(LocalDateTime.now());

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("AAPL"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(assetRepository.findAllWithListings()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(dailyPriceRepository.findTop2ByTickerOrderByTradeDateDesc("AAPL")).thenReturn(List.of(
                buildPrice("AAPL", LocalDate.of(2025, 1, 3), 202.0),
                buildPrice("AAPL", LocalDate.of(2025, 1, 2), 200.0)));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(buildPrice("AAPL", LocalDate.of(2025, 1, 3), 202.0)));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(0, result.get("jumpTriggeredDetections"));
        verify(equityCorporateActionService, never()).detectAndPersistWithDiagnostics(anyString(), anyLong());
    }

    @Test
    void adjustAllTickers_errorLeavesAdjustedNullForRetry() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("AAPL"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(Collections.emptyList());
        when(assetRepository.findAllWithListings()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(equityCorporateActionService.detectAndPersistWithDiagnostics("AAPL", 320193L))
                .thenThrow(new RuntimeException("SEC fetch failed"));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(1, result.get("failedTickers"));
        assertEquals(0, result.get("tickersProcessed"));
        // No raw-as-adjusted freeze: adjusted stays NULL so the next run retries.
        verify(dailyPriceRepository, never()).saveAll(anyList());
    }

    @Test
    void adjustAllTickers_skipsNoAssetAndSetsRawAsAdjusted() {
        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("FAKE"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(Collections.emptyList());
        when(assetRepository.findAllWithListings()).thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("FAKE"));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("FAKE"))
                .thenReturn(List.of(buildPrice("FAKE", LocalDate.of(2025, 1, 1), 50.0)));

        Map<String, Object> result = service.adjustAllTickers(PriceAdjustmentService.AdjustmentOptions.DEFAULTS);

        assertEquals(1, result.get("skippedNoAsset"));
        assertEquals(0, result.get("tickersProcessed"));
        verify(dailyPriceRepository).saveAll(anyList());
    }
}

package com.example.sec_api.service;

import com.example.sec_api.model.Asset;
import com.example.sec_api.model.CorporateAction;
import com.example.sec_api.model.CorporateAction.ActionType;
import com.example.sec_api.model.DailyPrice;
import com.example.sec_api.model.Listing;
import com.example.sec_api.repository.AssetRepository;
import com.example.sec_api.repository.CorporateActionRepository;
import com.example.sec_api.repository.DailyPriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAdjustmentServiceTest {

    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private SplitDividendService splitDividendService;
    @InjectMocks private PriceAdjustmentService service;

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
        return a;
    }

    private Asset buildAssetWithListing(String ticker, Long cik) {
        Asset a = new Asset();
        a.setId(cik);
        a.setCik(cik);
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setTitle(ticker);
        a.setListings(List.of(l));
        return a;
    }

    @Test
    void adjustTicker_noActions_setsAdjustedEqualToRaw() {
        Asset asset = buildAsset(320193L);
        DailyPrice dp = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 150.0);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(splitDividendService.detectAndPersist("AAPL", 320193L)).thenReturn(0);
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
        when(splitDividendService.detectAndPersist("AAPL", 320193L)).thenReturn(0);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(split));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(postSplit, splitDay, preSplit));

        service.adjustTicker("AAPL");

        assertEquals(50.0, postSplit.getAdjustedClose());
        assertEquals(50.0, splitDay.getAdjustedClose());
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
    void adjustTicker_withDividend_appliesDividendFactor() {
        Asset asset = buildAsset(320193L);
        DailyPrice afterDiv = buildPrice("AAPL", LocalDate.of(2025, 2, 1), 150.0);
        DailyPrice divDay = buildPrice("AAPL", LocalDate.of(2025, 1, 15), 152.0);
        DailyPrice beforeDiv = buildPrice("AAPL", LocalDate.of(2025, 1, 10), 155.0);

        CorporateAction dividend = new CorporateAction();
        dividend.setTicker("AAPL");
        dividend.setActionType(ActionType.DIVIDEND);
        dividend.setEffectiveDate(LocalDate.of(2025, 1, 15));
        dividend.setRatio(0.25);

        when(assetRepository.findByListings_Ticker("AAPL")).thenReturn(asset);
        when(splitDividendService.detectAndPersist("AAPL", 320193L)).thenReturn(0);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(List.of(dividend));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(afterDiv, divDay, beforeDiv));

        service.adjustTicker("AAPL");

        assertEquals(150.0, afterDiv.getAdjustedClose());
        assertTrue(divDay.getAdjustedClose() < 152.0);
        assertTrue(beforeDiv.getAdjustedClose() < 155.0);
    }

    @Test
    void adjustAllTickers_processesMultiple() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);
        Asset msft = buildAssetWithListing("MSFT", 789019L);

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("AAPL", "MSFT"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(Collections.emptyList());
        when(assetRepository.findAll()).thenReturn(List.of(aapl, msft));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL", "MSFT"));
        when(splitDividendService.detectAndPersist(anyString(), anyLong())).thenReturn(0);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc(anyString()))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc(anyString()))
                .thenReturn(List.of(buildPrice("X", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(false);

        assertEquals(2, result.get("tickersProcessed"));
        assertEquals(0, result.get("skippedNoAsset"));
    }

    @Test
    void adjustAllTickers_skipsSecFetchWhenActionsExist() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("AAPL"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(assetRepository.findAll()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(buildPrice("AAPL", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(false);

        assertEquals(1, result.get("tickersProcessed"));
        verify(splitDividendService, never()).detectAndPersist(anyString(), anyLong());
    }

    @Test
    void adjustAllTickers_forceTrue_refetchesSec() {
        Asset aapl = buildAssetWithListing("AAPL", 320193L);

        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(Collections.emptyList());
        when(corporateActionRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(assetRepository.findAll()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("AAPL"));
        when(splitDividendService.detectAndPersist("AAPL", 320193L)).thenReturn(0);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("AAPL"))
                .thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(buildPrice("AAPL", LocalDate.of(2025, 1, 1), 100.0)));

        Map<String, Object> result = service.adjustAllTickers(true);

        assertEquals(1, result.get("tickersProcessed"));
        verify(splitDividendService).detectAndPersist("AAPL", 320193L);
    }

    @Test
    void adjustAllTickers_skipsNoAssetAndSetsRawAsAdjusted() {
        when(dailyPriceRepository.findTickersWithUnadjustedPrices()).thenReturn(List.of("FAKE"));
        when(corporateActionRepository.findDistinctTickers()).thenReturn(Collections.emptyList());
        when(assetRepository.findAll()).thenReturn(Collections.emptyList());
        when(dailyPriceRepository.findDistinctTickers()).thenReturn(List.of("FAKE"));
        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("FAKE"))
                .thenReturn(List.of(buildPrice("FAKE", LocalDate.of(2025, 1, 1), 50.0)));

        Map<String, Object> result = service.adjustAllTickers(false);

        assertEquals(1, result.get("skippedNoAsset"));
        assertEquals(0, result.get("tickersProcessed"));
        verify(dailyPriceRepository).saveAll(anyList());
    }
}

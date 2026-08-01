package com.fattorestreet.sec_api.listing;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the SEC ticker-index parsing and upsert that used to live inline in
 * {@code AdminController.assetLoad}. The two SEC feeds ship in several shapes over time, so each
 * one has its own case here.
 */
@ExtendWith(MockitoExtension.class)
class SecTickerLoadServiceTest {

    @Mock
    private WebService webService;

    @Mock
    private AssetService assetService;

    @Mock
    private ListingService listingService;

    private SecTickerLoadService service;

    @BeforeEach
    void setUp() {
        service = new SecTickerLoadService(webService, assetService, listingService, new ObjectMapper());
    }

    /** Makes createOrUpdateAsset behave like the real one for the purposes of the loop: echo it back. */
    private void echoSavedAsset() {
        when(assetService.createOrUpdateAsset(any(Asset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Asset firstSavedAsset() {
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetService).createOrUpdateAsset(captor.capture());
        return captor.getValue();
    }

    private Listing firstSavedListing() {
        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingService).createOrUpdateListing(captor.capture());
        return captor.getValue();
    }

    @Test
    void emptyFeedsLoadNothing() {
        when(webService.fetchSecTickers()).thenReturn(Map.of());
        when(webService.fetchSecMutualFundTickers()).thenReturn("{}");

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(0, result.equitiesLoaded());
        assertEquals(0, result.fundsLoaded());
        assertEquals(0, result.tickersLoaded());
        verify(assetService, never()).createOrUpdateAsset(any());
    }

    @Test
    void loadsEquityRowsFromCompanyTickerIndex() {
        when(webService.fetchSecTickers()).thenReturn(Map.of(
                0, Map.of("ticker", "AAPL", "cik_str", "320193", "title", "Apple Inc.")));
        when(webService.fetchSecMutualFundTickers()).thenReturn("{}");
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(1, result.equitiesLoaded());
        assertEquals(0, result.fundsLoaded());
        Asset saved = firstSavedAsset();
        assertEquals(320193L, saved.getCik());
        assertFalse(saved.getIsFund());
        Listing listing = firstSavedListing();
        assertEquals("AAPL", listing.getTicker());
        assertEquals("Apple Inc.", listing.getTitle());
    }

    @Test
    void skipsEquityRowsMissingTickerOrCik() {
        when(webService.fetchSecTickers()).thenReturn(Map.of(
                0, Map.of("ticker", "", "cik_str", "320193", "title", "No ticker"),
                1, Map.of("ticker", "NOCIK", "cik_str", "not-a-number", "title", "Bad cik")));
        when(webService.fetchSecMutualFundTickers()).thenReturn("{}");

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(0, result.tickersLoaded());
        verify(assetService, never()).createOrUpdateAsset(any());
    }

    @Test
    void mutualFundFallbackKeysLoadTicker() {
        when(webService.fetchSecTickers()).thenReturn(Map.of());
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
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(1, result.fundsLoaded());
        assertEquals(0, result.equitiesLoaded());
        Asset saved = firstSavedAsset();
        assertTrue(saved.getIsFund());
        assertEquals(102909L, saved.getCik());
        // Series and class names compose the title when no explicit title field is present.
        assertEquals("Vanguard 500 Index Fund - ETF Shares", firstSavedListing().getTitle());
    }

    @Test
    void mutualFundTabularShapeLoadsTicker() {
        when(webService.fetchSecTickers()).thenReturn(Map.of());
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "fields": ["cik", "seriesId", "classId", "symbol", "series_name", "class_name"],
                  "data": [
                    ["0000102909", "S000001", "C000001", "VOO", "Vanguard 500 Index Fund", "ETF Shares"]
                  ]
                }
                """);
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(1, result.fundsLoaded());
        Asset saved = firstSavedAsset();
        assertTrue(saved.getIsFund());
        assertEquals(102909L, saved.getCik());
        assertEquals("VOO", firstSavedListing().getTicker());
    }

    @Test
    void mutualFundFieldsDataObjectRowsLoadTicker() {
        when(webService.fetchSecTickers()).thenReturn(Map.of());
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
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(1, result.fundsLoaded());
        Asset saved = firstSavedAsset();
        assertTrue(saved.getIsFund());
        assertEquals(102909L, saved.getCik());
    }

    @Test
    void mutualFundArrayShapeLoadsTicker() {
        when(webService.fetchSecTickers()).thenReturn(Map.of());
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                [
                  {"ticker": "VOO", "cik": "0000102909", "title": "Vanguard 500 ETF Shares"}
                ]
                """);
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(1, result.fundsLoaded());
        assertEquals("Vanguard 500 ETF Shares", firstSavedListing().getTitle());
    }

    @Test
    void tickerInBothFeedsKeepsEquityTitleAndIsMarkedFund() {
        // The fund index is the only source that identifies a ticker as a fund, but the company
        // index carries the better title -- the merge has to take one field from each.
        when(webService.fetchSecTickers()).thenReturn(Map.of(
                0, Map.of("ticker", "VOO", "cik_str", "102909", "title", "VANGUARD INDEX FUNDS")));
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "0": {"class_ticker": "VOO", "cik_str": "0000102909", "class_name": "ETF Shares"}
                }
                """);
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(1, result.fundsLoaded());
        assertEquals(0, result.equitiesLoaded());
        assertTrue(firstSavedAsset().getIsFund());
        assertEquals("VANGUARD INDEX FUNDS", firstSavedListing().getTitle());
    }

    @Test
    void listingFallsBackToTickerWhenTitleIsBlank() {
        when(webService.fetchSecTickers()).thenReturn(Map.of(
                0, Map.of("ticker", "ZZZZ", "cik_str", "1", "title", "  ")));
        when(webService.fetchSecMutualFundTickers()).thenReturn("{}");
        echoSavedAsset();

        service.load();

        assertEquals("ZZZZ", firstSavedListing().getTitle());
    }

    @Test
    void malformedFundJsonIsToleratedAndEquitiesStillLoad() {
        when(webService.fetchSecTickers()).thenReturn(Map.of(
                0, Map.of("ticker", "AAPL", "cik_str", "320193", "title", "Apple Inc.")));
        when(webService.fetchSecMutualFundTickers()).thenReturn("{ not json at all");
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(1, result.equitiesLoaded());
        assertEquals(0, result.fundsLoaded());
    }

    @Test
    void blankFundFeedIsTolerated() {
        when(webService.fetchSecTickers()).thenReturn(Map.of());
        when(webService.fetchSecMutualFundTickers()).thenReturn("");

        assertEquals(0, service.load().tickersLoaded());
    }

    @Test
    void propagatesSecFetchFailureSoCallersCanFailTheRun() {
        when(webService.fetchSecTickers()).thenThrow(new IllegalStateException("SEC 403"));

        assertThrows(IllegalStateException.class, () -> service.load());
        verify(assetService, never()).createOrUpdateAsset(any());
    }

    @Test
    void loadsBothFeedsAndCountsSeparately() {
        when(webService.fetchSecTickers()).thenReturn(Map.of(
                0, Map.of("ticker", "AAPL", "cik_str", "320193", "title", "Apple Inc."),
                1, Map.of("ticker", "MSFT", "cik_str", "789019", "title", "Microsoft Corp")));
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "0": {"ticker": "VOO", "cik": "102909", "title": "Vanguard 500"}
                }
                """);
        echoSavedAsset();

        SecTickerLoadService.SecTickerLoadResult result = service.load();

        assertEquals(2, result.equitiesLoaded());
        assertEquals(1, result.fundsLoaded());
        assertEquals(3, result.tickersLoaded());
        verify(listingService, org.mockito.Mockito.times(3)).createOrUpdateListing(any(Listing.class));
    }

    @Test
    void resultListsAreConsistent() {
        SecTickerLoadService.SecTickerLoadResult result =
                new SecTickerLoadService.SecTickerLoadResult(5, 2);
        assertEquals(7, result.tickersLoaded());
        assertEquals(List.of(5, 2), List.of(result.equitiesLoaded(), result.fundsLoaded()));
    }
}

package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexLoadServiceTest {

    @Mock
    private IndexMetricsRefreshService indexMetricsRefreshService;

    @Mock
    private FattoreIndexRebuildService fattore50IndexRebuildService;

    @Mock
    private FattoreIndexRebuildService fattore100IndexRebuildService;

    @Mock
    private FattoreIndexRebuildService fattore1000IndexRebuildService;

    private IndexLoadService service;

    @BeforeEach
    void setUp() {
        service = new IndexLoadService(
                indexMetricsRefreshService,
                fattore50IndexRebuildService,
                fattore100IndexRebuildService,
                fattore1000IndexRebuildService);
    }

    private static FattoreIndexRebuildService.RebuildResult result(String code) {
        return new FattoreIndexRebuildService.RebuildResult(code, 2026, 1, false, BigDecimal.ONE, List.of("AAPL"));
    }

    @Test
    void refreshRussell1000ScopeUsesRussellListings() {
        IndexMetricsRefreshService.RefreshResult expected =
                new IndexMetricsRefreshService.RefreshResult(10, 2, List.of("X:no_cik"));
        when(indexMetricsRefreshService.refreshRussell1000Listings(2026)).thenReturn(expected);

        assertEquals(expected, service.refresh("russell1000", 2026));
        verify(indexMetricsRefreshService, never()).refreshAllTickers(anyInt());
    }

    @Test
    void refreshIwbAliasAndCaseInsensitive() {
        IndexMetricsRefreshService.RefreshResult expected =
                new IndexMetricsRefreshService.RefreshResult(5, 0, List.of());
        when(indexMetricsRefreshService.refreshRussell1000Listings(2026)).thenReturn(expected);

        assertEquals(expected, service.refresh(" IWB ", 2026));
    }

    @Test
    void refreshAllScopeUsesAllTickers() {
        IndexMetricsRefreshService.RefreshResult expected =
                new IndexMetricsRefreshService.RefreshResult(20, 1, List.of("Y:no_price"));
        when(indexMetricsRefreshService.refreshAllTickers(2026)).thenReturn(expected);

        assertEquals(expected, service.refresh("all", 2026));
        verify(indexMetricsRefreshService, never()).refreshRussell1000Listings(anyInt());
    }

    @Test
    void refreshNullScopeDefaultsToRussell1000() {
        IndexMetricsRefreshService.RefreshResult expected =
                new IndexMetricsRefreshService.RefreshResult(3, 0, List.of());
        when(indexMetricsRefreshService.refreshRussell1000Listings(2026)).thenReturn(expected);

        assertEquals(expected, service.refresh(null, 2026));
    }

    @Test
    void refreshUnknownScopeThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.refresh("nope", 2026));
        verify(indexMetricsRefreshService, never()).refreshRussell1000Listings(anyInt());
        verify(indexMetricsRefreshService, never()).refreshAllTickers(anyInt());
    }

    @Test
    void rebuildAllRunsInCanonicalOrder() {
        when(fattore100IndexRebuildService.rebuild(2026)).thenReturn(result("FAT100"));
        when(fattore1000IndexRebuildService.rebuild(2026)).thenReturn(result("FAT1000"));
        when(fattore50IndexRebuildService.rebuild(2026)).thenReturn(result("FAT50"));

        List<FattoreIndexRebuildService.RebuildResult> results = service.rebuildAll(2026);

        assertEquals(List.of("FAT100", "FAT1000", "FAT50"),
                results.stream().map(FattoreIndexRebuildService.RebuildResult::indexCode).toList());
        InOrder order = inOrder(
                fattore100IndexRebuildService, fattore1000IndexRebuildService, fattore50IndexRebuildService);
        order.verify(fattore100IndexRebuildService).rebuild(2026);
        order.verify(fattore1000IndexRebuildService).rebuild(2026);
        order.verify(fattore50IndexRebuildService).rebuild(2026);
    }
}

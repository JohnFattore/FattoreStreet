package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.fattorestreet.sec_api.util.MarketTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexLoadRunnerTest {

    @Mock
    private IndexLoadService indexLoadService;

    @Mock
    private IndexMetricsRefreshService indexMetricsRefreshService;

    @Mock
    private ConfigurableApplicationContext context;

    private IndexLoadRunner runner;

    private final int currentYear = Year.now(MarketTime.MARKET).getValue();

    @BeforeEach
    void setUp() {
        runner = new IndexLoadRunner(indexLoadService, indexMetricsRefreshService, context);
        ReflectionTestUtils.setField(runner, "year", 0);
        ReflectionTestUtils.setField(runner, "scope", "russell1000");
        ReflectionTestUtils.setField(runner, "skipRefresh", false);
        ReflectionTestUtils.setField(runner, "ticker", "");
        ReflectionTestUtils.setField(runner, "minProcessed", 800);
    }

    private static IndexMetricsRefreshService.RefreshResult refreshResult(int processed) {
        return new IndexMetricsRefreshService.RefreshResult(processed, 3, List.of("X:no_cik"));
    }

    private static List<FattoreIndexRebuildService.RebuildResult> rebuildResults(boolean partial) {
        return List.of(
                new FattoreIndexRebuildService.RebuildResult("FAT100", 2026, 100, false, BigDecimal.TEN, List.of("MSFT")),
                new FattoreIndexRebuildService.RebuildResult("FAT1000", 2026, 900, partial, BigDecimal.TEN, List.of("GOOG")),
                new FattoreIndexRebuildService.RebuildResult("FAT50", 2026, 50, false, BigDecimal.TEN, List.of("AAPL")));
    }

    @Test
    void returnsZeroWhenRefreshMeetsThresholdAndRebuildsSucceed() {
        when(indexLoadService.refresh("russell1000", currentYear)).thenReturn(refreshResult(950));
        when(indexLoadService.rebuildAll(currentYear)).thenReturn(rebuildResults(false));

        assertEquals(0, runner.runLoad());
        verify(indexLoadService).rebuildAll(currentYear);
    }

    @Test
    void returnsZeroWhenRebuildIsPartial() {
        // FAT1000 is routinely partial (fewer eligible names than topN); that is not a failure.
        when(indexLoadService.refresh("russell1000", currentYear)).thenReturn(refreshResult(950));
        when(indexLoadService.rebuildAll(currentYear)).thenReturn(rebuildResults(true));

        assertEquals(0, runner.runLoad());
    }

    @Test
    void skipsRebuildAndReturnsOneWhenBelowMinProcessed() {
        when(indexLoadService.refresh("russell1000", currentYear)).thenReturn(refreshResult(200));

        assertEquals(1, runner.runLoad());
        verify(indexLoadService, never()).rebuildAll(anyInt());
    }

    @Test
    void skipRefreshRebuildsWithoutRefreshing() {
        ReflectionTestUtils.setField(runner, "skipRefresh", true);
        when(indexLoadService.rebuildAll(currentYear)).thenReturn(rebuildResults(false));

        assertEquals(0, runner.runLoad());
        verify(indexLoadService, never()).refresh(anyString(), anyInt());
        verify(indexMetricsRefreshService, never()).refreshSingleTicker(anyString(), anyInt());
    }

    @Test
    void explicitYearOverridesCurrentYear() {
        ReflectionTestUtils.setField(runner, "year", 2024);
        when(indexLoadService.refresh("russell1000", 2024)).thenReturn(refreshResult(950));
        when(indexLoadService.rebuildAll(2024)).thenReturn(rebuildResults(false));

        assertEquals(0, runner.runLoad());
        verify(indexLoadService).refresh("russell1000", 2024);
        verify(indexLoadService).rebuildAll(2024);
    }

    @Test
    void tickerModeUsesSingleTickerRefreshAndIgnoresMinProcessed() {
        ReflectionTestUtils.setField(runner, "ticker", "AAPL");
        when(indexMetricsRefreshService.refreshSingleTicker("AAPL", currentYear)).thenReturn(
                new IndexMetricsRefreshService.RefreshResult(1, 0, List.of()));
        when(indexLoadService.rebuildAll(currentYear)).thenReturn(rebuildResults(false));

        assertEquals(0, runner.runLoad());
        verify(indexLoadService, never()).refresh(anyString(), anyInt());
    }

    @Test
    void tickerModeReturnsOneWhenTickerNotProcessed() {
        ReflectionTestUtils.setField(runner, "ticker", "ZZZZBAD");
        when(indexMetricsRefreshService.refreshSingleTicker("ZZZZBAD", currentYear)).thenReturn(
                new IndexMetricsRefreshService.RefreshResult(0, 1, List.of("ZZZZBAD:unknown_ticker")));

        assertEquals(1, runner.runLoad());
        verify(indexLoadService, never()).rebuildAll(anyInt());
    }

    @Test
    void returnsOneWhenRefreshThrows() {
        when(indexLoadService.refresh("russell1000", currentYear)).thenThrow(new RuntimeException("boom"));

        assertEquals(1, runner.runLoad());
        verify(indexLoadService, never()).rebuildAll(anyInt());
    }

    @Test
    void returnsOneWhenRebuildThrows() {
        when(indexLoadService.refresh("russell1000", currentYear)).thenReturn(refreshResult(950));
        when(indexLoadService.rebuildAll(currentYear)).thenThrow(new RuntimeException("boom"));

        assertEquals(1, runner.runLoad());
    }
}

package com.fattorestreet.sec_api.fundamentals;

import java.time.Year;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundamentalsLoadRunnerTest {

    @Mock
    private EdgarService edgarService;

    @Mock
    private ConfigurableApplicationContext context;

    private FundamentalsLoadRunner runner;

    private final int currentYear = Year.now(MarketTime.MARKET).getValue();

    @BeforeEach
    void setUp() {
        runner = new FundamentalsLoadRunner(edgarService, context);
        ReflectionTestUtils.setField(runner, "yearsBack", 1);
        ReflectionTestUtils.setField(runner, "startYear", 0);
    }

    private static EdgarService.SyncResult syncResult(int startYear, int requests, int failures) {
        return new EdgarService.SyncResult(startYear, startYear, 500, 7L, 4000, requests, failures);
    }

    @Test
    void syncsTheYearsBackWindowAndReturnsZero() throws Exception {
        when(edgarService.syncFrames(currentYear - 1)).thenReturn(syncResult(currentYear - 1, 200, 3));

        assertEquals(0, runner.runLoad());
        verify(edgarService).syncFrames(currentYear - 1);
    }

    @Test
    void partialFrameFailuresStillReturnZero() throws Exception {
        // SEC 404s concept/period combinations nobody tagged; that is routine, not a failed run.
        when(edgarService.syncFrames(currentYear - 1)).thenReturn(syncResult(currentYear - 1, 200, 199));

        assertEquals(0, runner.runLoad());
    }

    @Test
    void returnsOneWhenEveryFrameRequestFailed() throws Exception {
        // What a missing SEC_CONTACT_EMAIL looks like: 403 on everything, nothing persisted.
        when(edgarService.syncFrames(currentYear - 1)).thenReturn(syncResult(currentYear - 1, 200, 200));

        assertEquals(1, runner.runLoad());
    }

    @Test
    void returnsZeroWhenNoFrameRequestsWereMade() throws Exception {
        when(edgarService.syncFrames(currentYear - 1)).thenReturn(syncResult(currentYear - 1, 0, 0));

        assertEquals(0, runner.runLoad());
    }

    @Test
    void explicitStartYearOverridesYearsBack() throws Exception {
        ReflectionTestUtils.setField(runner, "startYear", 2015);
        when(edgarService.syncFrames(2015)).thenReturn(syncResult(2015, 200, 0));

        assertEquals(0, runner.runLoad());
        verify(edgarService).syncFrames(2015);
    }

    @Test
    void startYearIsClampedToTheFramesEpoch() throws Exception {
        // Frames data does not exist before 2009, so asking for earlier just wastes SEC requests.
        ReflectionTestUtils.setField(runner, "startYear", 1998);
        when(edgarService.syncFrames(EdgarService.FRAMES_START_YEAR))
                .thenReturn(syncResult(EdgarService.FRAMES_START_YEAR, 200, 0));

        assertEquals(0, runner.runLoad());
        verify(edgarService).syncFrames(EdgarService.FRAMES_START_YEAR);
    }

    @Test
    void yearsBackOfZeroSyncsOnlyTheCurrentYear() throws Exception {
        ReflectionTestUtils.setField(runner, "yearsBack", 0);
        when(edgarService.syncFrames(currentYear)).thenReturn(syncResult(currentYear, 200, 0));

        assertEquals(0, runner.runLoad());
        verify(edgarService).syncFrames(currentYear);
    }

    @Test
    void returnsOneWhenSyncThrows() throws Exception {
        when(edgarService.syncFrames(anyInt())).thenThrow(new RuntimeException("boom"));

        assertEquals(1, runner.runLoad());
    }
}

package com.fattorestreet.sec_api.marketdata;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.fattorestreet.sec_api.corporateaction.PriceAdjustmentService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistLoadRunnerTest {

    @Mock
    private IexHistService iexHistService;

    @Mock
    private PriceAdjustmentService priceAdjustmentService;

    @Mock
    private ConfigurableApplicationContext context;

    /** What the runner passes when equity-only is off: no force, no fund filter, no yfinance. */
    private static final PriceAdjustmentService.AdjustmentOptions ALL_TICKERS =
            new PriceAdjustmentService.AdjustmentOptions(false, false, false, false);

    /** What the scheduled Fargate task passes (HIST_LOAD_EQUITY_ONLY=true): funds skipped. */
    private static final PriceAdjustmentService.AdjustmentOptions EQUITY_ONLY =
            new PriceAdjustmentService.AdjustmentOptions(false, false, true, false);

    private HistLoadRunner runner;

    @BeforeEach
    void setUp() {
        runner = new HistLoadRunner(iexHistService, priceAdjustmentService, context);
        ReflectionTestUtils.setField(runner, "days", 20);
        ReflectionTestUtils.setField(runner, "adjustEnabled", true);
        ReflectionTestUtils.setField(runner, "equityOnly", false);
    }

    private void stubSuccessfulLoad() throws Exception {
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 5, "skipped", 1, "notAvailable", 0, "errors", 0));
    }

    private void stubSuccessfulAdjustment() {
        when(priceAdjustmentService.adjustAllTickers(ALL_TICKERS)).thenReturn(
                Map.of("tickersProcessed", 5, "failedTickers", 0, "scheduledDetections", 1,
                        "jumpTriggeredDetections", 0, "totalPricesUpdated", 100, "totalSnappedActions", 0));
    }

    @Test
    void returnsZeroWhenDaysAreProcessed() throws Exception {
        stubSuccessfulLoad();
        stubSuccessfulAdjustment();

        assertEquals(0, runner.runLoad());
        verify(priceAdjustmentService).adjustAllTickers(ALL_TICKERS);
    }

    @Test
    void returnsZeroWhenEverythingSkipped() throws Exception {
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 0, "skipped", 20, "notAvailable", 0, "errors", 0));
        stubSuccessfulAdjustment();

        assertEquals(0, runner.runLoad());
    }

    @Test
    void returnsZeroOnPartialErrorsWhenSomethingProcessed() throws Exception {
        // Idempotent load: a partial failure should not fail the task, the next run retries.
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 3, "skipped", 0, "notAvailable", 0, "errors", 2));
        stubSuccessfulAdjustment();

        assertEquals(0, runner.runLoad());
    }

    @Test
    void returnsOneWhenEveryDayFailedAndSkipsAdjustment() throws Exception {
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 0, "skipped", 0, "notAvailable", 0, "errors", 4));

        assertEquals(1, runner.runLoad());
        verify(priceAdjustmentService, never()).adjustAllTickers(any());
    }

    @Test
    void returnsOneWhenLoadThrowsAndSkipsAdjustment() throws Exception {
        when(iexHistService.loadHistData(20)).thenThrow(new RuntimeException("boom"));

        assertEquals(1, runner.runLoad());
        verify(priceAdjustmentService, never()).adjustAllTickers(any());
    }

    @Test
    void returnsOneWhenAdjustmentThrows() throws Exception {
        stubSuccessfulLoad();
        when(priceAdjustmentService.adjustAllTickers(ALL_TICKERS)).thenThrow(new RuntimeException("adjust boom"));

        assertEquals(1, runner.runLoad());
    }

    @Test
    void passesEquityOnlyWhenConfigured() throws Exception {
        stubSuccessfulLoad();
        ReflectionTestUtils.setField(runner, "equityOnly", true);
        when(priceAdjustmentService.adjustAllTickers(EQUITY_ONLY)).thenReturn(
                Map.of("tickersProcessed", 3, "failedTickers", 0, "scheduledDetections", 1,
                        "jumpTriggeredDetections", 0, "totalPricesUpdated", 60, "totalSnappedActions", 0));

        assertEquals(0, runner.runLoad());
        verify(priceAdjustmentService).adjustAllTickers(EQUITY_ONLY);
    }

    @Test
    void skipsAdjustmentWhenDisabled() throws Exception {
        stubSuccessfulLoad();
        ReflectionTestUtils.setField(runner, "adjustEnabled", false);

        assertEquals(0, runner.runLoad());
        verify(priceAdjustmentService, never()).adjustAllTickers(any());
    }
}

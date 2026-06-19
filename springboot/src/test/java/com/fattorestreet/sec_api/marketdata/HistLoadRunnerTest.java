package com.fattorestreet.sec_api.marketdata;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistLoadRunnerTest {

    @Mock
    private IexHistService iexHistService;

    @Mock
    private ConfigurableApplicationContext context;

    private HistLoadRunner runner;

    @BeforeEach
    void setUp() {
        runner = new HistLoadRunner(iexHistService, context);
        ReflectionTestUtils.setField(runner, "days", 20);
    }

    @Test
    void returnsZeroWhenDaysAreProcessed() throws Exception {
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 5, "skipped", 1, "notAvailable", 0, "errors", 0));

        assertEquals(0, runner.runLoad());
    }

    @Test
    void returnsZeroWhenEverythingSkipped() throws Exception {
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 0, "skipped", 20, "notAvailable", 0, "errors", 0));

        assertEquals(0, runner.runLoad());
    }

    @Test
    void returnsZeroOnPartialErrorsWhenSomethingProcessed() throws Exception {
        // Idempotent load: a partial failure should not fail the task, the next run retries.
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 3, "skipped", 0, "notAvailable", 0, "errors", 2));

        assertEquals(0, runner.runLoad());
    }

    @Test
    void returnsOneWhenEveryDayFailed() throws Exception {
        when(iexHistService.loadHistData(20)).thenReturn(
                Map.of("processed", 0, "skipped", 0, "notAvailable", 0, "errors", 4));

        assertEquals(1, runner.runLoad());
    }

    @Test
    void returnsOneWhenLoadThrows() throws Exception {
        when(iexHistService.loadHistData(20)).thenThrow(new RuntimeException("boom"));

        assertEquals(1, runner.runLoad());
    }
}

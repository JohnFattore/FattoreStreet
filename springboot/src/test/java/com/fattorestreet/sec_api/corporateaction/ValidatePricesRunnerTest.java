package com.fattorestreet.sec_api.corporateaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import com.fattorestreet.sec_api.repository.IndexMemberRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidatePricesRunnerTest {

    private static final LocalDate MIN_DATE = LocalDate.of(2016, 1, 1);

    @Mock
    private AdjustedPriceValidationService adjustedPriceValidationService;

    @Mock
    private IndexMemberRepository indexMemberRepository;

    @Mock
    private ValidationReportPublisher validationReportPublisher;

    @Mock
    private ConfigurableApplicationContext context;

    /**
     * Not injected into the runner: these are the repositories the validation path touches, and the
     * licensing test below asserts nothing ever writes through them.
     */
    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private CorporateActionRepository corporateActionRepository;

    private ValidatePricesRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ValidatePricesRunner(
                adjustedPriceValidationService, indexMemberRepository, validationReportPublisher, context);
        ReflectionTestUtils.setField(runner, "indexCode", "FAT1000");
        ReflectionTestUtils.setField(runner, "minDate", "2016-01-01");
        ReflectionTestUtils.setField(runner, "snsTopicArn", "arn:aws:sns:us-east-1:1:reports");
        ReflectionTestUtils.setField(runner, "maxTickers", 0);
    }

    private static AdjustedPriceValidationService.BatchSummary summary() {
        return new AdjustedPriceValidationService.BatchSummary(
                2,
                1,
                1,
                3,
                List.of(Map.of("ticker", "AAPL", "maxAbsDeviation", 0.02, "datesOverThreshold", 4, "breaks", 1)),
                List.of(Map.of("ticker", "AAPL", "date", "2024-06-10", "step", -0.5, "nearestAction", Map.of())));
    }

    private void withMembers(String... tickers) {
        when(indexMemberRepository.findTickersByIndexCodeOrderByPercentDesc("FAT1000"))
                .thenReturn(List.of(tickers));
    }

    // @spec PRICE-VAL-031
    @Test
    void publishesReportAndReturnsZeroOnSuccess() {
        withMembers("AAPL", "MSFT", "GOOG");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());

        assertEquals(0, runner.runValidation());

        ArgumentCaptor<ValidationReportPublisher.ValidationReport> captor =
                ArgumentCaptor.forClass(ValidationReportPublisher.ValidationReport.class);
        verify(validationReportPublisher).publish(eq("arn:aws:sns:us-east-1:1:reports"), captor.capture());
        ValidationReportPublisher.ValidationReport report = captor.getValue();
        assertEquals("FAT1000", report.indexCode());
        assertEquals(MIN_DATE, report.minDate());
        assertEquals(2, report.tickersChecked());
        assertEquals(1, report.tickersSkipped());
        assertEquals(1, report.tickersOutOfTolerance());
        assertEquals(3, report.totalBreaks());
    }

    // @spec PRICE-VAL-025
    @Test
    void returnsOneWhenIndexHasNoMembers() {
        // A silent "0 tickers checked, all healthy" report would be worse than a failed task.
        when(indexMemberRepository.findTickersByIndexCodeOrderByPercentDesc("FAT1000")).thenReturn(List.of());

        assertEquals(1, runner.runValidation());
        verify(adjustedPriceValidationService, never()).validateBatch(anyList(), any());
        verifyNoInteractions(validationReportPublisher);
    }

    // @spec PRICE-VAL-030
    @Test
    void returnsOneWhenPublishFails() {
        withMembers("AAPL");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());
        org.mockito.Mockito.doThrow(new RuntimeException("sns down"))
                .when(validationReportPublisher).publish(anyString(), any());

        assertEquals(1, runner.runValidation());
    }

    // @spec PRICE-VAL-039
    @Test
    void returnsOneWhenValidationThrows() {
        withMembers("AAPL");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE)))
                .thenThrow(new RuntimeException("boom"));

        assertEquals(1, runner.runValidation());
        verifyNoInteractions(validationReportPublisher);
    }

    // @spec PRICE-VAL-026
    @Test
    void returnsOneWhenMinDateIsNotAnIsoDate() {
        ReflectionTestUtils.setField(runner, "minDate", "last tuesday");

        assertEquals(1, runner.runValidation());
        verifyNoInteractions(indexMemberRepository, adjustedPriceValidationService, validationReportPublisher);
    }

    // @spec PRICE-VAL-029
    @Test
    void blankTopicLogsInsteadOfPublishingAndStillSucceeds() {
        ReflectionTestUtils.setField(runner, "snsTopicArn", "  ");
        withMembers("AAPL");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());
        when(validationReportPublisher.render(any())).thenReturn("rendered report");

        assertEquals(0, runner.runValidation());
        verify(validationReportPublisher, never()).publish(anyString(), any());
        verify(validationReportPublisher).render(any());
    }

    // @spec PRICE-VAL-027, PRICE-VAL-028
    @Test
    void maxTickersCapsTheScopeToTheHeaviestMembers() {
        ReflectionTestUtils.setField(runner, "maxTickers", 2);
        withMembers("AAPL", "MSFT", "GOOG", "AMZN");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());

        assertEquals(0, runner.runValidation());

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.captor();
        verify(adjustedPriceValidationService).validateBatch(captor.capture(), eq(MIN_DATE));
        // The repository returns heaviest-weight-first, so capping takes the largest names.
        assertEquals(List.of("AAPL", "MSFT"), captor.getValue());
    }

    // @spec PRICE-VAL-028
    @Test
    void maxTickersLargerThanScopeValidatesEverything() {
        ReflectionTestUtils.setField(runner, "maxTickers", 50);
        withMembers("AAPL", "MSFT");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());

        assertEquals(0, runner.runValidation());

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.captor();
        verify(adjustedPriceValidationService).validateBatch(captor.capture(), eq(MIN_DATE));
        assertEquals(List.of("AAPL", "MSFT"), captor.getValue());
    }

    @Test
    void indexCodeIsNormalizedToUppercase() {
        ReflectionTestUtils.setField(runner, "indexCode", " fat50 ");
        when(indexMemberRepository.findTickersByIndexCodeOrderByPercentDesc("FAT50"))
                .thenReturn(List.of("AAPL"));
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());

        assertEquals(0, runner.runValidation());
        verify(indexMemberRepository).findTickersByIndexCodeOrderByPercentDesc("FAT50");
    }

    /**
     * FR-007: the validation path compares against reference data that may never be persisted, so a
     * write from this runner would be a licensing violation, not just a bug. Asserted rather than
     * left to convention.
     */
    // @spec PRICE-VAL-001, PRICE-VAL-004
    @Test
    void neverWritesToTheDatabase() {
        withMembers("AAPL", "MSFT");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());

        assertEquals(0, runner.runValidation());

        verifyNoInteractions(dailyPriceRepository, corporateActionRepository);
        verify(indexMemberRepository, never()).save(any());
        verify(indexMemberRepository, never()).saveAll(anyList());
        verify(indexMemberRepository, never()).delete(any());
        verify(indexMemberRepository, never()).deleteAll();
        verify(indexMemberRepository, never()).deleteByMarketIndex_Code(anyString());
        // The runner reaches the database only through this one read-only projection.
        verify(indexMemberRepository).findTickersByIndexCodeOrderByPercentDesc("FAT1000");
        org.mockito.Mockito.verifyNoMoreInteractions(indexMemberRepository);
    }

    // @spec PRICE-VAL-031
    @Test
    void perTickerFailuresAreTheServicesConcernNotAFatalError() {
        // tickersSkipped > 0 still exits 0: a partial report beats no report.
        withMembers("AAPL", "MSFT", "GOOG");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE)))
                .thenReturn(new AdjustedPriceValidationService.BatchSummary(
                        1, 2, 0, 0, List.of(), List.of()));

        assertEquals(0, runner.runValidation());
        verify(validationReportPublisher).publish(anyString(), any());
    }

    // @spec PRICE-VAL-002
    @Test
    void reportCarriesOnlyDerivedStatistics() {
        withMembers("AAPL");
        when(adjustedPriceValidationService.validateBatch(anyList(), eq(MIN_DATE))).thenReturn(summary());

        assertEquals(0, runner.runValidation());

        ArgumentCaptor<ValidationReportPublisher.ValidationReport> captor =
                ArgumentCaptor.forClass(ValidationReportPublisher.ValidationReport.class);
        verify(validationReportPublisher).publish(anyString(), captor.capture());
        ValidationReportPublisher.ValidationReport report = captor.getValue();
        // Deviations and step sizes only -- no reference price series is carried through.
        assertTrue(report.worstTickers().stream().allMatch(row -> row.containsKey("maxAbsDeviation")));
        assertTrue(report.durationMs() >= 0);
    }
}

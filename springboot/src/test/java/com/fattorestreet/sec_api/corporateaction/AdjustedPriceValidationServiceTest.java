package com.fattorestreet.sec_api.corporateaction;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.corporateaction.AdjustedPriceValidationService.PriceValidationReport;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdjustedPriceValidationServiceTest {

    private static final String TICKER = "AAPL";
    private static final LocalDate MIN_DATE = LocalDate.of(2016, 1, 1);

    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private CorporateActionRepository corporateActionRepository;
    @Mock
    private HttpClient httpClient;

    private AdjustedPriceValidationService service;

    @BeforeEach
    void setUp() {
        service = new AdjustedPriceValidationService(
                dailyPriceRepository, corporateActionRepository, new ObjectMapper(),
                "http://django.test/portfolio/", httpClient);
    }

    @SuppressWarnings("unchecked")
    private void stubDjango(String pricesJson) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpResponse<String> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(200);
                    when(response.body()).thenReturn(pricesJson);
                    return response;
                });
    }

    private static DailyPrice price(LocalDate date, Double adjustedClose) {
        DailyPrice dp = new DailyPrice();
        dp.setTicker(TICKER);
        dp.setTradeDate(date);
        dp.setClosePrice(adjustedClose);
        dp.setAdjustedClose(adjustedClose);
        return dp;
    }

    /** Builds a JSON array of {date, value} rows mirroring the Django asset-prices payload. */
    private static String pricesJson(Map<LocalDate, Double> rows) {
        List<String> parts = new ArrayList<>();
        rows.forEach((date, value) ->
                parts.add(String.format("{\"date\": \"%s\", \"value\": %s}", date, value)));
        return "[" + String.join(",", parts) + "]";
    }

    // @spec PRICE-VAL-005, PRICE-VAL-009
    @Test
    void identicalSeriesProducesZeroDeviation() throws Exception {
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        LocalDate d3 = LocalDate.of(2024, 1, 4);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(d1, 100.0), price(d2, 101.0), price(d3, 102.0)));
        stubDjango(pricesJson(Map.of(d1, 100.0, d2, 101.0, d3, 102.0)));
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals("ok", report.status());
        assertEquals(3, report.commonDates());
        assertEquals(0.0, report.maxAbsDeviation());
        assertEquals(0, report.datesOverThreshold());
        assertTrue(report.breaks().isEmpty());
    }

    // @spec PRICE-VAL-007
    @Test
    void constantLevelOffsetIsNormalizedAway() throws Exception {
        // Our series is uniformly 2% above the reference: after anchoring at the latest
        // common date there is no deviation, because no event difference exists.
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(d1, 102.0), price(d2, 204.0)));
        stubDjango(pricesJson(Map.of(d1, 100.0, d2, 200.0)));
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(0.0, report.maxAbsDeviation());
        assertTrue(report.breaks().isEmpty());
    }

    // @spec PRICE-VAL-011
    @Test
    void missingSplitShowsBreakAtSplitDate() throws Exception {
        // Reference halves historical prices for a 2:1 split on d3; our series never
        // applied it, so all dates before d3 deviate ~100% and the ratio steps at d3.
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        LocalDate d3 = LocalDate.of(2024, 1, 4);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(d1, 100.0), price(d2, 100.0), price(d3, 50.0)));
        stubDjango(pricesJson(Map.of(d1, 50.0, d2, 50.0, d3, 50.0)));
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1.0, report.maxAbsDeviation(), 0.0001);
        assertEquals(2, report.datesOverThreshold());
        assertEquals(1, report.breaks().size());
        assertEquals(d3.toString(), report.breaks().get(0).get("date"));
        assertNull(report.breaks().get(0).get("nearestAction"));
    }

    // @spec PRICE-VAL-012
    @Test
    void breakReportsNearbyStoredAction() throws Exception {
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(d1, 100.0), price(d2, 50.0)));
        stubDjango(pricesJson(Map.of(d1, 50.0, d2, 50.0)));
        CorporateAction split = new CorporateAction();
        split.setTicker(TICKER);
        split.setActionType(ActionType.SPLIT);
        split.setEffectiveDate(d2.plusDays(2));
        split.setRatio(0.5);
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(split));

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1, report.breaks().size());
        @SuppressWarnings("unchecked")
        Map<String, Object> nearest = (Map<String, Object>) report.breaks().get(0).get("nearestAction");
        assertEquals("SPLIT", nearest.get("actionType"));
        assertEquals(2L, nearest.get("daysFromBreak"));
    }

    // @spec PRICE-VAL-006
    @Test
    void nullAdjustedRowsAreSkipped() throws Exception {
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        DailyPrice unadjusted = price(d2, null);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(d1, 100.0), unadjusted));

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        // Only one adjusted row remains, so overlap is insufficient for a comparison.
        assertEquals("insufficient_overlap", report.status());
    }

    // @spec PRICE-VAL-015
    @Test
    void noAdjustedPricesShortCircuitsBeforeFetch() {
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of());

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals("no_adjusted_prices", report.status());
        verifyNoInteractions(httpClient);
    }

    // @spec PRICE-VAL-017
    @Test
    void referenceFetchFailureDegradesToNoReference() throws Exception {
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(d1, 100.0), price(d2, 101.0)));
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenAnswer(invocation -> {
                    HttpResponse<String> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(500);
                    return response;
                });

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals("no_reference_data", report.status());
    }

    // @spec PRICE-VAL-005
    @Test
    void minDateFiltersBothSeries() throws Exception {
        LocalDate old = LocalDate.of(2015, 6, 1);
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(old, 10.0), price(d1, 100.0), price(d2, 101.0)));
        stubDjango(pricesJson(Map.of(old, 999.0, d1, 100.0, d2, 101.0)));
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(2, report.commonDates());
        assertEquals(0.0, report.maxAbsDeviation());
    }

    // @spec PRICE-VAL-003
    @Test
    void stringDecimalValuesFromDjangoAreParsed() throws Exception {
        // DRF serializes Decimal as a JSON string by default.
        LocalDate d1 = LocalDate.of(2024, 1, 2);
        LocalDate d2 = LocalDate.of(2024, 1, 3);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(List.of(
                price(d1, 100.0), price(d2, 101.0)));
        stubDjango("[{\"date\": \"2024-01-02\", \"value\": \"100.0\"}, {\"date\": \"2024-01-03\", \"value\": \"101.0\"}]");
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());

        PriceValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals("ok", report.status());
        assertEquals(0.0, report.maxAbsDeviation());
    }

    // @spec PRICE-VAL-019, PRICE-VAL-020
    @Test
    void summarizeBatchRanksWorstTickersAndCountsStatuses() {
        PriceValidationReport clean = new PriceValidationReport(
                "AAPL", "ok", 100, MIN_DATE, MIN_DATE.plusDays(100), 0.0001, 0.001, 0, List.of());
        PriceValidationReport broken = new PriceValidationReport(
                "MSFT", "ok", 100, MIN_DATE, MIN_DATE.plusDays(100), 0.4, 0.9, 50,
                List.of(Map.of("ticker", "MSFT", "date", "2024-06-10", "step", 0.5)));
        PriceValidationReport noRef = PriceValidationReport.empty("ZZZZ", "no_reference_data");

        Map<String, Object> summary = service.summarizeBatch(List.of(clean, broken, noRef));

        assertEquals(2, summary.get("tickersValidated"));
        assertEquals(1, summary.get("tickersWithoutReference"));
        assertEquals(1, summary.get("tickersOverThreshold"));
        assertEquals(1, summary.get("totalBreaks"));
        List<?> worst = (List<?>) summary.get("worstTickers");
        assertEquals("MSFT", ((Map<?, ?>) worst.get(0)).get("ticker"));
    }

    // @spec PRICE-VAL-019
    @Test
    void summarizeExposesTheSameCountsAsTheMapShape() {
        PriceValidationReport clean = new PriceValidationReport(
                "AAPL", "ok", 100, MIN_DATE, MIN_DATE.plusDays(100), 0.0001, 0.001, 0, List.of());
        PriceValidationReport noRef = PriceValidationReport.empty("ZZZZ", "no_reference_data");

        AdjustedPriceValidationService.BatchSummary summary = service.summarize(List.of(clean, noRef));

        assertEquals(1, summary.tickersChecked());
        assertEquals(1, summary.tickersSkipped());
        assertEquals(0, summary.tickersOutOfTolerance());
        assertEquals(0, summary.totalBreaks());
        assertEquals(summary.toMap(), service.summarizeBatch(List.of(clean, noRef)));
    }

    // @spec PRICE-VAL-019
    @Test
    void validateBatchWalksEveryTickerAndAggregates() throws Exception {
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(any()))
                .thenReturn(List.of(price(LocalDate.of(2024, 1, 2), 100.0), price(LocalDate.of(2024, 1, 3), 101.0)));
        stubDjango("[{\"date\": \"2024-01-02\", \"value\": \"100.0\"}, {\"date\": \"2024-01-03\", \"value\": \"101.0\"}]");
        when(corporateActionRepository.findByTicker(any())).thenReturn(List.of());

        AdjustedPriceValidationService.BatchSummary summary =
                service.validateBatch(List.of("AAPL", "MSFT"), MIN_DATE);

        assertEquals(2, summary.tickersChecked());
        assertEquals(0, summary.tickersSkipped());
        verify(dailyPriceRepository).findByTickerOrderByTradeDateAsc("AAPL");
        verify(dailyPriceRepository).findByTickerOrderByTradeDateAsc("MSFT");
    }

    // @spec PRICE-VAL-018
    @Test
    void validateBatchCountsAFailedTickerAsSkippedAndKeepsGoing() {
        // A partial report is more useful than none, so one bad ticker must not abort the batch.
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc("AAPL"))
                .thenThrow(new IllegalStateException("db hiccup"));
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc("MSFT")).thenReturn(List.of());

        AdjustedPriceValidationService.BatchSummary summary =
                service.validateBatch(List.of("AAPL", "MSFT"), MIN_DATE);

        assertEquals(0, summary.tickersChecked());
        // MSFT has no stored prices (skipped by status), AAPL threw -- both count as skipped.
        assertEquals(2, summary.tickersSkipped());
    }

    // @spec PRICE-VAL-019
    @Test
    void validateBatchOverAnEmptyListIsAnEmptySummary() {
        AdjustedPriceValidationService.BatchSummary summary = service.validateBatch(List.of(), MIN_DATE);

        assertEquals(0, summary.tickersChecked());
        assertEquals(0, summary.tickersSkipped());
        assertTrue(summary.worstTickers().isEmpty());
        assertTrue(summary.breaks().isEmpty());
        verifyNoInteractions(dailyPriceRepository);
    }

    // @spec PRICE-VAL-001
    @Test
    void validateBatchNeverWritesThroughItsRepositories() {
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc("AAPL")).thenReturn(List.of());

        service.validateBatch(List.of("AAPL"), MIN_DATE);

        verify(dailyPriceRepository, never()).save(any());
        verify(dailyPriceRepository, never()).saveAll(any());
        verify(dailyPriceRepository, never()).delete(any());
        verify(corporateActionRepository, never()).save(any());
        verify(corporateActionRepository, never()).saveAll(any());
        verify(corporateActionRepository, never()).delete(any());
    }
}

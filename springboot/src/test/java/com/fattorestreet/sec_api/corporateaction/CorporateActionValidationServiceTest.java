package com.fattorestreet.sec_api.corporateaction;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.corporateaction.CorporateActionValidationService.ValidationReport;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.CorporateAction.SourceType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorporateActionValidationServiceTest {

    private static final String TICKER = "AAPL";
    private static final LocalDate MIN_DATE = LocalDate.of(2024, 1, 1);

    @Mock
    private CorporateActionRepository corporateActionRepository;
    @Mock
    private HttpClient httpClient;

    private CorporateActionValidationService service;

    @BeforeEach
    void setUp() {
        service = new CorporateActionValidationService(
                corporateActionRepository, new ObjectMapper(), "http://django.test/portfolio/", httpClient);
    }

    /** Stubs the Django dividends and splits endpoints with the given JSON payloads. */
    @SuppressWarnings("unchecked")
    private void stubDjango(String dividendsJson, String splitsJson) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    HttpResponse<String> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(200);
                    when(response.body()).thenReturn(
                            request.uri().toString().contains("asset-dividends") ? dividendsJson : splitsJson);
                    return response;
                });
    }

    @SuppressWarnings("unchecked")
    private void stubDjangoFailure(int statusCode) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpResponse<String> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(statusCode);
                    return response;
                });
    }

    private static CorporateAction dividend(LocalDate date, Double adjusted, double ratio) {
        CorporateAction a = new CorporateAction();
        a.setTicker(TICKER);
        a.setActionType(ActionType.DIVIDEND);
        a.setEffectiveDate(date);
        a.setRatio(ratio);
        a.setAdjustedDividend(adjusted);
        a.setSourceType(SourceType.SEC_EQUITY_XBRL);
        return a;
    }

    private static CorporateAction split(LocalDate date, double ratio) {
        CorporateAction a = new CorporateAction();
        a.setTicker(TICKER);
        a.setActionType(ActionType.SPLIT);
        a.setEffectiveDate(date);
        a.setRatio(ratio);
        return a;
    }

    // @spec PRICE-VAL-023
    @Test
    void matchingEventsProduceCleanReport() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                dividend(LocalDate.of(2024, 2, 9), 0.24, 0.24)));
        stubDjango("[{\"date\": \"2024-02-09\", \"amount\": 0.24}]", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1, report.secEventCount());
        assertEquals(1, report.yfEventCount());
        assertEquals(0, report.missingInSec());
        assertEquals(0, report.extraInSec());
        assertEquals(0, report.dateDrift());
        assertEquals(0, report.amountDrift());
        assertTrue(report.mismatchSamples().isEmpty());
    }

    // @spec PRICE-VAL-023
    @Test
    void djangoOnlyEventCountsAsMissingInSec() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());
        stubDjango("[{\"date\": \"2024-02-09\", \"amount\": 0.24}]", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1, report.missingInSec());
        assertEquals("missing_in_sec", report.mismatchSamples().get(0).get("category"));
        assertEquals("high", report.mismatchSamples().get(0).get("severity"));
    }

    // @spec PRICE-VAL-023
    @Test
    void secOnlyEventCountsAsExtraInSec() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                dividend(LocalDate.of(2024, 2, 9), 0.24, 0.24)));
        stubDjango("[]", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1, report.extraInSec());
        assertEquals("extra_in_sec", report.mismatchSamples().get(0).get("category"));
    }

    // @spec PRICE-VAL-024
    @Test
    void amountGapBeyondToleranceIsAmountDrift() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                dividend(LocalDate.of(2024, 2, 9), 0.30, 0.30)));
        stubDjango("[{\"date\": \"2024-02-09\", \"amount\": 0.24}]", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1, report.amountDrift());
        assertEquals(0, report.missingInSec());
        assertEquals("amount_drift", report.mismatchSamples().get(0).get("category"));
    }

    // @spec PRICE-VAL-023
    @Test
    void dateGapWithinToleranceIsDateDrift() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                dividend(LocalDate.of(2024, 2, 12), 0.24, 0.24)));
        stubDjango("[{\"date\": \"2024-02-09\", \"amount\": 0.24}]", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1, report.dateDrift());
        assertEquals(0, report.missingInSec());
        assertEquals(0, report.extraInSec());
    }

    // @spec PRICE-VAL-023
    @Test
    void minDateFiltersOldEventsOnBothSides() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                dividend(LocalDate.of(2023, 11, 10), 0.24, 0.24)));
        stubDjango("[{\"date\": \"2023-11-10\", \"amount\": 0.24}]", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(0, report.secEventCount());
        assertEquals(0, report.yfEventCount());
    }

    // @spec PRICE-VAL-003
    @Test
    void djangoHttpErrorDegradesToEmptyReference() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                dividend(LocalDate.of(2024, 2, 9), 0.24, 0.24)));
        stubDjangoFailure(500);

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(0, report.yfEventCount());
        assertEquals(1, report.extraInSec());
    }

    // @spec PRICE-VAL-024
    @Test
    void yfinanceSplitMultiplierIsNormalizedToSecRatio() throws Exception {
        // SEC stores 4:1 as 0.25; yfinance reports it as 4.0 — they must match after normalization.
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                split(LocalDate.of(2024, 6, 10), 0.25)));
        stubDjango("[]", "[{\"date\": \"2024-06-10\", \"value\": 4.0}]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(0, report.missingInSec());
        assertEquals(0, report.extraInSec());
        assertEquals(0, report.amountDrift());
    }

    // @spec PRICE-VAL-003
    @Test
    void dividendFallsBackToValueFieldWhenAmountMissing() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(
                dividend(LocalDate.of(2024, 2, 9), 0.24, 0.24)));
        stubDjango("[{\"date\": \"2024-02-09\", \"value\": 0.24}]", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(1, report.yfEventCount());
        assertEquals(0, report.missingInSec());
    }

    // @spec PRICE-VAL-003
    @Test
    void interruptedFetchRestoresInterruptFlagAndDegrades() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new InterruptedException("interrupted"));

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(0, report.yfEventCount());
        assertTrue(Thread.interrupted(), "interrupt flag should be restored (and cleared here)");
    }

    // @spec PRICE-VAL-003
    @Test
    void nonArrayDjangoPayloadIsTreatedAsFetchFailure() throws Exception {
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of());
        stubDjango("{\"detail\": \"not a list\"}", "[]");

        ValidationReport report = service.validateTicker(TICKER, MIN_DATE);

        assertEquals(0, report.yfEventCount());
    }

    // @spec PRICE-VAL-019
    @Test
    void summarizeBatchAggregatesCountsAndMismatchRate() throws Exception {
        ValidationReport clean = new ValidationReport(TICKER, 2, 2, 0, 0, 0, 0, List.of());
        ValidationReport dirty = new ValidationReport("MSFT", 1, 2, 1, 0, 0, 1,
                List.of(Map.of("ticker", "MSFT", "category", "missing_in_sec")));

        Map<String, Object> summary = service.summarizeBatch(List.of(clean, dirty));

        assertEquals(2, summary.get("tickersValidated"));
        assertEquals(3, summary.get("secEvents"));
        assertEquals(4, summary.get("yfinanceEvents"));
        assertEquals(1, summary.get("missingInSec"));
        assertEquals(1, summary.get("amountDrift"));
        // (missing 1 + dateDrift 0 + amountDrift 1) / 4 yf events
        assertEquals(0.5, summary.get("mismatchRate"));
        assertEquals(1, ((List<?>) summary.get("sampleMismatches")).size());
    }

    // @spec PRICE-VAL-019
    @Test
    void summarizeBatchWithNoYfEventsHasZeroMismatchRate() {
        ValidationReport empty = new ValidationReport(TICKER, 1, 0, 0, 1, 0, 0, List.of());

        Map<String, Object> summary = service.summarizeBatch(List.of(empty));

        assertEquals(0.0, summary.get("mismatchRate"));
    }

    // @spec PRICE-VAL-003
    @Test
    void requestUrlsEncodeTickerAndTrimBaseSlash() throws Exception {
        when(corporateActionRepository.findByTicker("BRK.B")).thenReturn(List.of());
        stubDjango("[]", "[]");

        service.validateTicker("BRK.B", MIN_DATE);

        org.mockito.ArgumentCaptor<HttpRequest> captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        assertEquals("http://django.test/portfolio/api/asset-dividends/?ticker=BRK.B",
                captor.getAllValues().get(0).uri().toString());
        assertEquals("http://django.test/portfolio/api/asset-splits/?ticker=BRK.B",
                captor.getAllValues().get(1).uri().toString());
    }
}

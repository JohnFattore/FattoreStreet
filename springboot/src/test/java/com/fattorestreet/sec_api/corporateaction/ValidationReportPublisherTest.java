package com.fattorestreet.sec_api.corporateaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationReportPublisherTest {

    private static final LocalDate MIN_DATE = LocalDate.of(2016, 1, 1);

    @Mock
    private SnsClient snsClient;

    private ValidationReportPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ValidationReportPublisher(() -> snsClient);
    }

    private static ValidationReportPublisher.ValidationReport report(
            int checked, int skipped, int outOfTolerance, int totalBreaks,
            List<Map<String, Object>> worst, List<Map<String, Object>> breaks) {
        return new ValidationReportPublisher.ValidationReport(
                "FAT1000", MIN_DATE, 723_000L, checked, skipped, outOfTolerance, totalBreaks, worst, breaks);
    }

    private static Map<String, Object> worstRow(String ticker, double deviation) {
        return Map.of("ticker", ticker, "maxAbsDeviation", deviation, "datesOverThreshold", 4, "breaks", 1);
    }

    private static Map<String, Object> breakRow(String ticker, String date) {
        return Map.of(
                "ticker", ticker,
                "date", date,
                "step", -0.4998,
                "nearestAction", Map.of(
                        "actionType", "SPLIT",
                        "effectiveDate", date,
                        "ratio", 2.0,
                        "daysFromBreak", 0L));
    }

    @Test
    void rendersHeadlineCountsAndScope() {
        String body = publisher.render(report(984, 16, 7, 23,
                List.of(worstRow("AAPL", 0.021)), List.of(breakRow("AAPL", "2024-06-10"))));

        assertTrue(body.contains("FAT1000"), body);
        assertTrue(body.contains("2016-01-01"), body);
        assertTrue(body.contains("Tickers checked:    984"), body);
        assertTrue(body.contains("Tickers skipped:    16"), body);
        assertTrue(body.contains("Out of tolerance:   7"), body);
        assertTrue(body.contains("Ratio breaks found: 23"), body);
        assertTrue(body.contains("12m 3s"), body);
    }

    @Test
    void rendersWorstTickersAndBreakDetail() {
        String body = publisher.render(report(100, 0, 1, 1,
                List.of(worstRow("AAPL", 0.021)), List.of(breakRow("AAPL", "2024-06-10"))));

        assertTrue(body.contains("AAPL"), body);
        assertTrue(body.contains("maxAbsDeviation=0.021"), body);
        assertTrue(body.contains("2024-06-10"), body);
        assertTrue(body.contains("nearestAction=SPLIT"), body);
    }

    @Test
    void announcesOmittedBreaksRatherThanTruncatingSilently() {
        List<Map<String, Object>> breaks = List.of(breakRow("AAPL", "2024-06-10"), breakRow("MSFT", "2024-07-01"));

        String body = publisher.render(report(100, 0, 2, 57, List.of(worstRow("AAPL", 0.021)), breaks));

        assertTrue(body.contains("showing 2 of 57"), body);
        assertTrue(body.contains("... 55 more omitted"), body);
    }

    @Test
    void omitsTheOmittedLineWhenEverythingIsShown() {
        String body = publisher.render(report(100, 0, 1, 1,
                List.of(worstRow("AAPL", 0.021)), List.of(breakRow("AAPL", "2024-06-10"))));

        assertFalse(body.contains("more omitted"), body);
    }

    @Test
    void cleanRunRendersWithoutBreakSection() {
        String body = publisher.render(report(500, 0, 0, 0, List.of(worstRow("AAPL", 0.0001)), List.of()));

        assertTrue(body.contains("No ratio breaks detected."), body);
    }

    @Test
    void emptyScopeStillRendersSomethingReadable() {
        String body = publisher.render(report(0, 12, 0, 0, List.of(), List.of()));

        assertTrue(body.contains("No tickers produced a comparable series."), body);
    }

    @Test
    void breakWithNoNearbyActionIsLabelledNone() {
        Map<String, Object> orphan = new java.util.HashMap<>();
        orphan.put("ticker", "XYZ");
        orphan.put("date", "2024-02-02");
        orphan.put("step", 0.3);
        orphan.put("nearestAction", null);

        String body = publisher.render(report(10, 0, 1, 1, List.of(), List.of(orphan)));

        assertTrue(body.contains("nearestAction=none"), body);
    }

    @Test
    void bodyStaysUnderTheSnsSizeLimitAndSaysSoWhenTruncated() {
        // Far more rows than the service ever emits, to exercise the hard guard.
        List<Map<String, Object>> manyBreaks = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            manyBreaks.add(breakRow("TICKER" + i, "2024-06-10"));
        }

        String body = publisher.render(report(1000, 0, 900, 9000, List.of(), manyBreaks));

        assertTrue(body.contains("report truncated"), "expected an explicit truncation notice");
        assertTrue(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 256 * 1024,
                "rendered body must fit an SNS message");
    }

    @Test
    void publishSendsSubjectAndBodyToTheConfiguredTopic() {
        publisher.publish("arn:aws:sns:us-east-1:1:reports", report(984, 16, 7, 23,
                List.of(worstRow("AAPL", 0.021)), List.of(breakRow("AAPL", "2024-06-10"))));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        PublishRequest request = captor.getValue();
        assertEquals("arn:aws:sns:us-east-1:1:reports", request.topicArn());
        assertTrue(request.subject().contains("FAT1000"), request.subject());
        assertTrue(request.subject().length() <= 100, request.subject());
        assertTrue(request.message().contains("Tickers checked:    984"), request.message());
    }

    @Test
    void publishPropagatesFailureSoTheTaskCanFail() {
        when(snsClient.publish(org.mockito.ArgumentMatchers.any(PublishRequest.class)))
                .thenThrow(new IllegalStateException("sns unavailable"));

        assertThrows(IllegalStateException.class, () -> publisher.publish(
                "arn:aws:sns:us-east-1:1:reports", report(1, 0, 0, 0, List.of(), List.of())));
    }

    @Test
    void subjectIsClippedToTheSnsLimit() {
        ValidationReportPublisher.ValidationReport longScope = new ValidationReportPublisher.ValidationReport(
                "A".repeat(150), MIN_DATE, 1000L, 1, 0, 0, 0, List.of(), List.of());

        assertEquals(100, publisher.subject(longScope).length());
    }

    @Test
    void reportFactoryCopiesEveryBatchSummaryField() {
        AdjustedPriceValidationService.BatchSummary summary =
                new AdjustedPriceValidationService.BatchSummary(
                        11, 2, 3, 44, List.of(worstRow("AAPL", 0.5)), List.of(breakRow("AAPL", "2024-06-10")));

        ValidationReportPublisher.ValidationReport built =
                ValidationReportPublisher.ValidationReport.from("FAT50", MIN_DATE, 5000L, summary);

        assertEquals("FAT50", built.indexCode());
        assertEquals(MIN_DATE, built.minDate());
        assertEquals(5000L, built.durationMs());
        assertEquals(11, built.tickersChecked());
        assertEquals(2, built.tickersSkipped());
        assertEquals(3, built.tickersOutOfTolerance());
        assertEquals(44, built.totalBreaks());
        assertEquals(1, built.worstTickers().size());
        assertEquals(1, built.breaks().size());
    }
}

package com.fattorestreet.sec_api.corporateaction;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Renders the weekly adjusted-price validation result as plain text and publishes it to SNS.
 *
 * <p>SNS email carries no attachments and caps a message at 256 KB, so the body is plain text and
 * bounded twice over: {@link AdjustedPriceValidationService} already caps the worst-ticker and
 * break samples, and {@link #render} additionally hard-truncates anything that would still exceed
 * the limit. Truncation is always announced in the body -- a silently shortened report reads as a
 * complete one.
 *
 * <p>Only instantiated in {@code validate-prices} run mode, so the default {@code server} deployment
 * never builds an SNS client or looks for AWS credentials.
 *
 * <p>Licensing note: every value rendered here is a derived comparison statistic. No raw reference
 * price series is carried into the report, and the report reaches an operator's inbox only -- never
 * the database, an API response, or the UI.
 */
@Component
@ConditionalOnProperty(name = "app.run-mode", havingValue = "validate-prices")
public class ValidationReportPublisher {

    private static final Logger log = LoggerFactory.getLogger(ValidationReportPublisher.class);

    /** SNS rejects messages over 256 KB; leave headroom for the truncation notice itself. */
    private static final int MAX_MESSAGE_BYTES = 250_000;

    /** SNS subjects are ASCII and capped at 100 characters. */
    private static final int MAX_SUBJECT_CHARS = 100;

    private final Supplier<SnsClient> snsClientFactory;

    public ValidationReportPublisher() {
        this(SnsClient::create);
    }

    ValidationReportPublisher(Supplier<SnsClient> snsClientFactory) {
        this.snsClientFactory = snsClientFactory;
    }

    /**
     * Publishes the rendered report to {@code topicArn}. Propagates whatever the SDK throws so the
     * caller can fail the task: a report nobody receives is indistinguishable from a run that never
     * happened.
     */
    public void publish(String topicArn, ValidationReport report) {
        String body = render(report);
        try (SnsClient sns = snsClientFactory.get()) {
            sns.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(subject(report))
                    .message(body)
                    .build());
        }
        log.info("Published adjusted-price validation report to {} ({} bytes)",
                topicArn, body.getBytes(StandardCharsets.UTF_8).length);
    }

    String subject(ValidationReport report) {
        String subject = String.format(
                Locale.US,
                "FattoreStreet adjusted-price validation: %s (%d of %d out of tolerance)",
                report.indexCode(),
                report.tickersOutOfTolerance(),
                report.tickersChecked());
        return subject.length() <= MAX_SUBJECT_CHARS ? subject : subject.substring(0, MAX_SUBJECT_CHARS);
    }

    /** Renders the plain-text body, truncating explicitly if it would exceed the SNS size limit. */
    public String render(ValidationReport report) {
        StringBuilder out = new StringBuilder(4096);
        out.append("FattoreStreet adjusted-price validation report\n")
                .append("=============================================\n\n")
                .append(String.format(Locale.US, "Index scope:        %s%n", report.indexCode()))
                .append(String.format(Locale.US, "Comparison window:  %s onwards%n", report.minDate()))
                .append(String.format(Locale.US, "Run duration:       %s%n%n", formatDuration(report.durationMs())))
                .append(String.format(Locale.US, "Tickers checked:    %d%n", report.tickersChecked()))
                .append(String.format(Locale.US, "Tickers skipped:    %d  (no stored prices, or no reference data)%n",
                        report.tickersSkipped()))
                .append(String.format(Locale.US, "Out of tolerance:   %d%n", report.tickersOutOfTolerance()))
                .append(String.format(Locale.US, "Ratio breaks found: %d%n%n", report.totalBreaks()));

        appendWorstTickers(out, report);
        appendBreaks(out, report);

        out.append('\n')
                .append("The ratio compares our stored adjusted close against a reference series, normalized at\n")
                .append("the latest common date. A step in that ratio dates a missing, extra or misdated\n")
                .append("corporate action; its size is the implied event magnitude.\n\n")
                .append("Reference data is used for comparison only. Nothing in this report is stored or served.\n");

        return truncateToLimit(out.toString());
    }

    private void appendWorstTickers(StringBuilder out, ValidationReport report) {
        List<Map<String, Object>> worst = report.worstTickers();
        if (worst.isEmpty()) {
            out.append("No tickers produced a comparable series.\n\n");
            return;
        }
        out.append(String.format(Locale.US, "Worst tickers by max deviation (showing %d of %d checked):%n",
                worst.size(), report.tickersChecked()));
        for (Map<String, Object> row : worst) {
            out.append(String.format(Locale.US, "  %-8s maxAbsDeviation=%-12s datesOverThreshold=%-6s breaks=%s%n",
                    row.get("ticker"),
                    row.get("maxAbsDeviation"),
                    row.get("datesOverThreshold"),
                    row.get("breaks")));
        }
        out.append('\n');
    }

    private void appendBreaks(StringBuilder out, ValidationReport report) {
        List<Map<String, Object>> breaks = report.breaks();
        if (breaks.isEmpty()) {
            out.append("No ratio breaks detected.\n");
            return;
        }
        out.append(String.format(Locale.US, "Ratio breaks (showing %d of %d):%n",
                breaks.size(), report.totalBreaks()));
        for (Map<String, Object> row : breaks) {
            out.append(String.format(Locale.US, "  %-8s %-12s step=%-12s %s%n",
                    row.get("ticker"),
                    row.get("date"),
                    row.get("step"),
                    describeNearestAction(row.get("nearestAction"))));
        }
        int omitted = report.totalBreaks() - breaks.size();
        if (omitted > 0) {
            out.append(String.format(Locale.US, "  ... %d more omitted%n", omitted));
        }
    }

    private String describeNearestAction(Object nearestAction) {
        if (!(nearestAction instanceof Map<?, ?> action) || action.isEmpty()) {
            return "nearestAction=none";
        }
        return String.format(Locale.US, "nearestAction=%s %s ratio=%s (%s days away)",
                action.get("actionType"),
                action.get("effectiveDate"),
                action.get("ratio"),
                action.get("daysFromBreak"));
    }

    /**
     * Hard size guard. The per-list caps upstream make this unreachable in practice, but SNS
     * rejects an oversized message outright, which would turn a useful report into a failed task.
     */
    private String truncateToLimit(String body) {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        if (encoded.length <= MAX_MESSAGE_BYTES) {
            return body;
        }
        String notice = String.format(Locale.US,
                "%n... report truncated: %d of %d bytes omitted to fit the SNS message limit.%n",
                encoded.length - MAX_MESSAGE_BYTES, encoded.length);
        // Cut on a character boundary rather than mid-codepoint.
        String kept = new String(encoded, 0, MAX_MESSAGE_BYTES, StandardCharsets.UTF_8);
        log.warn("Validation report exceeded {} bytes and was truncated", MAX_MESSAGE_BYTES);
        return kept + notice;
    }

    private String formatDuration(long elapsedMs) {
        long minutes = elapsedMs / 60000;
        long seconds = (elapsedMs % 60000) / 1000;
        return minutes > 0
                ? minutes + "m " + seconds + "s"
                : seconds + "s";
    }

    /**
     * One weekly validation run, ready to render. Every field is a derived statistic;
     * {@code worstTickers} and {@code breaks} arrive already capped, and {@code totalBreaks} is the
     * uncapped count so the renderer can say how many rows it is not showing.
     */
    public record ValidationReport(
            String indexCode,
            LocalDate minDate,
            long durationMs,
            int tickersChecked,
            int tickersSkipped,
            int tickersOutOfTolerance,
            int totalBreaks,
            List<Map<String, Object>> worstTickers,
            List<Map<String, Object>> breaks) {

        public static ValidationReport from(
                String indexCode,
                LocalDate minDate,
                long durationMs,
                AdjustedPriceValidationService.BatchSummary summary) {
            return new ValidationReport(
                    indexCode,
                    minDate,
                    durationMs,
                    summary.tickersChecked(),
                    summary.tickersSkipped(),
                    summary.tickersOutOfTolerance(),
                    summary.totalBreaks(),
                    summary.worstTickers(),
                    summary.breaks());
        }
    }
}

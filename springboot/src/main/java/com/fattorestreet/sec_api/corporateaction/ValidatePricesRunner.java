package com.fattorestreet.sec_api.corporateaction;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.fattorestreet.sec_api.repository.IndexMemberRepository;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * One-shot entrypoint for the weekly adjusted-price accuracy report.
 *
 * <p>Activated only when {@code app.run-mode=validate-prices} (env
 * {@code APP_RUN_MODE=validate-prices}); in the default {@code server} mode this bean is not created
 * and the application serves the API as usual. It is the scheduled replacement for the retired
 * {@code GET /admin/validate-adjusted-prices} endpoint, and the only run mode that is purely
 * diagnostic.
 *
 * <p><strong>This runner writes nothing.</strong> It reads stored adjusted closes, compares them
 * against a reference series, and emails derived statistics. That read-only property is what keeps
 * it inside the data-licensing rule, so it is asserted in {@link ValidatePricesRunnerTest} rather
 * than left to convention. It also never sets {@code AdjustmentOptions.validateWithYfinance} -- it
 * does not run price adjustment at all.
 *
 * <p>Scope is one index ({@code app.validate-prices.index-code}, default {@code FAT1000}) rather
 * than every ticker with prices: the price universe is the full IEX HIST symbol set, most of which
 * has no reference counterpart, and a cap-ranked equity index matches the universe actually being
 * maintained.
 *
 * <p>Exit codes (surfaced as the container exit code): {@code 0} on completion, including a run
 * where individual tickers were skipped -- a partial report beats no report. {@code 1} when the
 * index resolves to zero members (a silent "0 tickers checked, all healthy" is worse than a failed
 * task), when the configured minimum date will not parse, or when the report could not be
 * delivered. An empty {@code sns-topic-arn} means log-only, which is not a failure.
 */
@Component
@ConditionalOnProperty(name = "app.run-mode", havingValue = "validate-prices")
public class ValidatePricesRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ValidatePricesRunner.class);

    private final AdjustedPriceValidationService adjustedPriceValidationService;
    private final IndexMemberRepository indexMemberRepository;
    private final ValidationReportPublisher validationReportPublisher;
    private final ConfigurableApplicationContext context;

    /** Index whose members are validated. */
    @Value("${app.validate-prices.index-code:FAT1000}")
    private String indexCode;

    /** Start of the comparison window. */
    @Value("${app.validate-prices.min-date:2016-01-01}")
    private String minDate;

    /** Report destination; blank means log the report instead of publishing it. */
    @Value("${app.validate-prices.sns-topic-arn:}")
    private String snsTopicArn;

    /** Safety cap on tickers validated, heaviest index weight first; {@code 0} means no cap. */
    @Value("${app.validate-prices.max-tickers:0}")
    private int maxTickers;

    public ValidatePricesRunner(
            AdjustedPriceValidationService adjustedPriceValidationService,
            IndexMemberRepository indexMemberRepository,
            ValidationReportPublisher validationReportPublisher,
            ConfigurableApplicationContext context) {
        this.adjustedPriceValidationService = adjustedPriceValidationService;
        this.indexMemberRepository = indexMemberRepository;
        this.validationReportPublisher = validationReportPublisher;
        this.context = context;
    }

    @Override
    @SuppressFBWarnings(
            value = "DM_EXIT",
            justification = "One-shot Fargate task: exiting the JVM with the run's status code is"
                    + " how the task reports success or failure to EventBridge. SpringApplication.exit"
                    + " runs shutdown hooks first, so this is the documented Spring Boot pattern for"
                    + " a task that must propagate an exit code.")
    public void run(ApplicationArguments args) {
        int exitCode = runValidation();
        // Terminate the JVM so the ephemeral task stops. SpringApplication.exit runs shutdown hooks.
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /**
     * Runs the validation and returns the process exit code: {@code 0} on completion, {@code 1} on
     * an empty scope, an unparseable minimum date, or a failed report delivery.
     */
    int runValidation() {
        long startTime = System.currentTimeMillis();
        String resolvedIndexCode = indexCode == null ? "" : indexCode.trim().toUpperCase(Locale.ROOT);
        LocalDate resolvedMinDate;
        try {
            resolvedMinDate = LocalDate.parse(minDate == null ? "" : minDate.trim());
        } catch (DateTimeParseException e) {
            log.error("app.validate-prices.min-date is not an ISO date ({}); exiting non-zero.", minDate);
            return 1;
        }
        log.info("Starting one-shot adjusted-price validation (indexCode={}, minDate={}, maxTickers={}, "
                + "publish={})", resolvedIndexCode, resolvedMinDate, maxTickers, hasTopic() ? "sns" : "log-only");

        try {
            List<String> tickers =
                    indexMemberRepository.findTickersByIndexCodeOrderByPercentDesc(resolvedIndexCode);
            if (tickers.isEmpty()) {
                log.error("Index {} resolved to zero members; nothing to validate. Exiting non-zero rather than "
                        + "reporting a healthy run over an empty scope.", resolvedIndexCode);
                return 1;
            }
            if (maxTickers > 0 && tickers.size() > maxTickers) {
                log.info("Capping validation at the {} heaviest of {} {} members (app.validate-prices.max-tickers)",
                        maxTickers, tickers.size(), resolvedIndexCode);
                tickers = tickers.subList(0, maxTickers);
            }

            AdjustedPriceValidationService.BatchSummary summary =
                    adjustedPriceValidationService.validateBatch(tickers, resolvedMinDate);
            long elapsedMs = System.currentTimeMillis() - startTime;
            ValidationReportPublisher.ValidationReport report = ValidationReportPublisher.ValidationReport.from(
                    resolvedIndexCode, resolvedMinDate, elapsedMs, summary);

            if (hasTopic()) {
                validationReportPublisher.publish(snsTopicArn.trim(), report);
            } else {
                log.info("No SNS topic configured; report follows.\n{}", validationReportPublisher.render(report));
            }

            log.info("Adjusted-price validation finished in {}m {}s -- indexCode={}, tickersChecked={}, "
                            + "tickersSkipped={}, tickersOutOfTolerance={}, totalBreaks={}, published={}",
                    elapsedMs / 60000, (elapsedMs % 60000) / 1000,
                    resolvedIndexCode, summary.tickersChecked(), summary.tickersSkipped(),
                    summary.tickersOutOfTolerance(), summary.totalBreaks(), hasTopic());
            return 0;
        } catch (Exception e) {
            log.error("Adjusted-price validation failed", e);
            return 1;
        }
    }

    private boolean hasTopic() {
        return snsTopicArn != null && !snsTopicArn.isBlank();
    }
}

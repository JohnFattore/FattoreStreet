package com.fattorestreet.sec_api.fundamentals;

import java.time.Year;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.fattorestreet.sec_api.util.MarketTime;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * One-shot entrypoint for running the SEC XBRL frames sync (quarterly fundamentals) and then
 * exiting.
 *
 * <p>Activated only when {@code app.run-mode=fundamentals-load} (env
 * {@code APP_RUN_MODE=fundamentals-load}); in the default {@code server} mode this bean is not
 * created and the application serves the API as usual. Designed for an ephemeral Fargate scheduled
 * task, mirroring {@link com.fattorestreet.sec_api.marketdata.HistLoadRunner} and
 * {@link com.fattorestreet.sec_api.index.IndexLoadRunner}: the task <em>is</em> the work, so there is
 * no inbound HTTP call and no JWT verification. It is the scheduled equivalent of the
 * {@code GET /admin/sync-frames} endpoint.
 *
 * <p>Unlike that endpoint, which syncs every year from {@link EdgarService#FRAMES_START_YEAR}, the
 * nightly run covers only a recent window: {@code app.fundamentals-load.years-back} (default 1)
 * years back through the current year. Frames are fetched per (concept, period), so a full-history
 * sync is thousands of multi-megabyte SEC requests, nearly all of them re-reading settled filings.
 * Two calendar years is enough to absorb amendments and late filers, and quarters upsert by
 * (asset, year, quarter), so restating a year overwrites in place. Set
 * {@code app.fundamentals-load.start-year} to pin an explicit year (e.g. 2009) for a backfill run.
 *
 * <p>Exit codes (surfaced as the container exit code): {@code 0} on completion, {@code 1} when the
 * sync throws or when every frame request failed. That last guard matters because the per-request
 * handler only logs and continues: with a missing {@code SEC_CONTACT_EMAIL} the SEC answers 403
 * "Undeclared Automated Tool" to everything, and without the check the task would persist nothing
 * and still report success. Partial failures exit {@code 0} — the SEC legitimately 404s concept
 * and period combinations nobody tagged, and the sync is idempotent, so the next run retries.
 */
@Component
@ConditionalOnProperty(name = "app.run-mode", havingValue = "fundamentals-load")
public class FundamentalsLoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FundamentalsLoadRunner.class);

    private final EdgarService edgarService;
    private final ConfigurableApplicationContext context;

    /** Calendar years to walk back from the current year. Ignored when start-year is set. */
    @Value("${app.fundamentals-load.years-back:1}")
    private int yearsBack;

    /** Explicit first year to sync; {@code 0} means derive it from years-back. */
    @Value("${app.fundamentals-load.start-year:0}")
    private int startYear;

    public FundamentalsLoadRunner(EdgarService edgarService, ConfigurableApplicationContext context) {
        this.edgarService = edgarService;
        this.context = context;
    }

    @Override
    @SuppressFBWarnings(
            value = "DM_EXIT",
            justification = "One-shot Fargate task: exiting the JVM with the load's status code is"
                    + " how the task reports success or failure to EventBridge. SpringApplication.exit"
                    + " runs shutdown hooks first, so this is the documented Spring Boot pattern for"
                    + " a task that must propagate an exit code.")
    public void run(ApplicationArguments args) {
        int exitCode = runLoad();
        // Terminate the JVM so the ephemeral task stops. SpringApplication.exit runs shutdown hooks.
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /**
     * Runs the frames sync and returns the process exit code: {@code 0} on completion, {@code 1}
     * when the sync throws or every frame request failed.
     */
    int runLoad() {
        long startTime = System.currentTimeMillis();
        int resolvedStartYear = resolveStartYear();
        log.info("Starting one-shot fundamentals load (startYear={}, yearsBack={})", resolvedStartYear, yearsBack);
        try {
            EdgarService.SyncResult result = edgarService.syncFrames(resolvedStartYear);
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("Fundamentals load finished in {}m {}s -- years={}-{}, equitiesProcessed={}, fundsSkipped={}, "
                            + "quartersPersisted={}, frameRequests={}, frameFailures={}",
                    elapsedMs / 60000, (elapsedMs % 60000) / 1000,
                    result.startYear(), result.endYear(), result.equitiesProcessed(), result.fundsSkipped(),
                    result.quartersPersisted(), result.frameRequests(), result.frameFailures());
            if (result.frameRequests() > 0 && result.frameFailures() == result.frameRequests()) {
                log.error("Every frame request failed (likely a missing SEC_CONTACT_EMAIL User-Agent or an SEC "
                        + "outage); exiting non-zero so the task is marked failed.");
                return 1;
            }
            return 0;
        } catch (Exception e) {
            log.error("Fundamentals load failed", e);
            return 1;
        }
    }

    /**
     * Resolves the first year to sync: the explicit start-year when set, otherwise the current year
     * minus years-back. Clamped to {@link EdgarService#FRAMES_START_YEAR}, before which frames data
     * is not usable.
     */
    private int resolveStartYear() {
        int resolved = startYear > 0
                ? startYear
                : Year.now(MarketTime.MARKET).getValue() - Math.max(yearsBack, 0);
        return Math.max(resolved, EdgarService.FRAMES_START_YEAR);
    }
}

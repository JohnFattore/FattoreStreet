package com.fattorestreet.sec_api.index;

import java.time.Year;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * One-shot entrypoint for running the index load (metrics refresh + cap-ranked rebuilds) and then
 * exiting.
 *
 * <p>Activated only when {@code app.run-mode=index-load} (env {@code APP_RUN_MODE=index-load}); in
 * the default {@code server} mode this bean is not created and the application serves the API as
 * usual. Designed for an ephemeral Fargate scheduled task, mirroring
 * {@link com.fattorestreet.sec_api.marketdata.HistLoadRunner}: the task <em>is</em> the work, so
 * there is no inbound HTTP call and no JWT verification.
 *
 * <p>Exit codes (surfaced as the container exit code): {@code 0} on completion, {@code 1} when the
 * load throws or the refresh processed fewer listings than {@code app.index-load.min-processed}.
 * The threshold matters because {@link FattoreIndexRebuildService#rebuild} deletes members by index
 * code (not year) before reinserting — rebuilding on top of a mostly-failed refresh (SEC outage,
 * empty new calendar year) would shrink or wipe the live indexes, so the rebuild is skipped and
 * yesterday's members are kept.
 */
@Component
@ConditionalOnProperty(name = "app.run-mode", havingValue = "index-load")
public class IndexLoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexLoadRunner.class);

    private final IndexLoadService indexLoadService;
    private final IndexMetricsRefreshService indexMetricsRefreshService;
    private final ConfigurableApplicationContext context;

    /** Target calendar year; {@code 0} means the current year. */
    @Value("${app.index-load.year:0}")
    private int year;

    @Value("${app.index-load.scope:russell1000}")
    private String scope;

    /** Skip the metrics refresh and only rebuild (assumes metrics for the year already exist). */
    @Value("${app.index-load.skip-refresh:false}")
    private boolean skipRefresh;

    /** When set, refresh only this ticker (smoke-test mode); the rebuild still runs. */
    @Value("${app.index-load.ticker:}")
    private String ticker;

    /** Minimum refreshed listings required before rebuilding; ignored in single-ticker mode. */
    @Value("${app.index-load.min-processed:800}")
    private int minProcessed;

    public IndexLoadRunner(
            IndexLoadService indexLoadService,
            IndexMetricsRefreshService indexMetricsRefreshService,
            ConfigurableApplicationContext context) {
        this.indexLoadService = indexLoadService;
        this.indexMetricsRefreshService = indexMetricsRefreshService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = runLoad();
        // Terminate the JVM so the ephemeral task stops. SpringApplication.exit runs shutdown hooks.
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /**
     * Runs the load and returns the process exit code: {@code 0} on completion (per-ticker skips
     * are fine — metrics upsert by (listing, year), so unprocessed tickers keep prior values),
     * {@code 1} when the refresh falls below the rebuild guard or either phase throws.
     */
    int runLoad() {
        long startTime = System.currentTimeMillis();
        int resolvedYear = year > 0 ? year : Year.now().getValue();
        boolean singleTicker = ticker != null && !ticker.isBlank();
        log.info("Starting one-shot index load (year={}, scope={}, skipRefresh={}, ticker={}, minProcessed={})",
                resolvedYear, scope, skipRefresh, singleTicker ? ticker : "-", minProcessed);
        try {
            if (!skipRefresh) {
                IndexMetricsRefreshService.RefreshResult r = singleTicker
                        ? indexMetricsRefreshService.refreshSingleTicker(ticker, resolvedYear)
                        : indexLoadService.refresh(scope, resolvedYear);
                log.info("Index metrics refresh finished -- processed={}, skipped={}, skippedTickers={}",
                        r.processed(), r.skipped(), r.skippedTickers());
                int required = singleTicker ? 1 : minProcessed;
                if (r.processed() < required) {
                    log.error("Refresh processed {} listings (< {}); skipping rebuild to keep existing index "
                            + "members and exiting non-zero so the task is marked failed.",
                            r.processed(), required);
                    return 1;
                }
            }
            List<FattoreIndexRebuildService.RebuildResult> rebuilds = indexLoadService.rebuildAll(resolvedYear);
            for (FattoreIndexRebuildService.RebuildResult rb : rebuilds) {
                log.info("Rebuilt {} -- memberCount={}, partial={}", rb.indexCode(), rb.memberCount(), rb.partial());
            }
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("Index load finished in {}m {}s", elapsedMs / 60000, (elapsedMs % 60000) / 1000);
            return 0;
        } catch (Exception e) {
            log.error("Index load failed", e);
            return 1;
        }
    }
}

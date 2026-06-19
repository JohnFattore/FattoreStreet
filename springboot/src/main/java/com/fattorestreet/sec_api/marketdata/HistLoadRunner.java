package com.fattorestreet.sec_api.marketdata;

import java.util.Map;

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
 * One-shot entrypoint for running the IEX HIST price load and then exiting.
 *
 * <p>Activated only when {@code app.run-mode=hist-load} (env {@code APP_RUN_MODE=hist-load}); in the
 * default {@code server} mode this bean is not created and the application serves the API as usual.
 * Designed for an ephemeral Fargate scheduled task: the task <em>is</em> the work, so there is no
 * inbound HTTP call and no JWT verification. The application still boots as a normal web app (the
 * runner executes once the context is up, then exits) — port 8080 binds but receives no traffic
 * inside the isolated task, which keeps the boot path identical to the running service.
 *
 * <p>Exit codes (surfaced as the container exit code): {@code 0} on completion, {@code 1} when the
 * load throws or every attempted day failed. Per-day errors with at least one day processed exit
 * {@code 0} — the load is idempotent ({@code existsByTradeDate} skips), so the next run retries.
 */
@Component
@ConditionalOnProperty(name = "app.run-mode", havingValue = "hist-load")
public class HistLoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistLoadRunner.class);

    private final IexHistService iexHistService;
    private final ConfigurableApplicationContext context;

    @Value("${app.hist-load.days:20}")
    private int days;

    public HistLoadRunner(IexHistService iexHistService, ConfigurableApplicationContext context) {
        this.iexHistService = iexHistService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = runLoad();
        // Terminate the JVM so the ephemeral task stops. SpringApplication.exit runs shutdown hooks.
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /**
     * Runs the load and returns the process exit code: {@code 0} on completion (including per-day
     * errors as long as something was processed, since the load is idempotent and retries next run),
     * {@code 1} when the load throws or every attempted day failed.
     */
    int runLoad() {
        long startTime = System.currentTimeMillis();
        log.info("Starting one-shot IEX HIST load (days={})", days);
        try {
            Map<String, Object> result = iexHistService.loadHistData(days);
            long elapsedMs = System.currentTimeMillis() - startTime;
            int processed = ((Number) result.getOrDefault("processed", 0)).intValue();
            int errors = ((Number) result.getOrDefault("errors", 0)).intValue();
            log.info("IEX HIST load finished in {}m {}s -- processed={}, skipped={}, notAvailable={}, errors={}",
                    elapsedMs / 60000, (elapsedMs % 60000) / 1000,
                    processed, result.get("skipped"), result.get("notAvailable"), errors);
            if (errors > 0 && processed == 0) {
                log.error("Every attempted day failed; exiting non-zero so the task is marked failed.");
                return 1;
            }
            return 0;
        } catch (Exception e) {
            log.error("IEX HIST load failed", e);
            return 1;
        }
    }
}

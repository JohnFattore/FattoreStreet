package com.fattorestreet.sec_api.listing;

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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * One-shot entrypoint for loading the SEC ticker universe (equities plus funds), enriching ETF
 * identities, and then exiting.
 *
 * <p>Activated only when {@code app.run-mode=asset-load} (env {@code APP_RUN_MODE=asset-load}); in
 * the default {@code server} mode this bean is not created and the application serves the API as
 * usual. Designed for an ephemeral Fargate scheduled task, mirroring
 * {@link com.fattorestreet.sec_api.marketdata.HistLoadRunner} and
 * {@link com.fattorestreet.sec_api.index.IndexLoadRunner}: the task <em>is</em> the work, so there
 * is no inbound HTTP call and no JWT verification. It is the scheduled replacement for the retired
 * {@code GET /admin/asset-load} endpoint.
 *
 * <p>The load runs monthly: the SEC ticker indexes change slowly, and both {@code Asset} and
 * {@code Listing} upsert by their natural keys, so re-running is safe and additive.
 *
 * <p>Exit codes (surfaced as the container exit code): {@code 0} on completion, {@code 1} when the
 * load throws or when it loaded zero tickers. That last guard matters because the SEC answers 403
 * "Undeclared Automated Tool" to a request with no {@code SEC_CONTACT_EMAIL} in its User-Agent;
 * without the check a misconfigured task would persist nothing and still report success. ETF
 * identity enrichment failures are <em>not</em> fatal on their own -- the tickers are already
 * persisted by then and the next run retries the enrichment.
 */
@Component
@ConditionalOnProperty(name = "app.run-mode", havingValue = "asset-load")
public class AssetLoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AssetLoadRunner.class);

    private final SecTickerLoadService secTickerLoadService;
    private final EtfIdentityService etfIdentityService;
    private final ConfigurableApplicationContext context;

    /** Overwrite ETF identities that are already resolved, rather than filling only the gaps. */
    @Value("${app.asset-load.overwrite-existing:false}")
    private boolean overwriteExisting;

    public AssetLoadRunner(
            SecTickerLoadService secTickerLoadService,
            EtfIdentityService etfIdentityService,
            ConfigurableApplicationContext context) {
        this.secTickerLoadService = secTickerLoadService;
        this.etfIdentityService = etfIdentityService;
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
     * Runs the load and returns the process exit code: {@code 0} on completion, {@code 1} when the
     * SEC fetch throws or the run persisted no tickers at all.
     */
    int runLoad() {
        long startTime = System.currentTimeMillis();
        log.info("Starting one-shot asset load (overwriteExisting={})", overwriteExisting);
        try {
            SecTickerLoadService.SecTickerLoadResult load = secTickerLoadService.load();
            if (load.tickersLoaded() == 0) {
                log.error("Asset load persisted zero tickers (likely a missing SEC_CONTACT_EMAIL User-Agent or an "
                        + "SEC outage); exiting non-zero so the task is marked failed.");
                return 1;
            }
            Map<String, Object> enrichment = etfIdentityService.enrichFundListingIdentities(overwriteExisting);
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("Asset load finished in {}m {}s -- equitiesLoaded={}, fundsLoaded={}, tickersLoaded={}, "
                            + "etfIdentityOverwriteExisting={}, etfIdentityEnrichment={}",
                    elapsedMs / 60000, (elapsedMs % 60000) / 1000,
                    load.equitiesLoaded(), load.fundsLoaded(), load.tickersLoaded(),
                    overwriteExisting, enrichment);
            return 0;
        } catch (Exception e) {
            log.error("Asset load failed", e);
            return 1;
        }
    }
}

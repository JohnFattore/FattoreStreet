# The IEX HIST one-shot ingest — async prefetch pipelining, idempotent retries, and Fargate-as-cron

_FattoreStreet @ [`f337c3fe`](https://github.com/JohnFattore/FattoreStreet/tree/f337c3fef734f52b9a5a4a696997a3960944a332) — 2026-07-19_

_Source: [#112](https://github.com/JohnFattore/FattoreStreet/issues/112)_

## Overview

Every night, FattoreStreet's price history gets refreshed by a Spring Boot process that isn't a long-running service at all — it's the *same* container image as the API, booted with `APP_RUN_MODE=hist-load`, that downloads IEX's raw TOPS pcap feed, parses trades into OHLCV bars, saves them, runs corporate-action price adjustment, and then calls `System.exit()`. There's no Celery, no cron daemon inside the app, no queue. AWS EventBridge Scheduler invokes `ecs:RunTask` directly on a Fargate task definition once a day; the task's exit code (0 or 1) is itself the job-success signal, wired to a CloudWatch alarm → SNS → email. This is a distinctive design for a "batch job" in a system that's otherwise request/response — it's worth understanding both the Java-side engineering (single-thread-ahead async prefetch to overlap network I/O with parsing, and idempotency via `existsByTradeDate`) and the infra-side pattern (ephemeral compute as a cron replacement, no queue, no worker pool) since both patterns recur anywhere you need a scheduled bulk job without standing up dedicated infrastructure.

## Files to read

- `springboot/src/main/java/com/fattorestreet/sec_api/marketdata/IexHistService.java` — the whole pipeline:
  - Lines 78-182 (`loadHistData`): fetches the day index, filters out days already in the DB (`dailyPriceRepository.existsByTradeDate`, line 90) or missing from the IEX index (`notAvailable`), then walks `toProcess`
  - Lines 108-131: the prefetch pattern — before processing day *i*, day *i+1*'s download is already kicked off on a **single-thread daemon executor** (line 50, `downloadExecutor`) so the ~tens-of-MB pcap.gz download for tomorrow overlaps with today's parsing/DB-save instead of happening serially
  - Lines 133-152: trades are streamed via `PcapParser.parseGzip` straight into a `HashMap<String, OhlcvAccumulator>` (one accumulator per ticker) — no intermediate trade list is materialized
  - Lines 161-170: per-day error handling — a single bad day increments `errors` and moves on (still prefetching the *next* day), it doesn't abort the whole run
  - Lines 231-259: `tradingDates`/`isTradingDay` — a hand-rolled NYSE holiday calendar (fixed dates + floating holidays like "3rd Monday of January") used to avoid even querying the IEX index for days that can't have data
- `springboot/src/main/java/com/fattorestreet/sec_api/marketdata/HistLoadRunner.java` (121 lines, read the whole thing, it's short) — especially:
  - Lines 39-40: `@ConditionalOnProperty(name = "app.run-mode", havingValue = "hist-load")` — this bean, and therefore the entire one-shot behavior, only exists when that property is set; in normal `server` mode it's absent and the app just serves HTTP as usual
  - Lines 63-68: `run()` calls `System.exit(SpringApplication.exit(...))` — the container process must actually terminate for Fargate to consider the task "stopped" with an exit code
  - Lines 76-119 (`runLoad`/`runAdjustment`) and the class javadoc (lines 17-37): the exit-code contract — 0 on any partial success (because the load is idempotent and retries next run), 1 only when *everything* failed
- `springboot/deploy/terraform/main.tf`:
  - Lines 115-161: the `aws_ecs_task_definition` — note there's no `desired_count`/service, just a task definition meant to be `RunTask`'d
  - Lines 215-249: `aws_scheduler_schedule.this` — EventBridge Scheduler (not EventBridge *Rules*/CloudWatch Events) targeting `ecs:RunTask` with `retry_policy.maximum_retry_attempts = 0` (line 246) — deliberately no built-in retry, since the job is idempotent and just runs again tomorrow
  - Lines 391-427: the failure-alerting `aws_cloudwatch_event_rule` — pattern-matches ECS `TASK_STATE_CHANGE` events for `lastStatus = STOPPED` with a non-zero exit code or `TaskFailedToStart`, transforms it into a plain-English SNS email
- `springboot/deploy/terraform/variables.tf` lines 107-123 and 166-176 — the two schedules (`06:30 UTC` hist-load, `09:30 UTC` index-load, 3 hours apart so the index/metrics refresh sees fresh `DailyPrice` rows) and their comments on *why* those times

## Questions to work through while reading

1. Why does the prefetch pipeline in `loadHistData` use exactly **one** background thread (`Executors.newSingleThreadExecutor`) instead of, say, downloading several days ahead in parallel? What would break (or just stop helping) if `toProcess` had 20 days and you fired off 20 concurrent downloads instead?
2. Walk through what happens if the JVM crashes (OOM, killed task) partway through processing day 5 of a 10-day backfill. On the next scheduled run, which days re-download and re-parse, and which are skipped for free? Which single repository method makes this idempotent, and what would happen if `saveAll` partially failed (some tickers written, others not) right before a crash?
3. `HistLoadRunner` is a Spring `ApplicationRunner` gated by `@ConditionalOnProperty`, riding inside the exact same Spring Boot application (and Docker image) as the always-on API service. What are the tradeoffs of that vs. a genuinely separate lightweight CLI/script image? Why might sharing the image matter here (build pipeline, dependency reuse, JPA entities) even though port 8080 binds uselessly during the batch run?
4. The Terraform sets `retry_policy.maximum_retry_attempts = 0` on the EventBridge schedule target, and the task itself only exits 1 when *every* day failed. Contrast this with a "queue + worker + automatic retry" architecture (e.g., what you'd get from Celery/SQS). What class of failures does this design implicitly rely on "tomorrow's run" to fix, and what failures would it never recover from without a human noticing the SNS alert?
5. `isTradingDay` hand-codes NYSE holidays including floating ones (`nthWeekday`). What happens the day this calendar silently drifts out of date (e.g., NYSE adds a new observed holiday, or a rule like Memorial Day's "last Monday" — note line 251 approximates it as "Monday after the 24th" — is wrong for the given year)? Trace the failure mode: does it corrupt data, silently skip a real trading day, or just waste an index lookup?

## Primer: ephemeral compute as a cron replacement, and prefetch pipelining

**Fargate-as-cron.** Instead of a long-lived worker (Celery beat, a Kubernetes CronJob controller, a systemd timer on a box you patch), this pattern uses a *task definition* (a stopped container spec) plus a *scheduler* that calls a `RunTask` API on a cron expression. No compute exists between runs — you pay only for the minutes the container actually executes, there's no server to patch, and the "job" and "how it's invoked" are decoupled (you could also invoke the same task definition manually or from a Step Function). The tradeoff is that all the things a persistent worker gives you for free — a supervisor process, structured retry/backoff, a dashboard of job history — have to be reconstructed from primitives: here that's "exit code as job-success", "idempotent re-run as retry", and "CloudWatch Events pattern-match on task state as alerting."

**Async prefetch pipelining.** This is a classic double-buffering technique: when work has two independent phases with different bottlenecks (I/O-bound download vs. CPU-bound parse-and-persist), you can overlap consecutive iterations so phase-2-of-item-N runs concurrently with phase-1-of-item-N+1, instead of doing all of phase 1 then all of phase 2 for each item serially. With a single background worker and a `CompletableFuture` you get one level of pipelining (N+1 downloads while N is being parsed) without needing a thread pool sized to the whole job — the download thread is always at most one step ahead, bounded and predictable.

## External references

- AWS EventBridge Scheduler — "Schedule Amazon ECS tasks": https://docs.aws.amazon.com/scheduler/latest/UserGuide/schedule-types.html
- `java.util.concurrent.CompletableFuture` (the prefetch mechanism): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html

## Exercise (optional)

Locally (or by reading closely enough to reason it through), pick a 3-day `days` value and trace `loadHistData` by hand: write down, for each iteration of the loop, which day's download is in-flight, which day's file is being parsed, and when `nextDownload` gets reassigned. Then compare the wall-clock shape to what it would be with the prefetch removed (download day N, *then* parse day N, repeat) — this makes concrete how much wall-clock the pipelining actually saves versus a naive sequential loop.

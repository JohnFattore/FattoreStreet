# Ephemeral Fargate one-shot tasks — run-mode selection, EventBridge Scheduler, and exit-code contracts

_FattoreStreet @ [`4a11fd7c`](https://github.com/JohnFattore/FattoreStreet/tree/4a11fd7ce59ffdcae32ab48033937237db80c326) — 2026-07-27_

_Source: [#146](https://github.com/JohnFattore/FattoreStreet/issues/146)_

## Overview

FattoreStreet runs its two nightly data loads (IEX HIST price ingest, then index metrics/rebuild) as **ephemeral Fargate tasks** rather than as endpoints on the always-on Spring Boot service or as Django Celery jobs (Celery has been removed entirely). The same container image and jar serve three completely different behaviors — the live API, the hist load, and the index load — selected purely by one environment variable, `APP_RUN_MODE`, read by Spring's `@ConditionalOnProperty`. This is a neat pattern worth understanding closely: it avoids maintaining a second image/build pipeline for batch work, it lets EventBridge Scheduler replace what used to be `django-celery-beat`, and it turns "did last night's load succeed" into a plain ECS task exit code that CloudWatch/SNS can alert on — no polling, no separate job-status table. The subtlety is in what "success" means for an idempotent nightly job that partially fails (some days load, some tickers don't refresh): the runners deliberately treat partial failure as exit `0` (self-heals next run) and *total* failure as exit `1` (something needs a human), and the Terraform module encodes real operational scars (an IAM role with no `iam:Put*`, an accidental $17/mo-per-task incident from a task definition outrunning its image) directly in code comments.

## Files to read

- `springboot/deploy/terraform/main.tf` — the whole file (499 lines), especially:
  - Lines 152–213: `aws_ecs_task_definition.this` (hist-load) — note `environment` sets `APP_RUN_MODE=hist-load` (line 182) and `secrets` pulls three keys out of one shared JSON secret via ECS's `<arn>:<key>::` syntax (lines 195–199)
  - Lines 218–276: the EventBridge Scheduler's own IAM role, and why `scheduler_run_task`'s policy resources are built from string interpolation instead of `aws_ecs_task_definition.this.arn` (lines 241–249 comment) — a plan-time-unknown IAM document would trigger `iam:PutRolePolicy` on every apply, which the scoped developer role cannot do
  - Lines 278–312: `aws_scheduler_schedule.this` — cron target, `retry_policy.maximum_retry_attempts = 0`
  - Lines 326–417: the second task/schedule pair (`index_load`) sharing the cluster, IAM roles, and security group but with its own log group and schedule, offset ~3 hours later
  - Lines 419–498: the failure-alerting `aws_cloudwatch_event_rule` matching `ECS Task State Change` with `lastStatus=STOPPED` and a non-zero exit code or `TaskFailedToStart`, feeding an SNS email topic
- `springboot/src/main/java/com/fattorestreet/sec_api/marketdata/HistLoadRunner.java` — the whole file (129 lines); read the class Javadoc (lines 19–40) alongside `runLoad()` (84–104) and `runAdjustment()` (106–127) — note the exit-code branching: `errors > 0 && processed == 0` → `1`, everything else including per-day errors → falls through to adjustment, adjustment throwing → `1`
- `springboot/src/main/java/com/fattorestreet/sec_api/index/IndexLoadRunner.java` — the whole file (127 lines); compare its `runLoad()` (93–125) to `HistLoadRunner`'s — the min-processed guard (lines 106–112) is the interesting one: it exists specifically because `rebuild()` deletes-then-reinserts index members by index code, so rebuilding on top of a mostly-failed refresh would shrink the live indexes
- `springboot/deploy/terraform/README.md` — read top to bottom; especially the "Credentials" section (lines 67–139, what the scoped IAM role can and can't do and why) and "`terraform apply` is not a deploy" (lines 157–163, the real incident where `IndexLoadRunner` shipped a task definition against an image that didn't have that run mode yet, and 6 orphaned tasks ran forever at ~$17/mo each)
- `.claude/rules/infrastructure.md` — "Scheduled Fargate tasks" section, the condensed version of the same rules

## Questions to answer while reading

1. Why does `HistLoadRunner.run()` call `System.exit(SpringApplication.exit(context, () -> exitCode))` instead of just `System.exit(exitCode)` — what does `SpringApplication.exit` do first, and why does that matter for a task that opened a Postgres connection pool?
2. The task definition's `container_definitions` sets `APP_RUN_MODE=hist-load` and the `@ConditionalOnProperty` in `HistLoadRunner` checks `app.run-mode`. Find where the env var gets mapped to that Spring property name (hint: Spring Boot's relaxed binding for env vars) — is there a `application.properties` entry involved, or is this automatic?
3. `HistLoadRunner` treats "every attempted day failed" as a hard failure but "some days failed, some succeeded" as success (exit 0). Why is that the right call given `existsByTradeDate` skip-on-idempotent-retry — what would go wrong operationally if *any* per-day error caused exit 1 instead?
4. The scheduler's `ecs:RunTask` IAM policy (`main.tf:234-259`) is scoped to `task-definition/<name_prefix>:*` wildcards rather than a specific revision ARN. Given `skip_destroy = true` on the task definition (never deregistering old revisions), what problem would pinning to a specific revision ARN create on every `terraform apply` that registers a new revision?
5. Walk through the actual incident described in the README ("`terraform apply` is not a deploy"): concretely, what state was the ECS cluster in for the ~17 days between `IndexLoadRunner` merging and someone noticing, and why did the failure-alerting SNS topic (built specifically to catch stopped tasks with bad exit codes) stay silent the whole time?

## Primer: ephemeral batch jobs vs. always-on schedulers

Two broad ways to run recurring batch work in AWS: (1) a long-lived scheduler process (cron, Celery beat, a Kubernetes CronJob controller) that itself stays running and dispatches work, or (2) a fully ephemeral one — nothing runs between invocations, and a managed trigger (EventBridge Scheduler here, or plain CloudWatch Events cron) directly launches a short-lived compute unit (a Fargate `RunTask`, here) that does the work and terminates. FattoreStreet moved from option 1 (Celery beat inside the always-on Django/Spring services) to option 2 specifically for cost: a 4GB task that runs for a few minutes a night costs pennies, versus provisioning that RAM 24/7 on the EC2 host. The tradeoff is operational complexity shifts from "is the scheduler process alive" to "did the task actually exit with the code I expect, and does my alerting actually see that" — which is exactly the gap the README's "not a deploy" incident fell into (the task never *stopped*, so the STOPPED-with-bad-exit-code alert rule never even evaluated).

## External references

- AWS docs, EventBridge Scheduler "Schedule a one-time or recurring task": https://docs.aws.amazon.com/scheduler/latest/UserGuide/what-is-scheduler.html
- Spring Boot docs, `ApplicationRunner`/`CommandLineRunner` and `SpringApplication.exit`: https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.command-line-runner

## Exercise (optional)

Using `aws-inspect` (read-only, `AWS_PROFILE=fattorestreet`), check the ECS cluster right now: are there zero running tasks (the healthy steady state per `.claude/rules/infrastructure.md`)? Then look up the most recent hist-load task's exit code and CloudWatch logs (`aws logs tail /ecs/fattorestreet-hist-load`), and trace which of `HistLoadRunner`'s two `return` paths (day-load failure vs. adjustment failure) it took, if it failed at all.

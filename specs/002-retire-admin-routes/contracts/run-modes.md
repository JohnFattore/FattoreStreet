# Contract: Run Modes and Scheduled Tasks

The replacement trigger surface. A "run mode" is the operational interface this feature
substitutes for the deleted HTTP routes: one `APP_RUN_MODE` value per capability, on the
shared Spring Boot image, invoked by EventBridge Scheduler or `aws ecs run-task`.

## Invariants every runner must satisfy

1. Annotated `@ConditionalOnProperty(name = "app.run-mode", havingValue = "<mode>")`, so it is
   not instantiated in `server` mode.
2. Implements `ApplicationRunner`; terminates via
   `System.exit(SpringApplication.exit(context, () -> exitCode))` so shutdown hooks run and
   the container reports the code.
3. Carries `@SuppressFBWarnings("DM_EXIT")` with a justification — SpotBugs gates `mvn verify`
   and the justification is mandatory.
4. Exit `0` on completion (including idempotent partial failures), `1` when the job threw or a
   guard proved the run did no useful work.
5. Logs a single structured summary line before exiting, since CloudWatch is the only
   observability surface for a one-shot task.
6. Uses `MarketTime.MARKET` or `MarketTime.STORAGE` for any date resolution — bare
   `LocalDate.now()` is an ERROR-tier Error Prone finding on both main and test sources.

7. **Has a schedule.** Per FR-013 no run mode is manual-invocation-only. `aws ecs run-task` is
   for ad-hoc overrides of a job that already has a cadence, never its sole trigger.

## Schedule allocation

Slots must not overlap: the SEC rate limiter is per-process, so concurrent tasks double the
effective request rate and earn 403s for both. Rationale in `research.md` §11.

| Mode | Cadence | Slot (ET) | Measured runtime | SEC-bound? |
|---|---|---|---|---|
| `hist-load` | daily | 02:00 | **6h27m–15h32m** | Yes (detection phase) |
| `index-load` | daily | 09:30 | 11–13 min | Yes |
| `fundamentals-load` | daily | 13:30 | **unknown — never deployed** | Yes, heavily |
| `validate-prices` | weekly | Sun 20:00 | unknown | No — Django/yfinance only |
| `asset-load` | monthly | 1st, 22:00 | unknown | Yes, briefly |

Runtimes are from CloudWatch, 2026-08-01. **`hist-load` already overruns into `index-load`**:
on 2026-07-30 it ran to 17:32 from a 02:00 start, straight through the 09:30 index load. Both
new slots are placed in the evening for that reason, clear of every observed tail:
`validate-prices` at Sun 20:00 (no SEC calls, so a long `fundamentals-load` tail cannot
contend with it) and `asset-load` at 22:00. The staggering also keeps the two new jobs apart
when the 1st of the month falls on a Sunday.

`fundamentals-load` has **never run**, so its duration is unknown. Measure it on first deploy
and re-check both new slots against the result.

## Existing modes (unchanged by this feature)

| Mode | Runner | Schedule | Replaces |
|---|---|---|---|
| `hist-load` | `HistLoadRunner` | 02:00 ET daily | `/admin/load-hist` **and** `/admin/adjust-prices` in full |
| `index-load` | `IndexLoadRunner` | 09:30 ET daily | `/admin/indexes/*` (all five) |
| `fundamentals-load` | `FundamentalsLoadRunner` | 13:30 ET daily | `/admin/sync-frames` — ⚠️ **not deployed yet** |

## New mode: `asset-load`

**Replaces**: `GET /admin/asset-load`

| Aspect | Value |
|---|---|
| Runner | `AssetLoadRunner` (package `listing`) |
| Schedule | Monthly, ET, clear of all other jobs |
| Work | Fetch SEC company_tickers + mutual-fund ticker index, upsert `Asset`/`Listing`, then `EtfIdentityService.enrichFundListingIdentities(overwriteExisting)` |
| Config | `ASSET_LOAD_OVERWRITE_EXISTING` (default `false`) |
| Exit `1` when | Either SEC fetch throws, or zero tickers were loaded |

The ticker-parsing logic currently sits inline in `AdminController.assetLoad` (the method at
lines 106–182; its helpers — the `SecTickerRow` record and `parseSecMutualFundTickers` /
`extractSecMutualFundRows` / `firstText` / `parseCik` — sit lower in the same file, from
line 486). It must move into a service before the controller is deleted — controllers stay
thin and the runner cannot depend on a controller.

**Note on scope**: this job still enriches *ETF identity* (series/class metadata from SEC). That
is unrelated to ETF corporate actions and adjusted prices, which are out of scope. Identity
enrichment stays.

**Zero-ticker guard**: SEC answers 403 "Undeclared Automated Tool" to a missing
`SEC_CONTACT_EMAIL` User-Agent. Without the guard the task would persist nothing and report
success — the same failure mode `FundamentalsLoadRunner` guards against explicitly.

## Not built: `adjust-prices`

An earlier draft proposed this mode. Reading `PriceAdjustmentService` and measuring the live
job removed the need for it.

`hist-load`'s adjustment phase calls `applyAdjustments(ticker, actions)` **unconditionally**
for every ticker in its working set, recomputing the cumulative factor across the whole series
from *all* stored corporate actions and rewriting any row whose value changed. It does not skip
already-adjusted rows. Because each trading day's load inserts NULL-adjusted rows for every
active ticker, that working set is ~5,600 tickers nightly — so a late-discovered action is
folded into the full back-history on the next run, automatically.

`force=true` only ever added SEC re-detection outside the scope and staleness rules, which is
precisely the cost the rolling 1/7 refresh was designed to bound. Measured: the *unforced*
nightly pass takes 6h20m–7h04m for ~5,600 tickers with 133 detections. Forcing would mean ~24k
tickers with ~24k detections. Not runnable at any cadence.

Full reasoning and log excerpts: `research.md` §2.

## New mode: `validate-prices`

**Replaces**: `GET /admin/validate-adjusted-prices`

| Aspect | Value |
|---|---|
| Runner | `ValidatePricesRunner` (package `corporateaction`) |
| Schedule | Weekly, ET, clear of the other jobs |
| Work | For each member of `VALIDATE_PRICES_INDEX_CODE`, call `AdjustedPriceValidationService.validateTicker`; aggregate via `summarizeBatch`; render and publish to SNS |
| Config | `VALIDATE_PRICES_INDEX_CODE` (default `FAT1000`), `VALIDATE_PRICES_MIN_DATE` (default `2016-01-01`), `VALIDATE_PRICES_SNS_TOPIC_ARN`, `VALIDATE_PRICES_MAX_TICKERS` |
| Extra env | `DJANGO_PORTFOLIO_BASE_URL=https://fattorestreet.com/django/portfolio` |
| Exit `1` when | The index resolves to zero members, or the SNS publish fails |

**Writes nothing.** This is the only runner that touches the database read-only. Its test must
assert no repository save/delete occurs.

**Scope alignment**: FAT1000 is a cap-ranked equity index, so the report naturally covers only
the universe being maintained. It will not flag ETFs whose adjustment is deferred.

**Degradation (spec edge case)**: a per-ticker reference-fetch failure increments
`tickersSkipped` and continues. Only a zero-member scope or a failed publish is fatal — a
partial report is more useful than no report.

**Report delivery**: plain-text SNS message, under 256 KB. Truncation must be explicit
("N more omitted"), never silent.

## Ad-hoc invocation contract (FR-010)

Every capability the deleted routes offered as a query parameter stays reachable through
`aws ecs run-task` with a container environment override. These belong in the
`springboot/deploy/terraform/README.md` runbook.

| Former route + params | Replacement invocation |
|---|---|
| `/admin/sync-frames` (full 2009→present) | `fundamentals-load` with `FUNDAMENTALS_LOAD_START_YEAR=2009` |
| `/admin/adjust-prices?ticker=AAPL&force=true` | **No equivalent** — nightly `hist-load` recomputes the full series for every active ticker anyway |
| `/admin/asset-load?overwriteExisting=true` | `asset-load` with `ASSET_LOAD_OVERWRITE_EXISTING=true` |
| `/admin/validate-adjusted-prices?ticker=AAPL` | `validate-prices` scoped by index, or run the service locally against a dev DB |
| `/admin/indexes/refresh-stocks?ticker=X` | `index-load` with `INDEX_LOAD_TICKER=X` (already supported) |
| `/admin/indexes/rebuild?code=FAT50` | `index-load` rebuilds all three; no single-index override needed |
| `/admin/load-hist?days=30` | `hist-load` with `HIST_LOAD_DAYS=30` (already supported) |
| `/admin/adjust-prices?etfOnly=true` | **No supported equivalent** — ETFs deferred |
| `/admin/summarize-filings` | **No equivalent** — generator retired |
| `/admin/test` | **No equivalent** — debug leftover |

**Known reduction in capability**: single-ticker adjusted-price *validation* has no direct
task equivalent, since the runner is index-scoped. Recorded deliberately — it is an
interactive debugging affordance, and a developer can still call the service directly against
a local database. Do not add an index-of-one workaround unless it is asked for.

## Terraform additions

**Blocking prerequisite**: `fundamentals_load` is already written in `main.tf` but has never
been applied — no task definition, no schedule, no log group, zero refs in `terraform.tfstate`
(verified 2026-08-01). It must be applied and verified *before* `/admin/sync-frames` is
deleted, or fundamentals sync loses its only working path. See `research.md` §12.

Two new task definitions and two new schedules (`asset-load`, `validate-prices`), each
following the existing `aws_ecs_task_definition` + `aws_scheduler_schedule` +
`aws_cloudwatch_log_group` pattern. They share the existing ECR repo, ECS cluster, execution
role, task role, and task security group.

Additionally:
- `aws_sns_topic` + `aws_sns_topic_subscription` for validation reports (separate from the
  failure topic — see `research.md` §3)
- `aws_iam_role_policy` on the **task** role granting `sns:Publish` scoped to that topic ARN
- New variables for each schedule expression, enable flag, and task memory, mirroring the
  existing `index_load_*` / `fundamentals_load_*` naming
- The failure-alert `input_template` (inside `aws_cloudwatch_event_target.task_failures_sns`,
  `main.tf:596-610`) lists log groups by name; it must be extended to mention the new ones
- **Removal**: the `SECRET_KEY` entry in all three existing `secrets` blocks
  (`main.tf:200,369,478`), and no `SECRET_KEY` in any new task definition. The key stays in
  the `fattorestreet/env` blob for Django. See `data-model.md` "SECRET_KEY removal boundary."

All new schedules fall inside the CloudWatch failure rule's blast radius automatically — it
matches on `clusterArn` rather than per-task. Manual `run-task` failures alert too, which is
desirable.

Each new schedule needs its own `*_schedule_enabled` variable mirroring
`index_load_schedule_enabled`, so a misbehaving job can be parked with a one-line
`terraform apply` instead of a destroy.

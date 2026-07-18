# Daily Fargate Job for the Index Fund Generator

## Context

The Fattore index generator (FAT50 / FAT100 / FAT1000) currently runs only when triggered manually from the Admin page via authenticated REST endpoints. The nightly IEX HIST price load already runs as a daily EventBridge-scheduled Fargate task (`springboot/deploy/terraform/`), but index metrics and membership go stale unless someone remembers to click the buttons. Goal: review the load process and add a second daily Fargate job that refreshes index metrics and rebuilds the three indexes automatically, downstream of the nightly price load.

## How the load works today (review)

Two-phase process, all in Spring Boot (`com.fattorestreet.sec_api`), no Django/Celery involvement:

**Phase 1 — metrics refresh** (`index/IndexMetricsRefreshService`):
- Scope defaults to the Russell 1000 universe from bundled `resources/data/IWB_holdings.csv` (`refreshRussell1000Listings(year)`); `all` and single-ticker scopes also exist.
- Per listing: skip checks (asset exists, not a fund, has CIK, has a positive latest IEX `DailyPrice`), then two SEC calls via `client/WebService` (companyfacts for shares outstanding + public float, submissions for jurisdiction), throttled to ≥250 ms between requests.
- Computes marketCap, freeFloat, freeFloatMarketCap, volume; GOOG/GOOGL caps halved; upserts `ListingIndexMetrics` by (listing, year) in short per-listing transactions.
- Failure isolation per ticker; aborts the batch after 25 consecutive SEC fetch failures. ~1000 tickers ⇒ thousands of SEC calls ⇒ long-running (tens of minutes), which is why the frontend uses `timeout: 0`.

**Phase 2 — index rebuild** (`index/FattoreIndexRebuildService.rebuild(year)`, one bean per index via `config/FattoreIndexRebuildConfig`):
- DB-only and fast: filters eligible metrics (US-incorporated, non-fund, positive free-float cap), ranks by freeFloatMarketCap, takes top-N, cap-weights to exactly 100%, then full delete + reinsert of `IndexMember` rows per index. Idempotent.

**Triggers today**: `POST /admin/indexes/refresh-stocks` and `POST /admin/indexes/rebuild` (`controller/AdminController`; rebuild with `refreshMetrics=true` refreshes once then rebuilds FAT100, FAT1000, FAT50). Admin auth = Django SimpleJWT, user_id 1 only. No scheduler exists for this load.

**Operational ordering**: listings → daily prices (nightly hist-load Fargate job) → metrics refresh → rebuild. So the new job must run after the hist load.

## Existing Fargate pattern to reuse

- Run-mode switch: `app.run-mode=${APP_RUN_MODE:server}` in `application.properties`; `marketdata/HistLoadRunner` is `@ConditionalOnProperty(havingValue="hist-load")`, an `ApplicationRunner` that runs once and `System.exit`s (0/1) — container exit code surfaces success.
- Same Docker image works for server and job modes (entrypoint no-ops when `SECRETS_ARN` unset; ECS injects `POSTGRES_PASSWORD`/`SECRET_KEY` natively from the shared `fattorestreet/env` secret).
- Terraform module `springboot/deploy/terraform/`: dedicated ECR repo (manual ARM64 buildx push, not CI), ECS cluster, Fargate ARM64 task def, task SG + Postgres ingress rule, execution/task IAM roles, EventBridge Scheduler cron (default `cron(30 6 * * ? *)` UTC), public subnet + public IP (no NAT), retry 0.

## Implementation plan

**Key safety finding**: `FattoreIndexRebuildService.rebuild(year)` deletes `IndexMember` rows **by index code only** (`deleteByMarketIndex_Code`, lines 80/91), not by year — even when zero metrics are eligible. A rebuild after a failed/partial refresh would shrink or wipe live indexes. The runner guards against this (below).

### Part 1 — Spring Boot (`springboot/src/main/java/com/fattorestreet/sec_api/`)

**1a. Create `index/IndexLoadService.java`** — `@Service`, extracts orchestration shared by controller and runner. Constructor-injects `IndexMetricsRefreshService` + the three `@Qualifier`ed `FattoreIndexRebuildService` beans; builds the canonical ordered list FAT100, FAT1000, FAT50 in the constructor (do not inject `List<...>` by type — bean order isn't guaranteed).
- `RefreshResult refresh(String scope, int year)` — `"russell1000"`/`"iwb"` → `refreshRussell1000Listings`, `"all"` → `refreshAllTickers`, else `IllegalArgumentException`.
- `List<RebuildResult> rebuildAll(int year)` — rebuild each in order.

**1b. Refactor `controller/AdminController.java`** to delegate: `refreshIndexStocks` scope switch (~line 303) → `indexLoadService.refresh(...)` (catch `IllegalArgumentException` → 400; single-ticker branch stays); `rebuildCapRankedIndexes` no-code path (~line 346) → `indexLoadService.rebuildAll(...)`; drop the now-unused `capRankedRebuildAllOrdered` field. Keep `capRankedRebuildByCode` + legacy endpoints untouched. Response payloads unchanged.

**1c. Create `index/IndexLoadRunner.java`** — mirror `marketdata/HistLoadRunner.java` exactly: `@Component`, `@ConditionalOnProperty(name = "app.run-mode", havingValue = "index-load")`, `ApplicationRunner`, package-private `int runLoad()`, then `System.exit(SpringApplication.exit(context, () -> exitCode))`.

Config via `@Value`:
- `app.index-load.year` (default `0` = current year)
- `app.index-load.scope` (default `russell1000`)
- `app.index-load.skip-refresh` (default `false`)
- `app.index-load.ticker` (default empty; single-ticker smoke-test mode via `refreshSingleTicker`, still rebuilds)
- `app.index-load.min-processed` (default `800`)

`runLoad()` policy:
1. Unless skip-refresh, run refresh; log processed/skipped + skip reasons.
2. **Guard**: if refresh ran and `processed < min-processed` (single-ticker mode: `processed == 0`), skip rebuild and return 1 — yesterday's members are strictly better than a wiped/shrunken index.
3. Otherwise `rebuildAll(year)`, log each `RebuildResult`; `partial == true` is fine (FAT1000 is routinely partial). Return 0.
4. Any exception → log, return 1. (If rebuild throws after a good refresh, metrics are persisted; next run or manual `/admin/indexes/rebuild` recovers.)

**1d. `src/main/resources/application.properties`** — extend the run-mode comment to mention `index-load`; add:
```
app.index-load.year=${INDEX_LOAD_YEAR:0}
app.index-load.scope=${INDEX_LOAD_SCOPE:russell1000}
app.index-load.skip-refresh=${INDEX_LOAD_SKIP_REFRESH:false}
app.index-load.ticker=${INDEX_LOAD_TICKER:}
app.index-load.min-processed=${INDEX_LOAD_MIN_PROCESSED:800}
```

### Part 2 — Terraform (`springboot/deploy/terraform/`)

Extend the existing module; **no renames of existing resources** (renaming would destroy/recreate the ECR repo, cluster, roles, schedule). Reuse: ECR repo + image (same jar, mode via env var), cluster, execution/task roles, task SG + Postgres ingress rule, scheduler role, `fattorestreet/env` secret wiring. New per-job resources get their own name prefix.

`variables.tf` additions:
- `index_load_name_prefix` (default `"fattorestreet-index-load"`)
- `index_load_task_cpu` (default `512`) / `index_load_task_memory` (default `2048`) — the load is SEC-network-bound (≥250 ms throttle, mostly idle); tune after first run
- `index_load_scope` (default `"russell1000"`)
- `index_load_schedule_expression` (default `"cron(30 9 * * ? *)"` — 3 h after hist-load's `cron(30 6 * * ? *)`, shared `schedule_timezone`)
- `index_load_schedule_enabled` (default `true`)

`main.tf` additions:
- `aws_cloudwatch_log_group.index_load` — `/ecs/${var.index_load_name_prefix}`
- `aws_ecs_task_definition.index_load` — same FARGATE/ARM64/awsvpc pattern, reuse both IAM roles + ECR image `:${var.image_tag}`; env `APP_RUN_MODE=index-load`, `INDEX_LOAD_SCOPE`, `DB_URL`, `DB_USERNAME`; secrets `POSTGRES_PASSWORD`, `SECRET_KEY` identical to hist-load; container name `index-load`
- Edit `data.aws_iam_policy_document.scheduler_run_task` — add the new task def ARNs to `ecs:RunTask` resources (in-place policy update; PassRole already covers reused roles)
- `aws_scheduler_schedule.index_load` — same target shape (existing cluster, public subnets + task SG, `assign_public_ip = true`, retry 0)
- Do **not** touch the task SG description — changing it forces SG replacement

`outputs.tf`: `index_load_task_definition_family`, `index_load_log_group_name`, `index_load_schedule_name`.
`terraform.tfvars.example`: index-load block, e.g. `index_load_schedule_expression = "cron(0 5 * * ? *)"` with the existing `America/New_York` timezone (hist load at 02:00 ET finishes in minutes; 05:00 ET stays pre-market).

### Part 3 — Tests (`springboot/src/test/java/com/fattorestreet/sec_api/`)

- **`index/IndexLoadServiceTest.java`** — `@ExtendWith(MockitoExtension.class)`: scope dispatch (russell1000/iwb/all), unknown scope throws, `rebuildAll` order FAT100→FAT1000→FAT50 via Mockito `InOrder`.
- **`index/IndexLoadRunnerTest.java`** — mirror `HistLoadRunnerTest` (`ReflectionTestUtils.setField` for config): exit 0 on success and on partial skips above threshold; exit 1 + `verify(never())` rebuild when below `min-processed`; skip-refresh mode rebuilds only; refresh/rebuild exceptions → 1; `year=0` resolves to current year; ticker mode uses `refreshSingleTicker`.
- **`controller/AdminControllerTest.java`** — retarget the no-code rebuild test (~line 477) and refresh-stocks scope tests (~lines 278–343) to stub `@MockitoBean IndexLoadService`; single-code/legacy tests unchanged. Keep the JaCoCo floor green (`mvn verify`).

### Part 4 — Docs

- `springboot/README.md` — add `index-load` run mode next to `hist-load`/`APP_RUN_MODE`, and note the daily Fargate schedule + `INDEX_LOAD_*` env vars in the market-index section (~lines 124–153).
- `springboot/deploy/terraform/README.md` — retitle to cover both nightly loads; add index-load section: schedule (05:00 ET, after hist load), guard behavior, manual `aws ecs run-task` test using the new outputs, tuning rows; note there is no Celery/beat entry to disable (unlike hist-load) — admin endpoints stay for manual runs.
- `docs/API_REFERENCE.md` — no changes (no endpoint/payload changes).

### Rollout order

1. Merge Java + tests; push the ARM64 image to the existing ECR repo (`docker buildx build --platform linux/arm64 -t "$REPO:latest" --push .`) — the runner must be in the image before the schedule fires.
2. `terraform apply` (optionally `index_load_schedule_enabled = false` first).
3. Manual `aws ecs run-task` test, then enable the schedule.

## Verification

Local:
- `cd springboot && mvn test`, then `mvn verify` (JaCoCo floor).
- Smoke test against local Postgres (needs a listing with CIK + DailyPrice, e.g. AAPL): `APP_RUN_MODE=index-load INDEX_LOAD_TICKER=AAPL mvn spring-boot:run` — expect one-ticker refresh, three rebuilds logged, exit 0 (`echo $?`).
- Guard check: `INDEX_LOAD_TICKER=ZZZZBAD` → exit 1, no rebuild logged.
- `APP_RUN_MODE=server` still serves normally (runner bean absent).

AWS:
- One-off: `aws ecs run-task --cluster $(terraform output -raw ecs_cluster_name) --task-definition $(terraform output -raw index_load_task_definition_family) --launch-type FARGATE --network-configuration ...` (same shape as the README hist-load test); `aws logs tail $(terraform output -raw index_load_log_group_name) --follow`.
- Confirm exit code 0 (`aws ecs describe-tasks` → `containers[0].exitCode`), refresh reaches 100%, three rebuild lines; `GET /index-members?code=FAT50|FAT100|FAT1000` reflects the run. Check CloudWatch memory to validate the 2048 MiB default. After the first scheduled night, confirm the 05:00 ET firing and that hist-load finished well before.

## Accepted decisions & residual risks

- **Shared `IndexLoadService` refactor**: chosen (user-confirmed); payload-identical, small test updates.
- **`INDEX_LOAD_MIN_PROCESSED=800` guard**: chosen (user-confirmed); protects against partial SEC outages and the empty-new-year case. Note: early January, a legitimately fresh year starts at 0 processed — the first successful full refresh of the year clears the guard naturally.
- **Naming debt**: shared cluster/ECR/roles keep the `fattorestreet-hist-load` name to avoid destroy/recreate; only new per-job resources use `fattorestreet-index-load`. Cosmetic, deliberate.
- **Schedule coupling**: 05:00 ET assumes hist-load (02:00 ET) finishes in minutes as documented; if it ever runs long, metrics use the prior close and self-correct the next night.

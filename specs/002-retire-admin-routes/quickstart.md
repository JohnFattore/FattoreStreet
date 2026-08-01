# Quickstart: Validating the Admin-Route Retirement

Runnable checks that prove the feature works end to end. Run them in order — the sequence
mirrors the deploy ordering in `research.md` §9, which exists so a broken replacement never
leaves you without a trigger path.

## Prerequisites

- `AWS_PROFILE=fattorestreet` (set in `.claude/settings.json`; sessions expire hourly)
- Java 25 + Maven, Node 20+, `uv` for Django
- A local Postgres with the springboot schema, or the compose infra stack
- Terraform, run from `springboot/deploy/terraform/` (local state, no remote backend)

## Scenario 1 — New runners work locally (before any deploy)

Each new mode boots, does its work, and exits with the right code. Run against a dev database.

```bash
cd springboot

# asset-load: should upsert Assets/Listings and exit 0
APP_RUN_MODE=asset-load SEC_CONTACT_EMAIL=you@example.com mvn spring-boot:run

# validate-prices, log-only (no SNS ARN set)
APP_RUN_MODE=validate-prices VALIDATE_PRICES_INDEX_CODE=FAT50 \
  VALIDATE_PRICES_MAX_TICKERS=5 \
  DJANGO_PORTFOLIO_BASE_URL=http://localhost:8000/portfolio mvn spring-boot:run
```

**Expected**: each process logs one structured summary line and exits. `echo $?` is `0`.
The JVM must actually terminate — a runner that leaves the web server bound is the failure
mode that silently degrades a Fargate task to `server` mode and bills forever.

**Guard checks**:
- Run `asset-load` with `SEC_CONTACT_EMAIL` unset. SEC answers 403 to everything, and the task
  must exit `1` rather than reporting a successful load of nothing.
- Run `validate-prices` with an index code that has no members. Must exit `1`, not report
  "0 tickers checked, all healthy."

**Note**: there is no `adjust-prices` mode to test. The nightly `hist-load` already recomputes
the full adjusted series for every ticker in its working set (`research.md` §2).

## Scenario 2 — Server mode is unaffected, and needs no SECRET_KEY

The default path must not notice any of this, and must no longer require the shared secret.

```bash
cd springboot
env -u SECRET_KEY mvn spring-boot:run   # APP_RUN_MODE unset → server, SECRET_KEY absent

curl "http://localhost:8080/quarters?ticker=AAPL"              # 200
curl "http://localhost:8080/filing-summaries?ticker=AAPL"      # 200, still works
curl -i "http://localhost:8080/admin/asset-load"               # 404, not 401/403
curl -i -H "Authorization: Bearer $ANY_TOKEN" \
     "http://localhost:8080/admin/asset-load"                  # 404 even with a valid token
```

**Expected**: startup succeeds with `SECRET_KEY` unset (SC-007). This is the point of the
check — the old `jwtDecoder` bean threw `IllegalStateException` on a blank secret, so a clean
boot proves the bean is genuinely gone rather than merely unreachable.

The 404 on `/admin/*` is the contract test from `contracts/http-api-changes.md`: 404 proves the
route is gone, where 403 would only prove it is guarded.

## Scenario 3 — Filing summaries still read, never write

The generator is retired; the data and read path survive.

```bash
# Schema is UNCHANGED — no migration, entity retained. Startup must succeed as before.
cd springboot && mvn spring-boot:run
psql "$DB_URL" -c "SELECT count(*) FROM filing_summaries;"   # unchanged from before the work

cd ../react-app && npm run build && npx vitest --run
npm run dev   # open /sec-data?ticker=AAPL
```

**Expected**: the SEC Data page renders stored summaries exactly as before, with no console
errors and no failed requests.

**The removal check that matters** — nothing in `src/main` can write a `FilingSummary`:

```bash
cd springboot
grep -rn "filingSummaryRepository\.\(save\|delete\)\|new FilingSummary" src/main/java
# expect: no matches

ls src/main/java/com/fattorestreet/sec_api/filing/ 2>/dev/null
# expect: no such directory — the package held only FilingSummaryService
```

`FilingSummaryRepository` should still be referenced by exactly one caller,
`PublicController.filingSummaries`, and only for reads.

## Scenario 4 — Full CI gate

This is SC-005 and the real acceptance bar. Run everything the pipeline runs.

```bash
cd react-app && npm run lint && npm run lint:styles && npm run format:check \
  && npm run build && npx vitest --run

cd ../django && uv run python manage.py test

cd ../springboot && ./mvnw spotless:apply && mvn verify
```

**Watch specifically**: `mvn verify` runs `jacoco:check` against the bundle line-coverage
floor in `pom.xml`. Deleting `AdminControllerTest` (696 lines of test) alongside
`AdminController` moves that ratio, and the direction is not predictable by inspection. If it
drops below the floor, add runner tests — never lower the floor to make the build pass.
Retaining `FilingSummary`, its repository, and the public endpoint keeps their existing
coverage, which softens this.

Also confirm Error Prone stays quiet: `UnusedMethod` / `UnusedVariable` are ERROR-tier on
`src/main`, so any constructor-injected field orphaned by the controller deletion fails the
build rather than lingering.

Django's suite is in the list because `SECRET_KEY` is shared — running it proves the secret
cleanup did not reach past Spring Boot into Django's configuration.

## Scenario 5a — Deploy fundamentals-load (BLOCKING, do this first)

`fundamentals_load` is written in `main.tf` but has never been applied. Confirm the gap, close
it, and verify — all while `/admin/sync-frames` still exists as a fallback.

```bash
# Confirm the gap
aws scheduler list-schedules --query 'Schedules[].Name' --output text
# today: fattorestreet-index-load  fattorestreet-hist-load   (no fundamentals)

aws logs describe-log-groups --log-group-name-prefix /ecs/ \
  --query 'logGroups[].logGroupName' --output text
# today: /ecs/fattorestreet-hist-load  /ecs/fattorestreet-index-load

# Close it
cd springboot/deploy/terraform && terraform plan   # expect fundamentals_load resources to CREATE
terraform apply

# Verify with one manual run, and TIME IT — duration is unknown and both
# new slots are placed relative to it
aws ecs run-task --cluster fattorestreet-hist-load \
  --task-definition fattorestreet-fundamentals-load \
  --launch-type FARGATE --network-configuration '...'
aws logs tail /ecs/fattorestreet-fundamentals-load --follow
```

**Expected**: exit code 0, and a `Fundamentals load finished in Xm Ys -- ... quartersPersisted=N`
line with N > 0. Only after this may `/admin/sync-frames` be deleted (FR-014).

**If `terraform plan` shows unrelated drift**, stop and reconcile before applying — local state
plus a gitignored `terraform.tfvars` means the repo cannot tell you what is really deployed.

## Scenario 5b — Deploy and verify the new tasks

**Order matters twice over.** `terraform apply` is not a deploy: a task definition referencing
a runner the ECR image lacks degrades silently to `server` mode and runs forever. And the
`SECRET_KEY` injection must outlive the code that reads it, not the reverse.

```bash
# 1. Merge the runner additions AND the JWT code removal to main.
#    CI publishes to GHCR *and* ECR. Wait for docker-build.yml to finish.

# 2. Apply the new task definitions and schedules — this is also where the
#    SECRET_KEY entries come out of the three existing secrets blocks.
cd springboot/deploy/terraform && terraform plan && terraform apply

# 3. Run each new task once, by hand, before trusting its schedule
aws ecs run-task --cluster fattorestreet-hist-load \
  --task-definition fattorestreet-asset-load \
  --launch-type FARGATE --network-configuration '...'

# 4. Watch it
aws logs tail /ecs/fattorestreet-asset-load --follow
```

**Expected per task**: `STOPPED` with `exitCode: 0`, and the summary line in CloudWatch.

**Then confirm the cluster is quiet** — a healthy cluster has zero running tasks between
scheduled runs:

```bash
aws ecs list-tasks --cluster fattorestreet-hist-load --desired-status RUNNING
```

An empty list is the pass condition. A task still running an hour later means it degraded to
`server` mode: stop it immediately, since it bills until stopped.

**Confirm every schedule exists and none overlap** (SC-008):

```bash
aws scheduler list-schedules --query \
  'Schedules[].{Name:Name,State:State}' --output table
# expect 5: hist-load, index-load, fundamentals-load, validate-prices, asset-load
```

Cross-check each cron against the allocation table in `contracts/run-modes.md` **and against
observed durations**, not the nominal schedule. `hist-load` has been measured at 6h27m, 7h10m
and 15h32m from a 02:00 start — the long run was still going when `index-load` fired at 09:30.
That overlap predates this feature, but it is why both new jobs sit in the evening
(`validate-prices` Sun 20:00, `asset-load` 22:00). If `fundamentals-load`'s first measured
run is long, move them later still.

**Also verify the existing nightly jobs still run** after `SECRET_KEY` leaves their task
definitions. They never read it, but the first post-change `hist-load` is the proof.

The `aws-inspect` skill covers this class of check if you want it done conversationally.

## Scenario 6 — The weekly report actually arrives

```bash
aws ecs run-task --cluster fattorestreet-hist-load \
  --task-definition fattorestreet-validate-prices \
  --overrides '{"containerOverrides":[{"name":"validate-prices",
    "environment":[{"name":"VALIDATE_PRICES_MAX_TICKERS","value":"5"}]}]}' \
  --launch-type FARGATE --network-configuration '...'
```

**Expected**: an email at `johnefattore@gmail.com` within a couple of minutes containing the
index code, tickers checked, tickers out of tolerance, the worst offenders, and break dates.

**First run only**: SNS sends a subscription-confirmation email that must be clicked once.
Until confirmed, the publish succeeds and the message goes nowhere — a silent failure worth
checking for explicitly.

**Also verify the licensing invariant (FR-007)**: after the run, confirm nothing was written.

```sql
SELECT max(created_at) FROM corporate_actions;  -- unchanged from before the run
```

The report is derived yfinance comparison data, and it is allowed to exist only because it is
ephemeral, unpersisted, and never user-facing. A validation run that writes anything is a
defect, not a tuning issue.

## Scenario 7 — Rollback

If a replacement task misbehaves after the routes are deleted, the routes are not coming back
quickly. Disable the schedule and fix forward:

```bash
# Disable one schedule without destroying it
terraform apply -var='validate_prices_schedule_enabled=false'
```

Prior data is unaffected — every job is idempotent and upserts, so a skipped week costs
freshness, not correctness. The one exception is `index-load`, whose rebuild deletes members
by index code before reinserting; its existing `min-processed` guard already prevents a
mostly-failed refresh from wiping live indexes.

**Restoring ETF adjustment**, if it is ever revisited, needs no rollback — the code path in
`PriceAdjustmentService` is untouched. It is a one-variable change:
`hist_load_equity_only = false`, accepting the runtime that flag was added to avoid. Note that
the nightly job is already at 6h27m–15h32m with funds *excluded*, so this is not a free flip.

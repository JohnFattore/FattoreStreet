# Phase 0 Research: Retire Spring Boot Admin Routes

All decisions below were resolved against the live codebase; no NEEDS CLARIFICATION remain.

**Revised 2026-08-01** per user direction: ETF corporate actions are out of scope (§2),
Spring Boot's `SECRET_KEY` dependency is removed in-scope (§7), and filing summaries are
frozen rather than deleted (§8).

## 1. Route-to-replacement coverage map

The controlling question for this feature: does every deleted route have a home?

| Deleted route | Method | Replacement | Status |
|---|---|---|---|
| `/admin/asset-load` | GET | `APP_RUN_MODE=asset-load`, monthly schedule | **New** |
| `/admin/test` | GET | none — deleted outright | **Dropped** |
| `/admin/load-hist` | GET | `APP_RUN_MODE=hist-load` | Exists |
| `/admin/adjust-prices` | GET | `hist-load` nightly adjustment phase — already fully covers it | Exists |
| `/admin/validate-adjusted-prices` | GET | `APP_RUN_MODE=validate-prices`, weekly + SNS email | **New** |
| `/admin/summarize-filings` | GET | none — generator retired | **Dropped** |
| `/admin/sync-frames` | GET | `fundamentals-load` with `FUNDAMENTALS_LOAD_START_YEAR=2009` | ⚠️ **Code exists, never deployed — see §12** |
| `/admin/indexes/refresh-stocks` | POST | `index-load` refresh phase | Exists |
| `/admin/indexes/rebuild` | POST | `index-load` rebuild phase | Exists |
| `/admin/indexes/rebuild-fattore-50` | POST | `index-load` (rebuilds all) | Exists |
| `/admin/indexes/rebuild-fattore-100` | POST | `index-load` (rebuilds all) | Exists |
| `/admin/indexes/rebuild-fattore-1000` | POST | `index-load` (rebuilds all) | Exists |

**Decision**: `/admin/test` is deleted with no replacement. It is a hardcoded smoke test that
fetches AAPL companyfacts (CIK 320193) and dumps a revenue array — a debug leftover with no
caller in the repo.

**Rationale**: Its only value was proving the SEC client works, which the `fundamentals-load`
task now demonstrates far more thoroughly, and which `WebServiceTest` covers in CI.

**Alternatives considered**: Keeping it as an actuator health indicator. Rejected — it makes a
live outbound SEC call on every health probe, which burns the rate-limit budget the scheduled
jobs depend on.

## 2. ETF price adjustment — deferred, not solved

**Finding**: `springboot/deploy/terraform/variables.tf:103` defines `hist_load_equity_only`
with `default = true`, and `terraform.tfvars` does not override it. `HistLoadRunner` passes
that flag into `AdjustmentOptions(force=false, etfOnly=false, equityOnly=true, validate=false)`.
The `HistLoadRunner` Javadoc states this explicitly: fund adjustment is left "to the admin
endpoint." `/admin/adjust-prices?etfOnly=true` is therefore the only path that has ever
adjusted fund prices.

**Decision (user direction)**: ETF corporate actions and adjusted prices are **out of scope**.
No ETF adjustment job is built and no schedule covers funds. Equities are the whole focus.

**Consequence, stated plainly**: deleting the route closes the last ETF adjustment path. Funds
keep whatever adjusted values they already have, frozen, until this is revisited. This is a
knowing deferral, not an oversight — it must be recorded in `springboot/README.md` and the
terraform runbook so a future reader does not mistake it for a bug.

**What is built to replace it: nothing.** An earlier draft proposed a monthly forced
`adjust-prices` sweep. Reading `PriceAdjustmentService` and measuring the live job killed that
idea. Both halves of the justification were wrong.

### What `force=true` actually does

Two distinct effects, both in `adjustAllTickers`:

1. **Working-set expansion** (lines 216–220). Without force, the set is
   `findTickersWithUnadjustedPrices()` (rows with NULL adjusted columns — i.e. whatever the
   day's load just inserted) plus `scheduleStaleDetections(...)`. With force,
   `tickersToProcess.addAll(allPriceTickers)` widens it to **every ticker with price data**,
   ~24k.
2. **Detection-gate bypass** (line 276):
   `shouldFetchSec = options.force() || (inDetectionScope && (scheduledDetections.contains(ticker) || ...))`.
   Force short-circuits *both* the FAT1000 detection scope and the rolling staleness schedule,
   so **every** ticker in the set gets a fresh SEC fetch, each followed by
   `Thread.sleep(100)`.

### Why that is not runnable, measured

Live `/ecs/fattorestreet-hist-load` logs, 2026-08-01:

```
IEX HIST load finished in 6m 16s -- processed=1, skipped=18, notAvailable=1, errors=0
Price adjustment finished in 380m 34s -- tickersProcessed=5572, failedTickers=0,
  scheduledDetections=133, jumpTriggeredDetections=5, pricesUpdated=190563, snappedActions=10661
```

and 2026-07-31: `424m 2s -- tickersProcessed=5884, scheduledDetections=133`.

So the *unforced* nightly pass already costs **6h20m–7h04m** for ~5,600 tickers doing only
**133** SEC detections. Force would take that to ~24k tickers (4.3×) with ~24k detections
(180×). This is the 17-hour problem in its extreme form, and no cadence makes it acceptable.

### Why no replacement is needed anyway

The premise that a late-discovered action never reaches already-adjusted rows is **false**.
`applyAdjustments(ticker, actions)` is called **unconditionally** for every ticker in the
working set (line 309), reads *all* stored actions for that ticker, recomputes the cumulative
factor across the whole series, and `applyAdjustedIfChanged` rewrites any row whose computed
value differs. It does not skip rows that already have adjusted values.

And the working set is not narrow: every trading day's load inserts NULL-adjusted rows for
every actively traded ticker, which is why `tickersProcessed` is ~5,600 nightly, not a
handful. `pricesUpdated=190563` on a day that loaded one trading session is the proof — those
are overwhelmingly *back-history* rows being recomputed, not the day's new rows.

**Therefore**: a corporate action discovered on day N is folded into the full adjusted series
on night N+1, automatically, for any ticker that still trades. The nightly job already is the
sweep. `force=true` only ever added *SEC re-detection outside the scope and staleness rules* —
which is exactly the cost the rolling 1/7 refresh and the jump trigger were designed to bound.

**Decision**: no `adjust-prices` run mode. Equity adjustment stays exactly where it is.

**Residual capability lost**: on-demand forced SEC re-detection for a chosen ticker. This is a
debugging affordance, and for any actively traded ticker the nightly run reaches it anyway.
Recorded in "Known Reductions in Capability."

**yfinance note**: the runner must never set the validate flag. `AdjustmentOptions`'s fourth
field triggers the dev-only yfinance comparison, which has no place in a job that writes to
the database.

## 3. Report delivery mechanism

**Decision**: SNS. Publish the report from the validation runner to a topic subscribed by
`johnefattore@gmail.com`, reusing the pattern already at `main.tf:538-556`.

**Rationale**: The Terraform module already creates an SNS topic with an email subscription
driven by `var.notification_email`, and that subscription is already confirmed. Adding
`sns:Publish` to the task role is a few lines. No identity verification, no new service.

**Alternatives considered**:
- **SES**: supports HTML and CSV attachments, which would carry the full per-ticker
  breakdown. Rejected as disproportionate — it needs a verified identity, sandbox handling,
  and bounce/complaint plumbing for a single-recipient internal diagnostic.
- **CloudWatch logs only**: no push, so a slow drift in accuracy goes unnoticed until someone
  looks. Rejected, since noticing drift is the entire purpose.

**Constraint this imposes (FR-006)**: SNS email is plain text with a 256 KB message limit.
The runner must render a bounded summary — headline counts, the N worst tickers, the N most
significant break dates — not a per-ticker dump. `AdjustedPriceValidationService` already
caps its batch summary (`MAX_SUMMARY_WORST_TICKERS = 10`, `MAX_SUMMARY_BREAKS = 20`), so the
existing `summarizeBatch` output is already near the right shape.

**Topic choice**: a dedicated topic (`${name_prefix}-validation-reports`) rather than reusing
`task_failures`. The failure topic's SNS policy grants publish only to
`events.amazonaws.com` conditioned on the failure rule's ARN; publishing from the task role
would require widening that policy and would mix routine reports into a channel whose every
message currently means "something broke."

## 4. Validation scope and cadence

**Decision**: Weekly, scoped to members of a configurable index code defaulting to `FAT1000`.

**Rationale**: `AdminController.validateAdjustedPrices` with no ticker iterates
`dailyPriceRepository.findDistinctTickers()` — the full IEX HIST universe, roughly 24k
symbols — and issues one Django→yfinance HTTP call per ticker. That is infeasible as a
scheduled job and would hammer a reference source the data-licensing rule already restricts.
Scoping to one index mirrors the precedent set by
`app.price-adjustment.detection-index-code`, which scopes automatic SEC detection to FAT1000
for exactly this reason. Weekly fits a diagnostic that tracks slow drift.

FAT1000 is a cap-ranked equity index, so this scope also aligns with the equities-only
decision in §2 — the report will not flag ETFs whose adjustment nobody is maintaining.

**Alternatives considered**: nightly at the same scope (more yfinance traffic and another
schedule slot to keep clear of the SEC-rate-limited jobs, for a signal that does not change
daily); fully configurable scope including explicit ticker lists (more knobs than the single
consumer justifies — an index code plus a run-task override covers ad-hoc reruns).

## 5. Data-licensing compliance for the emailed report

**Constraint**: `.claude/rules/data-licensing-commercial-free.md` permits yfinance only for
dev/verification diagnostics, ephemeral and in-memory, never persisted, never returned from
an API, never rendered in the UI.

**Decision**: Proceed. The report is a private operational email to the repository owner,
computed in memory and discarded. It is not persisted, not served, and not user-facing.

**Rationale**: The rule's three prohibitions are storage, API responses, and UI rendering.
A diagnostic email to the operator is none of those, and it is the same category of use the
rule explicitly blesses ("sanity checks, comparisons, logs").

**Implementation guardrails**:
- The runner writes nothing to the database. This must be asserted in its test.
- The report body carries derived comparison statistics (ratios, deviation counts, break
  dates), not raw yfinance price series.
- `AdjustedPriceValidationService`'s class Javadoc currently asserts "the nightly hist-load
  job never calls it." That remains true — the new job is a separate weekly task — but the
  Javadoc must be updated to describe the new caller rather than implying no job calls it.

## 6. Network path to the reference data

**Finding**: all three existing task definitions run with `assign_public_ip = true` on public
subnets (`main.tf:305-309`, `410-414`, `519-523`). The only SG rule to the EC2 box is Postgres
on `var.db_port` (`main.tf:91-98`); there is no rule for Django's port.

**Decision**: point `DJANGO_PORTFOLIO_BASE_URL` at the public HTTPS origin
(`https://fattorestreet.com/django/portfolio`) for the validation task.

**Rationale**: the task already has a public IP and egress, so the public origin works with
zero new security-group surface. Opening port 8000 from the task SG to the EC2 instance would
add an ingress rule for a weekly diagnostic that has a perfectly good public path.

**Alternatives considered**: a new SG rule for direct Django access. Rejected — more surface,
no benefit, and it would couple the task to Django's internal port.

## 7. Removing the auth logic and the SECRET_KEY dependency

**Finding**: `SecurityConfig` exists solely to gate `/admin/**` behind
`hasRole("ADMIN")`, granted only when a Django SimpleJWT token carries `user_id = 1`. Every
other matcher is `permitAll`, and the chain ends in `.anyRequest().permitAll()`. The only
consumers of `app.django-jwt-secret` (`= ${SECRET_KEY:}`) are `SecurityConfig`'s `jwtDecoder`
bean and its own error message.

**Decision (user direction — in scope, not a follow-up)**: remove the JWT resource-server
wiring *and* every path by which `SECRET_KEY` reaches Spring Boot.

**Code removals**
- `SecurityConfig`: `jwtDecoder`, `jwtAuthenticationConverter`, `authoritiesFromUserIdClaim`,
  `ADMIN_DJANGO_USER_ID`, the `/admin/**` matcher, and the `.oauth2ResourceServer(...)` call.
  Retain a minimal `SecurityFilterChain` for CORS, CSRF-disable, and stateless session policy.
- `application.properties`: the `app.django-jwt-secret=${SECRET_KEY:}` line and its comment.
- `pom.xml`: `spring-boot-starter-oauth2-resource-server` (line 167). **Keep**
  `spring-boot-starter-security` (line 163) — the retained filter chain needs it.

**Config removals — exact sites, verified by grep**

| File | Line(s) | Action |
|---|---|---|
| `springboot/deploy/terraform/main.tf` | 200, 369, 478 | Drop the `SECRET_KEY` entry from all three `secrets` blocks |
| `springboot/deploy/terraform/variables.tf` | 68 | Update the `env_secret_arn` description to stop naming `SECRET_KEY` |
| `springboot/deploy/terraform/terraform.tfvars.example` | 16 | Same |
| `springboot/deploy/terraform/README.md` | 66, 75 | Same |
| `deploy/docker-compose.dev.yml` | 55 | Drop `SECRET_KEY` from the **springboot** service env |
| `deploy/run.sh` | 28 | Comment-only legacy reference; reword, do not delete the key |

**Critical scoping constraint**: `SECRET_KEY` is a **shared** secret. Django requires it and
`deploy/docker-compose.dev.yml:22` sets it in the `x-django-env` anchor. Only Spring Boot's
consumption is removed. The `fattorestreet/env` Secrets Manager blob keeps the key, and the
`django` service keeps reading it. Removing line 22 would break Django.

**Rationale**: with no authenticated route left, the decoder is dead code, and Error Prone
promotes `UnusedMethod`/`UnusedVariable` to ERROR on `src/main` — leaving it wired up "for
later" fails the build. Dropping the secret injection is genuine least-privilege cleanup: three
Fargate task roles stop being handed a credential they cannot use.

**Verification (SC-007)**: Spring Boot must start and serve every public route with
`SECRET_KEY` unset. Worth an explicit local run, since the old property defaulted to empty
(`${SECRET_KEY:}`) and `jwtDecoder` threw `IllegalStateException` on a blank value — that
startup failure mode should be gone, not merely unreachable.

**Testing consequence**: `testsupport/TestJwtTokens` is used by `SecurityConfigTest` and
`AdminControllerTest`. Both go away or are gutted; delete the helper once nothing references
it. `SecurityConfigTest` should be rewritten to assert the new reality — public routes reachable
without a token, `/admin/*` returning 404 — rather than deleted outright.

**Secrets-check note**: `deploy/docker-compose.dev.yml` lines carry
`# pragma: allowlist secret` markers for their dev-only placeholder values. Removing a line
removes its pragma with it; no baseline edit is needed, and per
`.claude/rules/secrets-check.md` the baseline must stay empty regardless.

## 8. Filing summaries — freeze the generator, keep the data

**Decision (user direction)**: remove only the code that *creates* filing summaries. The
entity, the table, the stored rows, the public read endpoint, and the UI component all stay,
so the feature may be revisited later without a schema migration to undo.

**Remove**

| Path | Note |
|---|---|
| `filing/FilingSummaryService.java` | The 10-K MD&A fetch + LLM summarization pipeline |
| `filing/` package | Becomes empty — `FilingSummaryService` is its only member (verified) |
| `AdminController.summarizeFilings` | Goes with the whole controller |
| `filing/FilingSummaryServiceTest.java` | Tests the deleted service |
| `LLM_SERVER_URL` property | `FilingSummaryService` is its only `src/main` consumer (verified) |

**Keep, unchanged**

| Path | Why |
|---|---|
| `model/FilingSummary.java` | Entity backing the retained read path |
| `repository/FilingSummaryRepository.java` | Used by `PublicController.filingSummaries` |
| `PublicController` `GET /filing-summaries` | Reads only via `findByTickerOrderByFilingDateDesc` — no dependency on the deleted service (verified) |
| `SecurityConfig` `"/filing-summaries"` matcher | Route still exists and must stay public |
| `PublicControllerTest` filing cases | Still valid |
| `filing_summaries` + `filing_summaries_aud` tables | Data retained; **no `V5` migration** |
| React `FilingSummaries.tsx`, `SECData.tsx` usage, `IFilingSummary`, `getFilingSummaries`, MSW handler | Read path intact end to end |

**Consequence to document**: the table becomes append-never. Existing summaries render
forever; no new 10-K is ever summarized. `springboot/README.md` must say so, or the next
reader will file it as a bug when a new filing produces no summary.

**Envers note**: `FilingSummary` is audited (`filing_summaries_aud` exists in
`V1__initial_schema.sql:198`). With no writer, the audit table simply stops receiving
revisions. Nothing to change.

**Layout rule**: `.claude/rules/springboot-java.md` lists `filing/` in the canonical package
layout. Removing the package means updating that rule file too.

**Explicitly out of scope**: the two blog posts in `django/blog/learning-topics/` that discuss
the summarization pipeline (151, 166). They are historical journal entries, not documentation
of current behavior, and the blog sync publishes them from file. Leave them alone.

## 9. Ordering constraint (deploy safety)

**Finding**: `.claude/rules/infrastructure.md` is explicit — "`terraform apply` is not a
deploy." A task definition referencing a runner the ECR image does not contain silently
degrades to `server` mode and runs (and bills) forever.

**Decision**: sequence the work as (1) add new runners and merge to `main` so CI publishes the
image to ECR, (2) `terraform apply` the new task definitions and schedules, (3) verify by
running each new task once, (4) only then delete the routes, the controller, the auth config,
and the React admin page.

**Rationale**: this keeps a working trigger path available throughout. If a new runner
misbehaves, the old route is still there to fall back on until step 4.

**Extra care on the `SECRET_KEY` removal**: dropping it from the task definitions is a
Terraform change, while dropping the code that reads it is an image change. Remove the *code*
dependency first and let it deploy; then remove the injection. Reversing that order gives a
window where the running image expects a secret the task no longer provides — harmless today
because the property defaults to empty, but only by luck.

## 10. Test-coverage consequence

**Finding**: `AdminControllerTest` is 696 lines and `Admin.test.tsx` is 266. Deleting them
removes a large block of covered lines from both suites.

**Decision**: add runner tests (`AssetLoadRunnerTest`, `ValidatePricesRunnerTest`) following
the `HistLoadRunner`/`IndexLoadRunner` test pattern, and check the JaCoCo bundle line-coverage
floor after the deletions.

Note this is now **two** new test classes, not three — dropping the `adjust-prices` runner
(§2) also drops its test, so less new coverage offsets the deletions. Measure, don't assume.

**Rationale**: `mvn verify` fails if bundle line coverage drops below the floor in `pom.xml`.
Deleting a well-tested controller alongside its tests can move the ratio either way; it must
be measured, not assumed. Per the project rule, the floor is never lowered to make a build
pass — if coverage drops, add tests.

Note that keeping `FilingSummary`, its repository, and the public endpoint (§8) preserves
their existing coverage, which softens this risk compared to a full deletion.

## 11. Schedule slot allocation

**Constraint**: the SEC rate limiter (`sec.http.min-interval-ms`) is **per-process**. Two tasks
calling `data.sec.gov` concurrently double the effective request rate toward SEC's ceiling and
earn 403s for both. `.claude/rules/infrastructure.md` makes keeping schedules clear of each
other a standing requirement, not a nicety.

### Measured reality (AWS, checked 2026-08-01)

Live state does **not** match the repo's documentation. Verified with `aws scheduler
list-schedules`, `aws ecs list-task-definitions`, and CloudWatch log-stream timestamps:

| Job | Live cron (ET) | Measured wall-clock | Notes |
|---|---|---|---|
| `hist-load` | `cron(0 2 * * ? *)` | **6h27m / 7h10m / 15h32m** | Ends 08:27, 09:10, **17:32** on three consecutive days |
| `index-load` | `cron(30 9 * * ? *)` | **11–13 min** | Live cron is **09:30**, not the 05:00 in `terraform.tfvars.example` |
| `fundamentals-load` | — | — | **Does not exist.** No schedule, no task definition, no log group, 0 refs in `terraform.tfstate` |

Two consequences:

**(a) The existing schedules already overlap.** On 2026-07-30 `hist-load` ran until 17:32,
straight through `index-load`'s 09:30 start. The "keep schedules clear" rule is violated in
practice today, driven by `hist-load`'s 2.4× runtime variance. This is pre-existing, not
something this feature introduces — but it means new slots must be placed against the *observed
tail*, not the nominal cron.

**(b) `fundamentals-load` has never been applied.** See §12.

### Allocation

Every run mode gets a cadence (FR-013). Only two new jobs remain after §2 dropped
`adjust-prices`.

| Mode | Cadence | Slot (ET) | SEC-bound? | Rationale |
|---|---|---|---|---|
| `hist-load` | daily | 02:00 | Yes | unchanged |
| `index-load` | daily | 09:30 | Yes | unchanged |
| `fundamentals-load` | daily | 13:30 | Yes, heavily | as coded; **must be deployed** (§12) |
| `validate-prices` | weekly | **Sun 20:00** | No — Django/yfinance | Sunday evening: no weekday `hist-load` tail can reach it, and it contends for no SEC budget |
| `asset-load` | monthly | **1st, 22:00** | Yes, briefly | Late enough to clear the 13:30 fundamentals load; short job |

`validate-prices` moved from the earlier Sunday-08:00 proposal to **Sunday 20:00** precisely
because of finding (a): a Saturday-night `hist-load` with a 15h tail would still be running at
08:00 Sunday. Evening is clear of every observed tail.

`asset-load` moved from 20:00 to **22:00** for the same reason — 20:00 is inside the window a
long `fundamentals-load` could still occupy, and its runtime is unmeasured (§12).
(`validate-prices` can stay at 20:00 because it makes no SEC calls, so a fundamentals tail
cannot contend with it.) The 20:00/22:00 staggering also keeps the two new jobs from firing
together when the 1st of the month falls on a Sunday, which identical slots would violate
(SC-008).

**Still unmeasured**: `fundamentals-load` has never run, so its duration is unknown. Measure it
on first deploy and re-check both new slots against the result.

**Enable flags**: each new schedule gets its own `*_schedule_enabled` variable mirroring
`index_load_schedule_enabled`, so a misbehaving job can be parked with a one-line
`terraform apply` rather than a destroy (quickstart Scenario 7).

**Worth raising separately**: `hist-load`'s 6–15 hour adjustment phase is the dominant cost in
the whole system (380–424 minutes vs ~6 minutes for the actual price load). Nothing in this
feature addresses it, but it is the obvious next optimization target.

## 12. `fundamentals-load` is defined but not deployed — a blocking prerequisite

**Finding** (AWS, 2026-08-01): `springboot/deploy/terraform/main.tf:439` defines
`aws_ecs_task_definition.fundamentals_load` and `:496` defines its schedule, and
`CLAUDE.md` describes "three Fargate one-shot tasks." The live account has **two**:

```
$ aws scheduler list-schedules --query 'Schedules[].{N:Name,S:State}'
fattorestreet-index-load  ENABLED
fattorestreet-hist-load   ENABLED

$ aws logs describe-log-groups --log-group-name-prefix /ecs/
/ecs/fattorestreet-hist-load   /ecs/fattorestreet-index-load
```

No `fundamentals-load` task definition, no schedule, no log group, and zero occurrences of
"fundamentals" in `terraform.tfstate`. The resources were written but never applied.

**Consequence**: `GET /admin/sync-frames` is currently the **only** working path for
fundamentals sync. Deleting it as part of this feature, without first deploying
`fundamentals-load`, leaves quarterly financials with no refresh mechanism at all.

**Decision**: deploying and verifying `fundamentals-load` is a **blocking prerequisite**
(FR-014), sequenced before any route deletion. It needs no new code — the runner already
exists and is tested; it needs a `terraform apply` and one verification run.

**Why this was invisible from the code**: `terraform.tfvars` is gitignored, so the repo cannot
show which variables are set, and local state means nothing in git reflects what was applied.
This is exactly the failure mode `.claude/rules/infrastructure.md` warns about with
"`terraform apply` is not a deploy" — here the inverse, a merge that never got applied.

**Also worth checking during implementation**: whether other drift exists between `main.tf` and
the live account. `index_load_schedule_expression` differs from the committed example
(09:30 live vs 05:00 in `terraform.tfvars.example`), which is legitimate tfvars override rather
than drift, but it shows the example file is stale and misleading.

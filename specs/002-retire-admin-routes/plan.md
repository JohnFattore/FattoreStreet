# Implementation Plan: Retire Spring Boot Admin Routes

**Branch**: `002-retire-admin-routes` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-retire-admin-routes/spec.md`

**Revisions (2026-08-01)**: (1) ETF corporate actions and adjusted prices are out of scope.
(2) Spring Boot's `SECRET_KEY` dependency is removed in-scope alongside the auth logic.
(3) Filing summaries are frozen, not deleted — the generator goes, the data and read path stay.
(4) Every replacement job runs on a schedule; none is manual-invocation-only.
(5) **No `adjust-prices` job** — the nightly `hist-load` already covers it, verified against
`PriceAdjustmentService` and live CloudWatch timings. (6) `fundamentals-load` turns out to be
undeployed and must be applied before `/admin/sync-frames` can go.

## Summary

Delete all twelve `/admin/**` routes from the Spring Boot service and move every capability
worth keeping to a Terraform-managed one-off Fargate task selected by `APP_RUN_MODE`, each on
its own EventBridge schedule. Two replacement jobs are already deployed (`hist-load`,
`index-load`), one exists in code but was never applied (`fundamentals-load`), and two are new
(`asset-load`, `validate-prices`). Because
no authenticated route survives, the JWT resource-server configuration and Spring Boot's
`SECRET_KEY` dependency go with it. The 10-K summarization generator is retired while its
stored data keeps serving. The React admin page is deleted.

The technically interesting parts are not the deletions:

1. **A blocking infrastructure gap.** `fundamentals-load` is written in Terraform but has
   **never been applied** — no task definition, no schedule, no log group in the live account
   (verified 2026-08-01). `GET /admin/sync-frames` is therefore the only working fundamentals
   path today, and deleting it first would leave zero coverage.
2. **A logic extraction.** `AdminController.assetLoad` holds ~80 lines of SEC ticker-parsing
   inline. It must move into a service before the controller can be deleted.
3. **A shared-secret boundary.** `SECRET_KEY` is Django's too. Only Spring Boot's consumption
   is removed, and the removal order matters.
4. **A deploy-ordering hazard.** A task definition referencing a runner the ECR image lacks
   degrades to `server` mode and bills forever.
5. **One route needs no replacement at all.** `/admin/adjust-prices` is already fully covered
   by the nightly `hist-load` adjustment phase; `force=true` is not runnable at any cadence.
   An earlier draft of this plan proposed a monthly sweep — reading the service and measuring
   the live job showed it would be pure duplication. Dropped.

**Explicitly deferred**: ETF/fund corporate actions and adjusted prices. Deleting
`/admin/adjust-prices?etfOnly=true` closes the last path that ever adjusted funds; they freeze
at their current values until this is revisited. Knowing deferral, documented as such.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1), TypeScript 5 (React 18 / Vite), HCL
(Terraform), Python 3 (Django 5, untouched here)

**Primary Dependencies**: Spring Boot Web + Data JPA + Flyway + Security (resource-server
wiring being removed, `spring-boot-starter-security` retained), AWS SDK for SNS publish
(**new**), React Bootstrap + RTK Query, EventBridge Scheduler + ECS Fargate

**Storage**: PostgreSQL, **entirely unchanged**. No entity deleted, no migration added.

**Testing**: JUnit 5 + Mockito (`mvn verify`, JaCoCo floor, SpotBugs/FindSecBugs, PMD, Error
Prone under `-Werror` on `src/main`); Vitest + Testing Library + MSW; Django `unittest`

**Target Platform**: ARM64 (Graviton). Docker Compose on one EC2 instance for the web tier;
ephemeral Fargate tasks for the jobs. Region `us-east-1`.

**Project Type**: Monorepo, three deployable services behind Nginx, plus scheduled Fargate
one-shots.

**Performance Goals**: Each task completes inside its schedule gap. Measured today:
`hist-load` 6h27m–15h32m (the adjustment phase is 380–424 min of it; the actual price load is
~6 min), `index-load` 11–13 min. `fundamentals-load` has never run, so it is unmeasured.

**Constraints**:
- The SEC rate limiter is **per-process**. Concurrent tasks double the effective request rate
  and earn 403s for both, so no two schedules may overlap.
- SNS email is plain text, 256 KB max, no attachments.
- `src/main` compiles warning-free under `-Werror`; `UnusedMethod`/`UnusedVariable` are
  ERROR-tier, so orphaned injected fields fail the build rather than lingering.
- yfinance data may never be persisted, returned from an API, or rendered in the UI.
- `SECRET_KEY` is shared with Django, which still needs it.

**Scale/Scope**: ~24k tickers in the price universe; validation deliberately scoped to ~1000
(FAT1000 members). 12 routes removed, 0 public endpoints removed, **2** runners + 2 new
schedules added (plus deploying the already-written `fundamentals-load`), ~3 Java files and
~3 React files deleted.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is the **unfilled Spec Kit template** — every principle is a
`[PRINCIPLE_N_NAME]` placeholder. There are no ratified project principles to gate against, so
this check is vacuous as written.

Rather than record a meaningless pass, the gate is evaluated against the repository's actual
always-on rules in `.claude/rules/`, which serve the same function:

| Rule | Applies? | Status |
|---|---|---|
| `data-licensing-commercial-free.md` | **Yes, centrally** | ⚠️ Needs care — see below |
| `auto-update-tests.md` | Yes | Runner tests added, deleted tests removed with their subjects, coverage floor re-checked |
| `auto-update-docs.md` | Yes | `docs/API_REFERENCE.md`, three app READMEs, terraform README, root `CLAUDE.md`, and `.claude/rules/springboot-java.md` (its layout section lists `filing/`) |
| `secrets-check.md` | Yes | Removing secret *references*, adding none; baseline stays empty |
| `infrastructure.md` | **Yes, centrally** | Deploy ordering and non-overlapping schedules are explicit phases |
| `springboot-java.md` | Yes | Runners follow the existing `@ConditionalOnProperty` + `DM_EXIT` pattern; `MarketTime` for all date resolution |

**Data-licensing evaluation (the one real gate)**

The validation job compares stored `adjustedClose` against a yfinance-backed Django endpoint
and emails the result. The rule permits yfinance for "verification/diagnostics in development"
and forbids storing it, returning it from APIs, and rendering it in the UI.

**Verdict: compliant, with enforced guardrails.** An operational email to the repository owner
is none of the three prohibited uses; it is the "sanity checks, comparisons, logs" case the
rule explicitly allows. The guardrails are binding requirements, not aspirations:

- the runner performs **no** database writes (asserted in its test)
- the report carries derived statistics only — never a raw yfinance price series
- nothing from the report reaches an API response or the UI
- no job that writes to the database ever sets `AdjustmentOptions.validateWithYfinance`; the
  nightly `hist-load` already hardcodes it `false`, and this feature adds no writing job

**Post-Phase-1 re-check**: passed, and more cleanly than the earlier draft. Dropping the
`adjust-prices` job means the only new job touching yfinance (`validate-prices`) is read-only,
so the licensing boundary is now structural rather than a matter of discipline.

**Note for the user**: the constitution file is a placeholder. If these gates should have real
teeth on future features, run `/speckit-constitution`. Not blocking here.

## Project Structure

### Documentation (this feature)

```text
specs/002-retire-admin-routes/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 — 11 resolved decisions
├── data-model.md        # Phase 1 — no schema change; run-mode config
├── quickstart.md        # Phase 1 — 7 runnable validation scenarios
├── contracts/
│   ├── http-api-changes.md   # What disappears from the HTTP surface
│   └── run-modes.md          # The replacement trigger contract + schedule table
└── tasks.md             # Phase 2 — created by /speckit-tasks, NOT here
```

### Source Code (repository root)

```text
springboot/src/main/java/com/fattorestreet/sec_api/
├── controller/
│   ├── AdminController.java          [DELETE — 596 lines]
│   └── PublicController.java         [UNCHANGED — /filing-summaries stays]
├── config/
│   └── SecurityConfig.java           [EDIT — strip JWT resource server, keep CORS/CSRF/stateless]
├── listing/
│   ├── AssetLoadRunner.java          [NEW]
│   └── SecTickerLoadService.java     [NEW — extracted from AdminController]
├── corporateaction/
│   ├── ValidatePricesRunner.java     [NEW]
│   ├── ValidationReportPublisher.java [NEW — SNS render + publish]
│   └── AdjustedPriceValidationService.java  [EDIT — Javadoc, index scoping]
├── filing/                           [DELETE — package holds only FilingSummaryService]
├── model/FilingSummary.java          [KEEP]
└── repository/FilingSummaryRepository.java  [KEEP — read path]

springboot/src/main/resources/
├── application.properties  [EDIT — add run-mode props; drop app.django-jwt-secret + LLM_SERVER_URL]
└── db/migration/           [UNCHANGED — no new migration]

springboot/src/test/java/.../sec_api/
├── controller/AdminControllerTest.java   [DELETE — 696 lines]
├── controller/PublicControllerTest.java  [UNCHANGED]
├── config/SecurityConfigTest.java        [REWRITE — assert public access + /admin 404]
├── filing/FilingSummaryServiceTest.java  [DELETE]
├── testsupport/TestJwtTokens.java        [DELETE once unreferenced]
├── listing/AssetLoadRunnerTest.java      [NEW]
└── corporateaction/ValidatePricesRunnerTest.java  [NEW]

springboot/pom.xml   [EDIT — drop spring-boot-starter-oauth2-resource-server; KEEP starter-security]

springboot/deploy/terraform/
├── main.tf          [EDIT — 2 task defs, 2 schedules, 2 log groups, SNS topic + task IAM policy,
│                     and remove SECRET_KEY from the existing secrets blocks.
│                     NOTE: fundamentals_load already written here but NEVER APPLIED]
├── variables.tf     [EDIT — schedule/enable/memory vars per new job; reword env_secret_arn desc]
├── terraform.tfvars.example  [EDIT]
└── README.md        [EDIT — ad-hoc runbook, ETF deferral note, secret list]

react-app/src/
├── pages/Admin.tsx                   [DELETE — 487 lines]
├── pages/SECData.tsx                 [UNCHANGED — FilingSummaries stays]
├── components/FilingSummaries.tsx    [KEEP]
├── App.tsx                           [EDIT — drop /react-admin route, keep success-bar]
├── interfaces.ts                     [UNCHANGED — verified: holds no admin-specific types; IFilingSummary stays]
└── functions/api/springbootApi.ts    [EDIT — drop 7 admin mutations + their hook exports; keep
                                       getFilingSummaries. index.ts re-exports no admin hook (verified) —
                                       Admin.tsx imports them straight from springbootApi.ts]

react-app/__tests__/
├── Admin.test.tsx                    [DELETE — 266 lines]
└── mocks/handlers.ts                 [EDIT — drop admin handlers; keep filing-summaries]

deploy/docker-compose.dev.yml  [EDIT — drop SECRET_KEY from springboot svc ONLY (line 55, not 22)]
deploy/run.sh                  [EDIT — reword comment at line 28]

docs/{API_REFERENCE.md,ARCHITECTURE.md}, CLAUDE.md,
{springboot,react-app}/README.md, .claude/rules/springboot-java.md   [EDIT]
```

**Structure decision**: runners live beside the services they drive (`AssetLoadRunner` in
`listing/`, the two price runners in `corporateaction/`), matching the existing convention
where `HistLoadRunner` sits in `marketdata/` and `IndexLoadRunner` in `index/`. No new
top-level package.

## Schedule Allocation

Every run mode gets a cadence (FR-013). Slots must not overlap — the SEC rate limiter is
per-process. Rationale in `research.md` §11.

| Mode | Cadence | Slot (ET) | Measured runtime | Deployed? |
|---|---|---|---|---|
| `hist-load` | daily | 02:00 | **6h27m–15h32m** | Yes |
| `index-load` | daily | 09:30 | 11–13 min | Yes |
| `fundamentals-load` | daily | 13:30 | never run | **No — blocking** |
| `validate-prices` | weekly | Sun 20:00 | unknown | New |
| `asset-load` | monthly | 1st, 22:00 | unknown | New |

Runtimes from CloudWatch, 2026-08-01. **The existing schedules already overlap**: on 2026-07-30
`hist-load` ran to 17:32 from a 02:00 start, straight through `index-load`'s 09:30 slot. Both
new jobs are therefore placed in the evening, clear of every observed tail: `validate-prices`
at Sun 20:00 (it makes no SEC calls, so a long `fundamentals-load` tail cannot contend with it)
and `asset-load` at 22:00. The staggering also keeps the two new jobs apart when the 1st of the
month falls on a Sunday. Live `index-load` runs at
09:30, not the 05:00 in `terraform.tfvars.example` — that file is stale.

## Implementation Phases

Ordering is load-bearing. Phases A–C are purely additive, so the admin routes keep working as a
fallback until Phase E removes them.

### Phase A — Extract, don't delete (additive)

Move `AdminController`'s inline SEC ticker-parsing (the `SecTickerRow` record,
`parseSecMutualFundTickers`, `extractSecMutualFundRows`, `firstText`, `parseCik`, and the
upsert loop) into `SecTickerLoadService`. Rewire the controller to call it. Behavior identical;
existing tests still pass against the unchanged route. This is the prerequisite that lets a
runner reach the logic without depending on a controller.

### Phase B — Add the two runners (additive)

`AssetLoadRunner` and `ValidatePricesRunner`, plus `ValidationReportPublisher`. Each follows
the invariants in `contracts/run-modes.md`. Add the new properties to `application.properties`
and write the two runner tests.

No `AdjustPricesRunner` — `research.md` §2 shows the nightly `hist-load` already recomputes the
full adjusted series for every ticker it touches.

Verify locally per quickstart Scenario 1 — including that the JVM actually terminates.

### Phase C — Deploy fundamentals-load first (BLOCKING), then the new tasks

**C0, before anything else**: apply the already-written `fundamentals_load` task definition and
schedule, and run it once by hand. It exists in `main.tf` but has never been applied — no task
definition, no schedule, no log group, zero refs in `terraform.tfstate`. Until it runs,
`GET /admin/sync-frames` is the only fundamentals path and cannot be deleted (FR-014,
`research.md` §12). This needs no code change. **Time the run** — its duration is unknown and
both new slots are placed relative to it.

**C1**: two task definitions, two schedules, two log groups, the validation-report SNS topic and
subscription, an `sns:Publish` policy on the task role scoped to that topic ARN, and
`*_schedule_enabled` variables for each new job. Extend the failure-alert `input_template` to
name the new log groups.

**Do not apply yet.** Merge to `main` first so CI publishes the image carrying the new runners
to ECR, *then* apply, *then* run each task once by hand (quickstart Scenario 5). Applying before
the image exists is the failure mode that leaves a `server`-mode task billing forever.

### Phase D — Retire the filing-summary generator

Delete `FilingSummaryService` and the now-empty `filing/` package, `FilingSummaryServiceTest`,
and the `LLM_SERVER_URL` property. **Keep** the entity, the repository, `GET /filing-summaries`,
its `permitAll` matcher, `PublicControllerTest`'s filing cases, and the whole React read path.
No migration. Verify with quickstart Scenario 3 that nothing in `src/main` can write a
`FilingSummary`.

Update `.claude/rules/springboot-java.md`, whose package-layout section lists `filing/`.

### Phase E — Delete the admin surface and the auth wiring

`AdminController` in full. Strip `SecurityConfig` to CORS + CSRF-disable + stateless, removing
`jwtDecoder`, `jwtAuthenticationConverter`, `authoritiesFromUserIdClaim`,
`ADMIN_DJANGO_USER_ID`, the `/admin/**` matcher, the `.oauth2ResourceServer(...)` call, and
`app.django-jwt-secret`. Drop `spring-boot-starter-oauth2-resource-server` from `pom.xml` and
**keep** `spring-boot-starter-security`. Delete `AdminControllerTest`, rewrite
`SecurityConfigTest` to assert the new reality, and delete `TestJwtTokens` once unreferenced.

Delete `Admin.tsx`, its route, its seven RTK Query mutations, its MSW handlers, and
`Admin.test.tsx` — keeping `/react-admin/success-bar` and `AdminSuccessBar` working.

Watch Error Prone here: any constructor-injected field orphaned by these deletions is an
ERROR-tier `UnusedVariable` on `src/main`, so the build tells you what you missed.

### Phase F — Remove the SECRET_KEY injection (after Phase E deploys)

Only once the image no longer reads it: drop `SECRET_KEY` from the three `secrets` blocks in
`main.tf` (lines 200, 369, 478) and from the **springboot** service in
`deploy/docker-compose.dev.yml` (line 55). Reword the descriptions in `variables.tf:68`,
`terraform.tfvars.example:16`, `terraform/README.md:66,75`, and `deploy/run.sh:28`.

**Do not touch** `docker-compose.dev.yml:22` (the `x-django-env` anchor) or the key itself in
the `fattorestreet/env` Secrets Manager blob — Django still requires both. Reversing this
phase's order relative to Phase E leaves a window where the running image expects a secret the
task no longer supplies.

### Phase G — Docs and verification

Update `docs/API_REFERENCE.md`, `docs/ARCHITECTURE.md`, `springboot/README.md` (which documents
a `minConfidence` param on `/admin/adjust-prices` that the code never had, and must now state
the ETF deferral and the frozen-summaries behavior), `react-app/README.md`,
`springboot/deploy/terraform/README.md`, and root `CLAUDE.md`. Then run the full CI gate
(quickstart Scenario 4) and confirm the JaCoCo floor still holds.

## Complexity Tracking

No constitutional violations to justify — the constitution is unfilled. Two deliberate
complexity choices are recorded instead:

| Choice | Why | Alternative rejected |
|---|---|---|
| **No** `adjust-prices` job at all | `hist-load`'s adjustment phase calls `applyAdjustments` unconditionally for ~5,600 tickers nightly, recomputing each full series from all stored actions — a separate sweep would duplicate it, and `force=true` (~24k tickers × fresh SEC fetch) is not runnable at any cadence | A monthly forced sweep, proposed in an earlier draft and dropped once the service was read and the live job measured |
| A **dedicated** SNS topic for reports rather than reusing the failure topic | The failure topic's policy grants publish only to `events.amazonaws.com` scoped to the failure rule's ARN; reusing it means widening that policy and mixing routine reports into a channel where every message currently means something broke | Reuse — cheaper in Terraform lines, worse in signal quality and least-privilege |

## Known Reductions in Capability

Recorded deliberately so they are decisions rather than surprises:

- **ETF/fund corporate actions and adjusted prices** are no longer maintained by anything.
  Funds freeze at their current adjusted values. Restoring coverage later means flipping
  `hist_load_equity_only` to `false` and accepting the runtime, or building a separate ETF job.
- **On-demand forced SEC re-detection for a chosen ticker** (`?force=true&ticker=X`) has no
  replacement. For any actively traded ticker the nightly run reaches it anyway; this was a
  debugging affordance.
- **New 10-K summaries** are never generated. Existing ones render forever; a ticker whose
  filing post-dates the last generation run returns an empty list.
- **Single-ticker adjusted-price validation** has no task equivalent; the runner is
  index-scoped. It was an interactive debugging affordance.
- **Single-index rebuild** (`?code=FAT50`) has no task equivalent; `index-load` rebuilds all
  three cap-ranked indexes together, which the nightly schedule already does.
- **`/admin/test`** is dropped outright — a hardcoded AAPL smoke test with no caller.
- **Synchronous feedback** is gone across the board. Every job reports through CloudWatch logs,
  an exit code, and the SNS failure alert instead of an HTTP response body.

## Open Questions for Implementation

1. **How long does `fundamentals-load` take?** It has never run. Both new slots are placed
   relative to it, so measure on first deploy (Phase C0) and adjust if needed.
2. **Is `hist-load`'s 6–15 hour runtime acceptable?** Out of scope here, but it is the dominant
   cost in the system — the adjustment phase is 380–424 min against ~6 min for the price load
   itself, and its variance is what makes it collide with `index-load`. Worth its own feature.

## Follow-ups (explicitly out of scope)

- Revisit ETF corporate actions and adjusted prices.
- Revisit 10-K summarization; the schema is retained precisely so this stays cheap.
- Fill in `.specify/memory/constitution.md` via `/speckit-constitution` so future Constitution
  Check gates have real teeth.
- The two `django/blog/learning-topics/` posts describing the summarization pipeline (151, 166)
  stay as-is — historical journal entries, not current documentation.

## Next Step

Run `/speckit-tasks` to generate the dependency-ordered `tasks.md` from these artifacts.

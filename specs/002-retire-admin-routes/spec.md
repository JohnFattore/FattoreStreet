# Feature Specification: Retire Spring Boot Admin Routes

**Feature Branch**: `002-retire-admin-routes`

**Created**: 2026-08-01

**Status**: Draft (revised 2026-08-01 — ETF scope, secret cleanup, filing-summary scope)

**Input**: User description: "lets work to remove all these admin routes and have all of the functionality replaced by terraform one off jobs. some of the new jobs are already in place. /admin/validate-adjusted-prices can run and email johnefattore@gmail.com a report. lets remove the summarize filings functionality all together, not needed"

**Revisions**: (1) ETF corporate actions and adjusted prices are explicitly out of scope —
equities only. (2) Spring Boot's `SECRET_KEY` dependency is removed alongside the auth logic.
(3) Filing summaries are *frozen*, not deleted: the generation logic goes, the stored data and
its read path stay.

## Overview

The Spring Boot service exposes twelve `/admin/**` HTTP routes that kick off long-running
data jobs synchronously inside the web process. Three of those jobs already have proper
one-shot Fargate equivalents (`hist-load`, `index-load`, `fundamentals-load`), which makes
the HTTP routes a duplicate trigger path that ties job execution to the lifetime of a
browser request.

This feature removes every `/admin/**` route. Each capability worth keeping moves to a
Terraform-managed one-off Fargate task selected by `APP_RUN_MODE`. Because no authenticated
route survives, the JWT resource-server configuration and Spring Boot's `SECRET_KEY`
dependency are removed with it. The 10-K filing summarization *generator* is deleted; the
summaries already in the database keep serving. The React admin page, which exists only to
call these routes, is deleted.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Operator runs a data job without the web service (Priority: P1)

The site owner needs to refresh the SEC ticker universe, re-run a price load, or rebuild the
cap-ranked indexes. Instead of logging into the React admin page and holding a browser tab
open for a multi-hour request, they run a one-shot ECS task (or let its schedule fire) and
watch CloudWatch logs. The task's exit code reports success or failure, and the existing SNS
alert emails them when it fails.

**Why this priority**: This is the whole point. Every remaining story depends on the jobs
existing as tasks before the routes can be deleted.

**Independent Test**: With the admin routes still in place, run each replacement task via
`aws ecs run-task` and confirm it performs the same work and exits 0. If every capability is
reachable as a task, this story is delivered even before a single route is deleted.

### User Story 2 - Owner receives a weekly adjusted-price accuracy report (Priority: P2)

Once a week the owner gets an email summarizing how closely the stored `adjustedClose` series
tracks the yfinance reference for the FAT1000 universe: how many tickers were checked, how
many fell outside tolerance, the worst offenders, and the localized break dates that point at
missing or misdated corporate actions.

**Why this priority**: This is the one admin route whose value was interactive inspection.
Turning it into a push report preserves the signal without keeping the route.

**Independent Test**: Run the validation task by hand against a small ticker set and confirm
an email arrives with a readable summary. Verifiable on its own, with no other job changed.

### User Story 3 - Fundamentals keep syncing after the route is deleted (Priority: P1)

Quarterly financials continue to refresh from SEC XBRL frames on a nightly schedule. Nobody
has to call an HTTP route to make it happen.

**Why this priority**: **Blocking.** `fundamentals-load` is defined in Terraform but has never
been applied — it has no task definition, no schedule, and no log group in the live account
(verified 2026-08-01). `GET /admin/sync-frames` is currently the *only* working path for
fundamentals sync. Deleting it before the task is deployed leaves zero coverage.

**Independent Test**: Apply the `fundamentals-load` task definition and schedule, run it once
by hand, and confirm quarters are persisted — all while the admin route still exists.

### User Story 4 - Reader still sees existing filing summaries (Priority: P3)

A visitor opens the SEC Data page for a ticker that already has a stored 10-K summary. The
summary renders exactly as before. No new summaries are generated from this point on.

**Why this priority**: The generation pipeline is being retired, but the data it already
produced still has value and the read path is cheap to keep.

**Independent Test**: Load the SEC Data page for a ticker with existing summaries and confirm
they render. Confirm no code path can write a new `FilingSummary` row.

### Edge Cases

- **Full-history frames backfill.** `GET /admin/sync-frames` walks 2009→present; the nightly
  fundamentals task covers a one-year window. The backfill path must survive as a documented
  run-task invocation.
- **Forced re-adjustment.** `?force=true` and `?ticker=X` on the adjust route are the recovery
  tools when a bad corporate action is detected. They need an env-var equivalent.
- **Validation report exceeds the delivery limit.** A run covering ~1000 tickers can produce
  more findings than a single plain-text email can carry.
- **Reference data unavailable.** The validation job depends on a Django endpoint backed by
  yfinance. When it is down or rate-limited, the job must degrade to a partial report rather
  than fail the whole run.
- **Filing summaries go stale.** With the generator removed, no new 10-K is ever summarized.
  The read endpoint must behave correctly for tickers that have no rows — an empty list, not
  an error.
- **`force=true` has no viable replacement, and needs none.** Measured nightly runs process
  ~5,600 tickers in ~6–7 hours with only 133 SEC detections. Forcing would expand that to
  ~24k tickers each with a fresh SEC fetch. It is not runnable at any cadence — and the
  nightly pass already delivers what force was used for. Dropped, not replaced.
- **hist-load already overruns its slot.** Measured wall-clock: 6h27m, 7h10m, and 15h32m on
  three consecutive nights from a 02:00 ET start. The 15h32m run was still going when
  `index-load` fired at 09:30. Any new schedule must be placed against that reality, not
  against the nominal cron.
- **Shared secret.** `SECRET_KEY` in AWS Secrets Manager is shared with Django, which still
  requires it. Only Spring Boot's consumption of it is removed; the secret itself stays.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST NOT expose any HTTP route under `/admin/` on the Spring Boot
  service after this feature ships.
- **FR-002**: Every retained admin capability MUST be invocable as a one-shot Fargate task
  selected by `APP_RUN_MODE`, with the task's exit code reporting success or failure.
- **FR-003**: The SEC asset load (ticker universe + ETF identity enrichment) MUST run as a
  scheduled task on a monthly cadence.
- **FR-004**: Corporate-action price adjustment for equities MUST continue to run nightly
  inside the existing `hist-load` task. **No new adjustment job is added** — the nightly pass
  already recomputes the full adjusted series for every ticker it touches (see
  `research.md` §2). ETF/fund corporate actions and adjusted prices are **out of scope**.
- **FR-013**: Every replacement job MUST run on a recurring EventBridge schedule. No retained
  capability may be manual-invocation-only; `aws ecs run-task` is for ad-hoc overrides of a
  job that already has a cadence, never the sole trigger.
- **FR-014**: The `fundamentals-load` task definition and schedule MUST be deployed and
  verified in AWS **before** `GET /admin/sync-frames` is deleted. It is currently defined in
  Terraform but absent from the live account.
- **FR-005**: The adjusted-price validation job MUST run weekly, scoped to the members of a
  configurable market index (defaulting to FAT1000), and MUST email a summary report to
  `johnefattore@gmail.com`.
- **FR-006**: The validation report MUST fit within its delivery channel's size limit,
  truncating detail rather than failing to send.
- **FR-007**: The validation job MUST NOT persist any yfinance-sourced value to the database
  and MUST NOT expose one through any API response or UI surface.
- **FR-008**: 10-K filing summary **generation** MUST be removed: the summarization service and
  its admin trigger. The `FilingSummary` entity, the `filing_summaries` table and its existing
  rows, the public `GET /filing-summaries` endpoint, and the UI component that renders it MUST
  all be retained and keep working.
- **FR-009**: The React admin page and its associated API client bindings MUST be removed.
  The corporate-action success bar at `/react-admin/success-bar` MUST continue to work.
- **FR-010**: Ad-hoc invocations that the deleted routes supported (single ticker, forced
  re-adjustment, full-history frames backfill) MUST remain possible via documented run-task
  environment overrides.
- **FR-011**: Documentation MUST be updated so no doc describes a route that no longer exists.
- **FR-012**: Spring Boot MUST NOT require `SECRET_KEY` to start or run. Its JWT verification
  configuration, the `app.django-jwt-secret` property, and the `SECRET_KEY` injection in the
  Fargate task definitions and the springboot service's compose environment MUST all be
  removed. Django's use of `SECRET_KEY` is unaffected.

### Key Entities

- **Run mode**: the `APP_RUN_MODE` value that selects which `ApplicationRunner` executes.
  Existing in code: `server`, `hist-load`, `index-load`, `fundamentals-load` (the last is
  **not deployed**). This feature adds `asset-load` and `validate-prices`.
- **FilingSummary**: retained unchanged as a read-only, frozen dataset. Its producer is
  removed; its entity, table, repository, endpoint, and UI component all stay.
- **Validation report**: an ephemeral, in-memory diagnostic comparing stored adjusted closes
  to a yfinance reference. Never persisted.

## Success Criteria *(mandatory)*

- **SC-001**: No route mapping under `/admin` remains anywhere in `springboot/src/main/java`.
- **SC-002**: Every one of the twelve deleted routes maps to either a named replacement
  task or an explicit, recorded decision to drop the capability.
- **SC-003**: A weekly report email arrives with a per-run summary of adjusted-price accuracy.
- **SC-004**: The SEC Data page still renders stored filing summaries, and no code path in
  `src/main` can write a `FilingSummary` row.
- **SC-005**: The full CI gate passes: React lint/format/build/tests, Django tests, and
  `mvn verify` including the JaCoCo coverage floor.
- **SC-006**: A healthy cluster still shows zero running tasks between scheduled runs.
- **SC-007**: Spring Boot starts and serves every public route with `SECRET_KEY` unset.
- **SC-008**: Every run mode except `server` has an EventBridge schedule, and no two schedules
  overlap.

## Assumptions

- The `notification_email` already wired to the task-failure SNS topic is the same address as
  the report recipient, so a single confirmed subscription can serve both.
- ETF adjusted-price coverage is knowingly deferred. Funds keep whatever adjusted values they
  already have, and no scheduled job will refresh them until this is revisited.
- Existing filing summaries are worth keeping on screen even though they will never be
  refreshed. Revisiting generation later is expected, so the schema stays put.
- The `llm/` local inference stack stays in the repo; only Spring Boot's dependency on it goes
  away.

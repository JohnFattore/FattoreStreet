# Tasks: Retire Spring Boot Admin Routes

**Input**: Design documents from `/specs/002-retire-admin-routes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md (all present)

**Tests**: Included. The plan and the repo's `auto-update-tests` rule require runner tests
(`AssetLoadRunnerTest`, `ValidatePricesRunnerTest`), a `SecurityConfigTest` rewrite, and a
JaCoCo floor re-check after the deletions.

**Organization**: Grouped by user story, but this is a migration with load-bearing ordering
(plan.md "Implementation Phases"): additive work first, deploy and verify replacements, only
then delete. The feature ships as **two merges to `main`**: PR1 (additive: extraction,
runners, terraform) and PR2 (deletions and docs). Cross-story dependencies that this forces
are listed under Dependencies.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 (run jobs without the web service), US2 (weekly accuracy report),
  US3 (fundamentals keep syncing), US4 (filing summaries still render)

## Phase 1: Setup

**Purpose**: Get the work onto its own branch with the spec artifacts committed.

- [X] T001 Create branch `002-retire-admin-routes` from `main`; bring over the untracked
      `specs/002-retire-admin-routes/` directory and the modified `.specify/feature.json`
      (currently sitting on `split-llm-notebook-from-blog`), and commit them so the plan files
      ride the PR.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The logic extraction that lets a runner reach the SEC ticker-load code without
depending on a controller (plan Phase A). Purely behavior-preserving.

**⚠️ CRITICAL**: T002–T003 block US1's runner work. US3 (infra-only) does not depend on them.

- [X] T002 Create `springboot/src/main/java/com/fattorestreet/sec_api/listing/SecTickerLoadService.java`
      by extracting from `AdminController`: the upsert loop in `assetLoad` (method body,
      lines 106–182) plus the `SecTickerRow` record and the `parseCik`,
      `parseSecMutualFundTickers`, `extractSecMutualFundRows`, `firstText` helpers (from
      line 486). Constructor-inject the same collaborators the moved code already uses.
      Behavior identical, no new functionality.
- [X] T003 Rewire `AdminController.assetLoad` to delegate to `SecTickerLoadService`, delete the
      moved private members from the controller, and run `cd springboot && mvn verify`:
      `AdminControllerTest` must pass unchanged against the still-existing route.

**Checkpoint**: Route behavior unchanged; the parsing logic is now service-hosted.

---

## Phase 3: User Story 3 - Fundamentals keep syncing (Priority: P1, BLOCKING) 🎯

**Goal**: Deploy the already-written `fundamentals-load` Fargate task so
`GET /admin/sync-frames` stops being the only fundamentals path (FR-014, research §12).
Infra-only: no code change, can start immediately.

**Independent Test**: quickstart Scenario 5a. Task runs by hand, exits 0, persists quarters,
all while the admin route still exists as fallback.

- [ ] T004 ⏸ [US3] From `springboot/deploy/terraform/`, run `terraform plan` and confirm the
      `fundamentals_load` log group, task definition, and schedule show as CREATE. If the plan
      shows unrelated drift, stop and reconcile before applying (local state plus gitignored
      tfvars means the repo cannot tell you what is deployed).
- [ ] T005 ⏸ [US3] `terraform apply`; confirm with `aws scheduler list-schedules` and
      `aws logs describe-log-groups --log-group-name-prefix /ecs/` that the
      `fattorestreet-fundamentals-load` schedule (13:30 ET daily) and log group now exist.
- [ ] T006 ⏸ [US3] Run the task once by hand (`aws ecs run-task`, quickstart Scenario 5a), tail
      `/ecs/fattorestreet-fundamentals-load`, and confirm exit code 0 with
      `quartersPersisted=N`, N > 0. **Time the run**: if its tail reaches past 20:00 ET,
      revisit the 20:00/22:00 slots for the two new jobs in plan.md and
      contracts/run-modes.md before T019.

**Checkpoint**: Fundamentals has a working scheduled path; `/admin/sync-frames` is now
deletable once the other replacements are verified.

---

## Phase 4: User Story 1 - Run data jobs without the web service (code) (Priority: P1)

**Goal**: The `asset-load` run mode, per contracts/run-modes.md invariants.

**Independent Test**: quickstart Scenario 1. `APP_RUN_MODE=asset-load` boots, upserts
Assets/Listings, enriches ETF identity, logs one summary line, JVM terminates, exit 0.

- [X] T007 [US1] Create `springboot/src/main/java/com/fattorestreet/sec_api/listing/AssetLoadRunner.java`:
      `@ConditionalOnProperty(name = "app.run-mode", havingValue = "asset-load")`,
      implements `ApplicationRunner`, terminates via
      `System.exit(SpringApplication.exit(context, () -> exitCode))` with
      `@SuppressFBWarnings("DM_EXIT")` and justification, logs one structured summary line,
      uses `MarketTime` for any date resolution. Calls `SecTickerLoadService` then
      `EtfIdentityService.enrichFundListingIdentities(overwriteExisting)`. Zero-ticker guard:
      exit 1 when the SEC fetch throws or zero tickers were loaded (the missing
      `SEC_CONTACT_EMAIL` 403 failure mode).
- [X] T008 [P] [US1] Add `app.asset-load.overwrite-existing=${ASSET_LOAD_OVERWRITE_EXISTING:false}`
      to `springboot/src/main/resources/application.properties`, following the existing
      `app.<mode>.<key>` block style.
- [X] T009 [US1] Create `springboot/src/test/java/com/fattorestreet/sec_api/listing/AssetLoadRunnerTest.java`
      following the `HistLoadRunner`/`IndexLoadRunner` test pattern (Mockito): success path
      exits 0, zero-ticker guard exits 1, overwrite flag passed through.
- [ ] T010 [US1] ⏸ BLOCKED (needs a dev Postgres + SEC network access) Local verification (quickstart Scenario 1): run `asset-load` against a dev DB,
      confirm work done, one summary line, JVM actually terminates, `echo $?` is 0; then run
      with `SEC_CONTACT_EMAIL` unset and confirm exit 1.

---

## Phase 5: User Story 2 - Weekly adjusted-price accuracy report (code) (Priority: P2)

**Goal**: The `validate-prices` run mode: index-scoped validation, bounded plain-text report,
SNS publish. Read-only against the database (FR-007).

**Independent Test**: quickstart Scenario 1 (log-only local run) and Scenario 6 (email
arrives from a manual task run with `VALIDATE_PRICES_MAX_TICKERS=5`).

- [X] T011 [P] [US2] Update `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/AdjustedPriceValidationService.java`:
      class Javadoc must describe the new weekly caller instead of implying no job calls it;
      expose whatever batch entry point the runner needs over an index-scoped ticker list
      (scope resolution via the existing
      `IndexMemberRepository.findByMarketIndex_CodeOrderByPercentDesc(code)`).
- [X] T012 [P] [US2] Create `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/ValidationReportPublisher.java`:
      render the ValidationReport fields from data-model.md (indexCode, tickersChecked,
      tickersSkipped, tickersOutOfTolerance, worst tickers capped at 10, breaks capped at 20,
      minDate, duration) as plain text under SNS's 256 KB limit, truncating with an explicit
      "N more omitted" line, never silently; publish via the AWS SDK SNS client. Add the SNS
      SDK dependency to `springboot/pom.xml`.
- [X] T013 [US2] Create `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/ValidatePricesRunner.java`
      (same runner invariants as T007): resolve members of
      `app.validate-prices.index-code`; per-ticker reference-fetch failure increments
      `tickersSkipped` and continues; exit 1 only on zero-member scope or failed SNS publish;
      empty `sns-topic-arn` means log-only. **No database writes anywhere in the runner.**
- [X] T014 [P] [US2] Add the four `app.validate-prices.*` properties to
      `springboot/src/main/resources/application.properties`: `index-code` (`FAT1000`),
      `min-date` (`2016-01-01`), `sns-topic-arn` (empty), `max-tickers` (`0`), each with its
      env override per data-model.md.
- [X] T015 [US2] Create `springboot/src/test/java/com/fattorestreet/sec_api/corporateaction/ValidatePricesRunnerTest.java`:
      report rendered and published on success (exit 0), zero-member index exits 1, publish
      failure exits 1, and an explicit assertion that no repository save/delete is ever
      invoked (licensing invariant, FR-007).
- [ ] T016 [US2] ⏸ BLOCKED (needs a dev Postgres + a running local Django) Local verification (quickstart Scenario 1): log-only run with
      `VALIDATE_PRICES_INDEX_CODE=FAT50` and `VALIDATE_PRICES_MAX_TICKERS=5` against local
      Django exits 0; a no-member index code exits 1.

**Checkpoint**: Both new runners work locally. The admin routes are still fully functional.

---

## Phase 6: Deploy and verify the replacement tasks (US1 + US2 infra)

**Goal**: PR1 merges the additive code, CI publishes the image to ECR, then Terraform applies
the new jobs and each is verified by hand. Order is load-bearing: applying before the image
exists creates a `server`-mode task that bills forever (research §9).

- [X] T017 [P] [US1] In `springboot/deploy/terraform/main.tf` + `variables.tf` +
      `terraform.tfvars.example`: `asset-load` log group, task definition (no `SECRET_KEY`
      entry), and monthly schedule (1st, 22:00 ET), with `asset_load_*` variables (schedule
      expression, `asset_load_schedule_enabled`, memory) mirroring the `index_load_*` naming;
      extend the failure-alert `input_template` (main.tf:596–610) to name both new log groups.
- [X] T018 [P] [US2] In the same Terraform module: `validate-prices` log group, task definition
      (env `DJANGO_PORTFOLIO_BASE_URL=https://fattorestreet.com/django/portfolio`, no
      `SECRET_KEY`), weekly schedule (Sun 20:00 ET), `validate_prices_*` variables; dedicated
      `aws_sns_topic` + `aws_sns_topic_subscription` for validation reports (reusing
      `var.notification_email`), and an `aws_iam_role_policy` on the **task** role granting
      `sns:Publish` scoped to that topic ARN; wire the topic ARN into the task definition env.
- [ ] T019 ⏸ [US1] Open PR1 from `002-retire-admin-routes` (Phases 2–5 code plus T017/T018
      Terraform; admin routes untouched), get CI green, merge to `main`, and wait for
      `docker-build.yml` to publish the image to ECR.
- [ ] T020 ⏸ [US1] `terraform plan && terraform apply` from `springboot/deploy/terraform/`;
      confirm both new task definitions, schedules, and log groups exist.
- [ ] T021 ⏸ [US1] Run `asset-load` once by hand (quickstart Scenario 5b): `STOPPED` with
      `exitCode: 0`, summary line in CloudWatch, then
      `aws ecs list-tasks --cluster fattorestreet-hist-load --desired-status RUNNING` returns
      empty (no task degraded to `server` mode).
- [ ] T022 ⏸ [US2] Run `validate-prices` once with the `VALIDATE_PRICES_MAX_TICKERS=5` override
      (quickstart Scenario 6): confirm the SNS subscription for the new topic is **confirmed**
      (first run sends a confirmation email that must be clicked), the report email arrives at
      `johnefattore@gmail.com`, and nothing was written
      (`SELECT max(created_at) FROM corporate_actions` unchanged).
- [ ] T023 ⏸ [US1] Confirm all five schedules exist and match contracts/run-modes.md slots
      (SC-008): `aws scheduler list-schedules` shows hist-load 02:00 daily, index-load 09:30
      daily, fundamentals-load 13:30 daily, validate-prices Sun 20:00 weekly, asset-load
      1st 22:00 monthly.

**Checkpoint**: US1's independent test is delivered: every retained capability runs as a task.
The routes are now safe to delete.

---

## Phase 7: User Story 1 - Delete the admin surface and auth wiring

**Goal**: Plan Phase E. Depends on T006, T021, T022 (every replacement verified in AWS).
Sequence the controller deletion first so the tree compiles at every task boundary (the
controller is the only caller of `FilingSummaryService`, deleted in Phase 8).

- [X] T024 [US1] Delete
      `springboot/src/main/java/com/fattorestreet/sec_api/controller/AdminController.java` and
      `springboot/src/test/java/com/fattorestreet/sec_api/controller/AdminControllerTest.java`.
- [X] T025 [US1] Strip `springboot/src/main/java/com/fattorestreet/sec_api/config/SecurityConfig.java`
      to CORS + CSRF-disable + stateless session: remove `jwtDecoder`,
      `jwtAuthenticationConverter`, `authoritiesFromUserIdClaim`, `ADMIN_DJANGO_USER_ID`, the
      `/admin/**` matcher, and the `.oauth2ResourceServer(...)` call. Keep every `permitAll`
      matcher including `"/filing-summaries"`. Remove the `app.django-jwt-secret=${SECRET_KEY:}`
      line and its comment from `springboot/src/main/resources/application.properties` (lines
      34–35).
- [X] T026 [P] [US1] Remove `spring-boot-starter-oauth2-resource-server` from
      `springboot/pom.xml` (keep `spring-boot-starter-security`; the retained filter chain
      needs it).
- [X] T027 [US1] Rewrite `springboot/src/test/java/com/fattorestreet/sec_api/config/SecurityConfigTest.java`
      to assert the new reality: public routes reachable with no token, `/admin/asset-load`
      returns 404 both without and with a Bearer token, startup succeeds with `SECRET_KEY`
      unset. Then delete `springboot/src/test/java/com/fattorestreet/sec_api/testsupport/TestJwtTokens.java`
      (unreferenced once `AdminControllerTest` and the old `SecurityConfigTest` are gone).
- [X] T028 [P] [US1] React removal: delete `react-app/src/pages/Admin.tsx` and
      `react-app/__tests__/Admin.test.tsx`; in `react-app/src/App.tsx` drop the `Admin` import
      and the `/react-admin` route while keeping `/react-admin/success-bar` and
      `AdminSuccessBar`; in `react-app/src/functions/api/springbootApi.ts` delete the seven
      admin mutations (including `adminAssetLoad`'s legacy `admin/load` 404 fallback) and
      their hook exports, keeping `getFilingSummaries`; in
      `react-app/__tests__/mocks/handlers.ts` delete the admin handlers (including the
      `admin/load` one), keeping filing-summaries. `interfaces.ts` needs no change (verified:
      no admin-specific types).
- [X] T029 [P] [US1] Dev-config cleanup riding the same PR: remove the `SECRET_KEY` line from
      the **springboot** service in `deploy/docker-compose.dev.yml` (line 55 only; line 22 is
      the `x-django-env` anchor Django still needs), and reword the comment at
      `deploy/run.sh:28` so it stops calling the key Django-and-springboot-shared.
- [X] T030 [US1] Run `cd springboot && mvn verify` and `cd react-app && npm run build && npx vitest --run`.
      Error Prone flags any constructor-injected field orphaned by the deletions as ERROR-tier
      `UnusedVariable` on `src/main`; fix everything it finds.

**Checkpoint**: No `/admin` mapping remains in `src/main` (SC-001); builds green locally.

---

## Phase 8: User Story 4 - Filing summaries still render, generator retired (Priority: P3)

**Goal**: Plan Phase D. Delete only the producer; entity, table, rows, endpoint, and UI stay.
Runs after T024 (the controller was the service's only caller).

**Independent Test**: quickstart Scenario 3. SEC Data page renders stored summaries; no code
path in `src/main` can write a `FilingSummary` row.

- [X] T031 [US4] Delete `springboot/src/main/java/com/fattorestreet/sec_api/filing/FilingSummaryService.java`
      (which empties and removes the `filing/` package) and
      `springboot/src/test/java/com/fattorestreet/sec_api/filing/FilingSummaryServiceTest.java`;
      remove the `llm.server.url=${LLM_SERVER_URL:...}` line from
      `springboot/src/main/resources/application.properties` (line 55). Keep
      `model/FilingSummary.java`, `repository/FilingSummaryRepository.java`,
      `PublicController.filingSummaries`, and `PublicControllerTest`'s filing cases untouched.
      No Flyway migration.
- [X] T032 [P] [US4] Update `.claude/rules/springboot-java.md`: remove `filing/` from the
      canonical package layout.
- [X] T033 [US4] Verify the freeze (quickstart Scenario 3):
      `grep -rn "filingSummaryRepository\.\(save\|delete\)\|new FilingSummary" springboot/src/main/java`
      returns nothing; `FilingSummaryRepository` is referenced in `src/main` only by
      `PublicController`; `cd react-app && npx vitest --run` passes with the SEC Data page
      tests intact.

**Checkpoint**: SC-004 satisfied in the working tree.

---

## Phase 9: Polish - Documentation (rides PR2)

**Purpose**: FR-011: no doc may describe a route that no longer exists. All parallel, all
must be in PR2.

- [ ] T034 [P] Update `docs/API_REFERENCE.md`: remove the `/admin/**` route documentation;
      note that Spring Boot has no authenticated routes.
- [ ] T035 [P] Update `springboot/README.md`: remove the admin-route usage section (including
      the documented `minConfidence` parameter the code never had), remove `SECRET_KEY` and
      `LLM_SERVER_URL` from the env-var table, document the five run modes and their
      schedules, the frozen filing-summaries behavior (a ticker whose 10-K post-dates the last
      generation run returns an empty list, by design), and the ETF adjusted-price deferral.
- [ ] T036 [P] Update `react-app/README.md`: remove the Admin page from the pages list and the
      admin mutations from the API layer section.
- [ ] T037 [P] Update `docs/ARCHITECTURE.md` and root `CLAUDE.md`: authentication section (no
      Spring Boot JWT verification, `SECRET_KEY` is Django-only), scheduled jobs (five Fargate
      one-shots, not three), and the two React notes that name `Admin.tsx` (the "only
      intentional exception is the raw axios calls in `src/pages/Admin.tsx`" line and the 401
      interceptor description), plus the springboot env-var list (`SECRET_KEY`,
      `LLM_SERVER_URL`).
- [ ] T038 [P] Update `springboot/deploy/terraform/README.md`: the ad-hoc run-task runbook
      table from contracts/run-modes.md (FR-010), the secret-blob description without
      `SECRET_KEY` for Spring Boot (66, 75), the ETF deferral note, and the
      `*_schedule_enabled` park procedure. Also reword `variables.tf:68` and
      `terraform.tfvars.example:16` descriptions, and refresh the stale
      `index_load_schedule_expression` example (live is 09:30, example says 05:00).

---

## Phase 10: Ship, secret cleanup, final verification

**Purpose**: PR2 merges, deploys, and only then does the `SECRET_KEY` injection leave the
task definitions (plan Phase F ordering: the running image must stop reading the key before
the task stops supplying it).

- [ ] T039 Full local CI gate (quickstart Scenario 4): react lint + lint:styles + format:check
      + build + vitest; `uv run python manage.py test` in `django/` (proves the secret cleanup
      did not reach Django); `./mvnw spotless:apply && mvn verify` in `springboot/`. If the
      JaCoCo bundle floor fails after the deletions, add runner/controller tests; never lower
      the floor.
- [ ] T040 Open PR2 (Phases 7–9), get CI green including the detect-secrets scan, merge to
      `main`, wait for `docker-build.yml`, and deploy the web tier per `deploy/` so the
      running springboot image no longer reads `SECRET_KEY`.
- [ ] T041 Verify SC-007 and the HTTP contract (quickstart Scenario 2 against the deployed
      service): public routes 200, `/admin/asset-load` 404 with and without a token,
      `GET /filing-summaries?ticker=AAPL` unchanged.
- [ ] T042 Terraform secret removal (plan Phase F): delete the `SECRET_KEY` entries from the
      three `secrets` blocks in `springboot/deploy/terraform/main.tf` (lines 200, 369, 478),
      `terraform plan && terraform apply`, then confirm the next scheduled `hist-load` and
      `index-load` runs complete normally without it. Do NOT touch the key in the
      `fattorestreet/env` Secrets Manager blob (Django still reads it).
- [ ] T043 SC-002 final sweep: walk the route map in research.md §1 confirming each of the
      twelve routes has its named replacement or recorded drop; `grep -rn "/admin/" docs/ *.md
      springboot/README.md react-app/README.md` finds no stale route references; mark the
      spec's success criteria checked.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (P1)**: none.
- **Foundational (P2)**: after T001. Blocks Phase 4 (US1 code). Does NOT block Phase 3 (US3).
- **US3 (P3)**: independent, infra-only; can run in parallel with Phases 2, 4, 5. MUST
  complete before Phase 7 (FR-014).
- **US1 code (P4)**: after Foundational.
- **US2 code (P5)**: after T001 only; parallel with Phase 4.
- **Deploy (P6)**: T017/T018 anytime after T001; T019 after Phases 2, 4, 5 complete;
  T020 strictly after T019 (image must exist); T021–T023 after T020.
- **US1 deletions (P7)**: after T006, T021, T022, T023 (every replacement verified live).
- **US4 (P8)**: T031 strictly after T024 (controller is the service's only caller).
- **Docs (P9)**: parallel with Phases 7–8, same PR.
- **Ship (P10)**: T039 after Phases 7–9; T040 after T039; T041 after T040; T042 strictly
  after T040's deploy; T043 last.

### Cross-Story Dependencies (unavoidable in a migration)

- Route deletion (US1, Phase 7) requires US2's runner (T022) and US3's deploy (T006):
  FR-001 cannot ship before FR-014 and FR-005 are live.
- US4's deletion (T031) requires US1's controller deletion (T024) so the tree compiles.

### Parallel Opportunities

- T004–T006 (US3, AWS console/CLI work) alongside all of Phases 2, 4, 5 (local code).
- Within Phase 4/5: T008, T011, T012, T014 are [P] file-disjoint edits.
- T017 and T018 (Terraform, different resource blocks) in parallel.
- Phase 7: T026, T028, T029 in parallel with each other after T024/T025.
- Phase 9: all five doc tasks in parallel.

## Parallel Example: after Foundational completes

```bash
# One developer/agent per track:
Track A (US3, AWS): T004 → T005 → T006
Track B (US1 code): T007 → T008/T009 → T010
Track C (US2 code): T011/T012 → T013/T014 → T015 → T016
Track D (Terraform): T017, T018
# All tracks join at T019 (PR1 merge).
```

## Implementation Strategy

**MVP scope**: Phases 1–6 (through T023). That alone delivers US1's independent test (every
capability reachable as a task, routes still up as fallback), US2's report email, and US3's
blocking deploy: real value shipped with zero deletions and easy rollback (park a schedule
via its `*_schedule_enabled` variable). Stop and validate there.

**Increment 2**: Phases 7–10 (the deletions, docs, and secret cleanup) as PR2, only after
every replacement has been observed succeeding in AWS.

Rollback at any point per quickstart Scenario 7: disable a schedule with one
`terraform apply -var='..._schedule_enabled=false'`; before Phase 7 the admin routes still
exist as the fallback trigger path.

## Notes

- Runner invariants (exit codes, `DM_EXIT` suppression, single summary line, `MarketTime`)
  are contractual: contracts/run-modes.md.
- The validation runner must never set `AdjustmentOptions.validateWithYfinance` and never
  write to the database (FR-007); T015 asserts it.
- `SECRET_KEY` boundary: only Spring Boot's consumption goes. The Secrets Manager blob and
  `docker-compose.dev.yml:22` (Django anchor) stay.
- Commit after each task or logical group; PR1 after T018, PR2 after T039.

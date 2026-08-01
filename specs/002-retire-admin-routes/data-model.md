# Phase 1 Data Model: Retire Spring Boot Admin Routes

**Revised 2026-08-01**: no entity is deleted and no migration is required. The persistence
layer is entirely unchanged by this feature.

## Persisted entities — all retained

`Asset`, `Listing`, `Quarter`, `DailyPrice`, `CorporateAction`, `MarketIndex`, `IndexMember`,
`ListingIndexMetrics`, `FilingExtraction`, and `FilingSummary` are all untouched. No Flyway
migration is added.

### FilingSummary — retained, frozen

The generator is removed; the data and its read path stay (see `research.md` §8).

| Aspect | Detail | Status |
|---|---|---|
| Java class | `com.fattorestreet.sec_api.model.FilingSummary` | **Kept** |
| Table | `public.filing_summaries` (`V1__initial_schema.sql:182`) | **Kept, with rows** |
| Audit table | `public.filing_summaries_aud` (Envers, `:198`) | **Kept** |
| Repository | `FilingSummaryRepository` | **Kept** — used by `PublicController` |
| Read path | `GET /filing-summaries` → React `FilingSummaries.tsx` | **Kept** |
| Producer | `FilingSummaryService` (10-K MD&A fetch + LLM summarization) | **Removed** |
| Trigger | `GET /admin/summarize-filings` | **Removed** |

**Resulting state**: append-never. Every existing row keeps serving; no new row is ever
written. `PublicController.filingSummaries` reads via
`findByTickerOrderByFilingDateDesc(ticker)` and depends on nothing that is being deleted, so
the read path needs no change at all.

**Consequence to document**: a ticker whose 10-K post-dates the last generation run returns an
empty summary list. That is correct behavior now, not a bug. `springboot/README.md` must say
so explicitly.

**Envers**: with no writer, the audit table simply stops receiving revisions. Nothing to
change, nothing to clean up.

**Why no migration**: keeping the schema means revisiting generation later costs nothing — no
`DROP` to write, no data to re-derive, no `ddl-auto=validate` startup coordination between an
entity deletion and a migration.

**Do not confuse with `FilingExtraction`** (`V4__filing_extractions.sql`), which caches
corporate-action text extraction per accession and is load-bearing for dividend/split
detection. Unrelated despite the similar name.

## New in-memory types

Neither is persisted. Both live only for the duration of a one-shot task.

### ValidationReport

Rendered from the existing `AdjustedPriceValidationService.summarizeBatch(...)` output into
the plain-text body published to SNS.

| Field | Type | Source |
|---|---|---|
| `indexCode` | String | resolved scope, e.g. `FAT1000` |
| `tickersChecked` | int | count of tickers the run compared |
| `tickersSkipped` | int | no stored prices, or reference fetch failed |
| `tickersOutOfTolerance` | int | deviation above `DEVIATION_THRESHOLD` (0.005) |
| `worstTickers` | List | capped at `MAX_SUMMARY_WORST_TICKERS` (10) |
| `breaks` | List | capped at `MAX_SUMMARY_BREAKS` (20) |
| `minDate` | LocalDate | comparison window start |
| `durationMs` | long | wall-clock run time |

**Size invariant (FR-006)**: the rendered body must stay under SNS's 256 KB message limit.
The two existing caps already bound the two unbounded lists; the renderer must additionally
truncate with an explicit "N more omitted" line rather than silently dropping rows, per the
project's no-silent-caps convention.

**Licensing invariant (FR-007)**: every field above is a derived comparison statistic. No raw
yfinance price series is carried into the report, and nothing here reaches a database, an API
response, or the UI.

## Run-mode configuration

`APP_RUN_MODE` selects exactly one `ApplicationRunner` via
`@ConditionalOnProperty(name = "app.run-mode", havingValue = "...")`. Adding a mode is
additive; `server` remains the default and is unaffected.

Every mode except `server` runs on a recurring EventBridge schedule (FR-013). Slot rationale
and the SEC-contention constraint are in `research.md` §11.

| Mode | Runner | Schedule (ET) | Status |
|---|---|---|---|
| `server` | none (normal web app) | n/a | existing |
| `hist-load` | `HistLoadRunner` | daily 02:00 | existing, deployed |
| `index-load` | `IndexLoadRunner` | daily 09:30 | existing, deployed |
| `fundamentals-load` | `FundamentalsLoadRunner` | daily 13:30 | code exists, **not deployed** — blocking prerequisite |
| `validate-prices` | `ValidatePricesRunner` | weekly, Sun 20:00 | **new** |
| `asset-load` | `AssetLoadRunner` | monthly, 1st 22:00 | **new** |

No `adjust-prices` mode. The nightly `hist-load` adjustment phase already recomputes the full
adjusted series for every ticker it touches (~5,600/night), so a separate sweep would be pure
duplication — and `force=true` is not runnable at any cadence. See `research.md` §2.

### New configuration properties

Follow the existing `app.<mode>.<key>` naming with an env-var override, as
`application.properties` already does for the three current modes.

**asset-load**

| Property | Env | Default | Meaning |
|---|---|---|---|
| `app.asset-load.overwrite-existing` | `ASSET_LOAD_OVERWRITE_EXISTING` | `false` | Overwrite already-resolved ETF identities |

**adjust-prices** — not built, no properties. The nightly `hist-load` adjustment phase already
covers it; `force=true` is not runnable at any cadence. Measured reasoning in `research.md` §2.

**Existing `hist-load` config is unchanged**, including `HIST_LOAD_EQUITY_ONLY=true` (confirmed
set on the deployed task definition). Funds stay excluded — that is the ETF deferral, and no
new property changes it.

There is deliberately **no** `validate-with-yfinance` property. That fourth
`AdjustmentOptions` field triggers dev-only diagnostics and must be hardcoded `false` in any
job that writes to the database.

**validate-prices**

| Property | Env | Default | Meaning |
|---|---|---|---|
| `app.validate-prices.index-code` | `VALIDATE_PRICES_INDEX_CODE` | `FAT1000` | Scope: members of this index |
| `app.validate-prices.min-date` | `VALIDATE_PRICES_MIN_DATE` | `2016-01-01` | Comparison window start |
| `app.validate-prices.sns-topic-arn` | `VALIDATE_PRICES_SNS_TOPIC_ARN` | `""` | Report topic; empty means log-only |
| `app.validate-prices.max-tickers` | `VALIDATE_PRICES_MAX_TICKERS` | `0` | Safety cap; `0` means no cap |

Scope resolution uses `IndexMemberRepository.findByMarketIndex_CodeOrderByPercentDesc(code)`,
which already exists — no new repository method is needed. An empty result must exit non-zero
rather than silently reporting "0 tickers checked, all healthy."

## Removed configuration

| Key | Where | Reason |
|---|---|---|
| `app.django-jwt-secret` (`= ${SECRET_KEY:}`) | `application.properties:35` | Sole consumer was `SecurityConfig.jwtDecoder`; no authenticated route remains |
| `SECRET_KEY` (Spring Boot only) | `main.tf:200,369,478`; `docker-compose.dev.yml:55` | Nothing in Spring Boot reads it once JWT verification is gone |
| `LLM_SERVER_URL` | `application.properties` | Sole `src/main` consumer was `FilingSummaryService` |

### SECRET_KEY removal boundary — read before editing

`SECRET_KEY` is **shared with Django**, which still requires it. Remove only Spring Boot's
consumption:

- **Remove**: the three `secrets` entries in `main.tf`, and line 55 of
  `deploy/docker-compose.dev.yml` (the `springboot` service env).
- **Keep**: the key itself in the `fattorestreet/env` Secrets Manager blob, and
  `deploy/docker-compose.dev.yml:22` (the `x-django-env` anchor). Removing line 22 breaks
  Django.
- **Reword, don't delete**: `deploy/run.sh:28`, `terraform/variables.tf:68`,
  `terraform.tfvars.example:16`, and `terraform/README.md:66,75` — all describe the shared
  blob's contents and should stop implying Spring Boot needs the key.

**Ordering**: remove the code that reads the key first and let that image deploy, then remove
the injection (`research.md` §9). The reverse order leaves a window where the running image
expects a secret the task no longer supplies.

## Exit-code contract (all runners)

Unchanged from the existing three, and the new runners must match it:

- `0` — completed, including partial per-item failures that are idempotent and retry next run
- `1` — the job threw, or a guard determined the run did no useful work

This matters because the EventBridge failure rule at `main.tf:575` pattern-matches on a
non-zero container exit code to fire the SNS alert. A runner that swallows a total failure and
exits `0` is invisible.

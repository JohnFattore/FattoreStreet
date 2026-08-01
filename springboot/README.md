# SecFilingsAPI

A Spring Boot 3.4 microservice providing SEC EDGAR financial data for all publicly traded US companies.

## Features

- Fetches quarterly financial data from SEC EDGAR XBRL filings
- Bulk sync of all XBRL frames (2009 to present), plus a nightly Fargate task that syncs a recent year window (`APP_RUN_MODE=fundamentals-load`)
- Derives missing quarterly values from annual totals
- Calculates Trailing Twelve Months (TTM) metrics and Year-over-Year (YoY) growth
- Calculates financial ratios (net margin, gross margin, ROA, debt-to-assets)
- Ticker/CIK mapping from SEC ticker endpoints (including SEC mutual fund ticker index for ETF/fund coverage)
- Downloads and parses IEX HIST TOPS pcap/pcapng files into daily OHLCV prices (built-in binary parser, no external dependencies)
- Detects stock splits and dividends from SEC EDGAR `EntityCommonStockSharesOutstanding` and `CommonStockDividendsPerShareDeclared`
- Normalizes SEC dividend facts into quarterly payouts (handles cumulative YTD reporting and fiscal-Q4 edge cases)
- Derives dividend ex-dates from SEC 8-K record-date disclosures and settlement-regime logic (T+3 before 2017-09-05, T+2 through 2024-05-27, T+1 after)
- Applies cumulative backward price adjustment factors for split/dividend-adjusted OHLCV (decoupled from IEX load — runs as a separate process)
- Serves stored 10-K MD&A summaries through `GET /filing-summaries`. **The generator is retired** — no new summary is ever written, so a ticker whose 10-K post-dates the last generation run returns an empty list. That is current behavior, not a bug; see "Filing summaries (frozen)" below
- Serves FRED (Federal Reserve Economic Data) observation series via public `POST /fred-data` (per-series optional year-over-year `pc1` units, 24h in-memory cache; powers the Economic Indicators page)
- **Market indexes (Spring-only — Django does not serve these routes):** `MarketIndex` (one row per index: `code` + `display_name`), `Listing` + `ListingIndexMetrics` (one row per listing per **calendar year**; IEX daily prices + SEC companyfacts for shares/float), and `IndexMember` rows (FK to `MarketIndex`). Public `GET /index-members` and `GET /iwb-reference-holdings` (bundled IWB CSV tickers + fund weights for UI benchmark). Metrics refresh and rebuilds have no HTTP surface: the nightly `index-load` task refreshes metrics for the current year and then rebuilds all three indexes together. **Fattore 50** / **Fattore 100** / **Fattore 1000** are Russell-style (float-adjusted cap rank) top-50 / top-100 / top-1000 proxies (`FAT50` / `FAT100` / `FAT1000`), not official FTSE Russell products.

## Java package layout

Application code under `com.fattorestreet.sec_api`: `client` (SEC HTTP / `WebService`), `index`, `corporateaction` and `corporateaction.support`, `economic` (FRED client/cache), `fundamentals`, `listing`, `marketdata`, plus shared `controller`, `repository`, `model`, `config`, and `util`. The `index` package holds index membership listing, SEC facts parsing, and metrics refresh. Tests mirror those package names under `src/test/java`.

## Stack

- Java 25 (LTS), Spring Boot 4.1
- Spring Data JPA + Hibernate (PostgreSQL)
- **Hibernate Envers** — all JPA entities are audited (`@Audited`). Hibernate creates a `revinfo` table and per-entity `*_AUD` tables (for example `assets_AUD`, `daily_prices_AUD`). Expect **large** audit table growth on high-churn data, especially `daily_prices` ingests; plan disk and retention accordingly. The inverse `Asset.listings` collection is `@NotAudited` so listing history is tracked only via `listings_AUD`.
- Spring Security for the filter chain only (CORS, CSRF-disable, stateless sessions). **No route is authenticated** and the service verifies no tokens: the JWT resource server went with the `/admin/**` routes, and with it Spring Boot's dependency on Django's `SECRET_KEY`
- Spring `RestTemplate`-based SEC client (`WebService` in `client` package)
- Bean Validation (`spring-boot-starter-validation`)
- Custom pcap/pcapng + IEX-TP binary parser using `ByteBuffer` for HIST TOPS trade extraction
- Maven build, Docker multi-stage image

### Database migrations

- **[Flyway](https://flywaydb.org/)** (`spring-boot-starter-flyway` + `flyway-database-postgresql`; Boot 4 moved the auto-configuration into this per-feature starter, so bare `flyway-core` no longer activates it) runs migrations from [`src/main/resources/db/migration`](src/main/resources/db/migration) at startup, before Hibernate initializes.
- **`spring.jpa.hibernate.ddl-auto=validate`** — Hibernate checks the schema against entities but does not alter tables. Any entity change needs a **new Flyway migration** (for example `V2__...sql`).
- **Initial install (empty PostgreSQL):** Flyway applies `V1__initial_schema.sql` (base tables + Hibernate Envers `revinfo` and `*_aud` tables).
- **Existing database** that already matches the app but has no `flyway_schema_history` table: `application.properties` sets `spring.flyway.baseline-on-migrate=true`, so Flyway baselines at V1 instead of re-applying duplicate DDL; future changes go in `V2+`. A fresh (empty) database still runs `V1__initial_schema.sql` normally.
- **Tests:** the `test` profile sets `spring.flyway.enabled=false` and uses H2 with `ddl-auto=create-drop`, so Hibernate builds the schema in memory and tests do not require PostgreSQL or Flyway.

## Data Licensing Policy

All externally fetched data used by this service must be permitted for free commercial use.

Before adding or changing any external data source:
- Verify the source Terms of Use or license explicitly allow commercial use.
- Verify redistribution and storage rights for persisted data.
- Document the source URL and license or ToS reference in this README.
- Do not merge sources with unclear or restrictive commercial terms.

## Getting Started

### Prerequisites

- Java 25 (the Maven enforcer fails the build on anything older)
- Maven
- PostgreSQL

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/springboot` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `POSTGRES_PASSWORD` | `postgres` | Database password (same key Django uses; also the postgres image's init var) |
| `SHOW_JPA_SQL` | `false` | When `true`, logs Hibernate-generated SQL. Use locally; leave unset or `false` in production. |
| (property) `fattore50.rebuild.top-n` | `50` | How many names the Fattore 50 rebuild includes. Set in `.env` as `fattore50.rebuild.top-n=50` if needed. |
| (property) `fattore100.rebuild.top-n` | `100` | How many names the Fattore 100 rebuild includes. Set in `.env` as `fattore100.rebuild.top-n=100` if needed. |
| (property) `fattore1000.rebuild.top-n` | `1000` | How many names the Fattore 1000 rebuild includes. Set in `.env` as `fattore1000.rebuild.top-n=1000` if needed. |
| `HIST_LOAD_EQUITY_ONLY` | `false` | When `true`, the post-load price adjustment skips fund tickers. ETF detection fetches hundreds of filings per fund and dominates the runtime; the scheduled Fargate task sets this to `true`. |
| `PRICE_ADJUSTMENT_DETECTION_INDEX` | `FAT1000` | Index code whose members are eligible for automatic SEC corporate-action detection (property `app.price-adjustment.detection-index-code`). The price universe is the whole IEX HIST symbol set (~24k tickers, about two thirds with no SEC counterpart), which takes days to re-scan at the SEC rate limit. Blank scans every ticker with prices; `force=true` bypasses the scope. |
| `INDEX_LOAD_YEAR` | `0` (current year) | Target calendar year for the one-shot index load (`APP_RUN_MODE=index-load`) |
| `INDEX_LOAD_SCOPE` | `russell1000` | Index-load metrics refresh scope: `russell1000` (IWB universe) or `all` |
| `INDEX_LOAD_SKIP_REFRESH` | `false` | When `true`, the index load skips the metrics refresh and only rebuilds |
| `INDEX_LOAD_TICKER` | (empty) | When set, the index load refreshes just this ticker (smoke-test mode) before rebuilding |
| `INDEX_LOAD_MIN_PROCESSED` | `800` | Minimum refreshed listings before the index load rebuilds; below it the task keeps existing members and exits `1` |
| `FUNDAMENTALS_LOAD_YEARS_BACK` | `1` | Calendar years the one-shot fundamentals load (`APP_RUN_MODE=fundamentals-load`) syncs back from the current year. Frames are fetched per (concept, period), so raising this multiplies SEC requests for filings that rarely change |
| `FUNDAMENTALS_LOAD_START_YEAR` | `0` (derive from years-back) | Explicit first year for the fundamentals load, clamped to 2009. Set to `2009` for a one-off full backfill |
| `DJANGO_PORTFOLIO_BASE_URL` | `http://localhost:8000/portfolio` | Base URL for Django portfolio API used by diagnostics-only yfinance validation |
| `APP_RUN_MODE` | `server` | Which one-shot job to run instead of serving the API: `hist-load`, `index-load`, `fundamentals-load`, `asset-load`, `validate-prices`. See "Run modes" below |
| `ASSET_LOAD_OVERWRITE_EXISTING` | `false` | When `true`, the asset load re-resolves ETF identities that are already resolved rather than only filling gaps |
| `VALIDATE_PRICES_INDEX_CODE` | `FAT1000` | Index whose members the weekly adjusted-price validation checks |
| `VALIDATE_PRICES_MIN_DATE` | `2016-01-01` | Start of the validation comparison window |
| `VALIDATE_PRICES_SNS_TOPIC_ARN` | (empty) | SNS topic the validation report is published to; empty logs the report instead |
| `VALIDATE_PRICES_MAX_TICKERS` | `0` (no cap) | Safety cap on tickers validated per run, heaviest index weight first |
| `SEC_HTTP_CONNECT_TIMEOUT_MS` | `15000` | SEC API connect timeout (milliseconds) |
| `SEC_HTTP_READ_TIMEOUT_MS` | `120000` | SEC API read timeout per request (milliseconds) |
| `SEC_HTTP_RETRY_MAX_ATTEMPTS` | `3` | Max attempts for transient SEC failures (429/5xx/timeout) |
| `SEC_HTTP_RETRY_BASE_BACKOFF_MS` | `1000` | Base backoff for SEC retries; multiplied by attempt |
| `SEC_HTTP_MIN_INTERVAL_MS` | `250` | Minimum delay between SEC requests to reduce throttling |
| `FRED_API_KEY` | (empty) | FRED API key for the public `POST /fred-data` economic data endpoint |

### Run Locally

```bash
mvn spring-boot:run
```

`springboot/.env` is auto-imported at startup (`spring.config.import=optional:file:.env[.properties]`) for local runs. Keep values unquoted. No `SECRET_KEY` is needed: nothing in this service reads it.

**CORS (browser → Spring from the Vite dev server or production):** `WebConfig` registers a `CorsConfigurationSource` and `SecurityConfig` enables `http.cors`. Allowed origins include local dev (`http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost`, `http://localhost:80`) and production (`https://fattorestreet.com`, `https://www.fattorestreet.com`). Edit `WebConfig` if you add more hosts.

The service starts on port **8080** by default.

### Run modes

`APP_RUN_MODE` selects exactly one `ApplicationRunner` on the shared image. `server` (the default)
serves the API; every other value performs one job, logs a single summary line, and exits with a
status code the container surfaces to EventBridge. **No route triggers any of these** — the admin
endpoints that used to are gone, and each mode runs on its own EventBridge schedule.

| Mode | Runner | Schedule (ET) | Work |
|------|--------|---------------|------|
| `server` | — | n/a | Serve the read-only API |
| `hist-load` | `HistLoadRunner` | daily 02:00 | IEX HIST price load, then corporate-action detection and price adjustment |
| `index-load` | `IndexLoadRunner` | daily 09:30 | Index metrics refresh, then rebuild `FAT50` / `FAT100` / `FAT1000` |
| `fundamentals-load` | `FundamentalsLoadRunner` | daily 13:30 | SEC XBRL frames sync into `Quarter` rows |
| `validate-prices` | `ValidatePricesRunner` | weekly, Sun 20:00 | Read-only adjusted-price accuracy report, emailed via SNS |
| `asset-load` | `AssetLoadRunner` | monthly, 1st 22:00 | SEC ticker universe into `Asset` / `Listing`, then ETF identity enrichment |

Slots must not overlap: the SEC rate limiter is per-process, so two tasks calling `data.sec.gov` at
once double the effective request rate and earn 403s for both. Exit codes are uniform — `0` on
completion (including per-item failures that are idempotent and retry next run), `1` when the job
threw or a guard proved the run did no useful work. The EventBridge failure rule alerts on any
non-zero exit, so a runner that swallows a total failure and exits `0` would be invisible.

Ad-hoc runs go through `aws ecs run-task` with a container environment override; the runbook is in
`deploy/terraform/README.md`.

### Run with Docker

```bash
docker build -t sec-api .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/springboot \
  -e DB_USERNAME=postgres \
  -e POSTGRES_PASSWORD=postgres \
  sec-api
```

### IEX Price Data

The service downloads IEX HIST TOPS pcap files, parses them in-process using a custom binary parser, and writes OHLCV data directly to the database. It runs as the nightly `hist-load` task, not from an HTTP request:

```bash
# Locally: 02:00 ET equivalent, ~1 year of IEX TOPS data
APP_RUN_MODE=hist-load HIST_LOAD_DAYS=252 mvn spring-boot:run

# A smaller window
APP_RUN_MODE=hist-load HIST_LOAD_DAYS=30 mvn spring-boot:run
```

Dates already in the database are skipped automatically. IEX load saves raw prices only — price adjustments run as a separate process.

### Market index metrics (IEX + SEC)

`ListingIndexMetrics` is built from the latest IEX-derived `DailyPrice` per ticker, SEC companyfacts (`EntityCommonStockSharesOutstanding`, `EntityPublicFloat`), and SEC **submissions** JSON for jurisdiction: `country_incorp` / `country_hq` plus `state_incorp` / `state_hq` (US state codes are split out so country columns are real countries). Run after listings and daily prices exist. **Dual-listed share classes (same CIK):** SEC shares outstanding is consolidated per issuer; for Alphabet **GOOG** / **GOOGL** only, `market_cap` and `free_float_market_cap` are scaled by **½** per listing so the two lines together approximate the issuer total (see `DualClassIndexCapSplit`).

**Ticker scope:** `refresh-stocks` defaults to Russell 1000 / IWB tickers from `src/main/resources/data/IWB_holdings.csv` (equity rows only), packaged as `classpath:/data/IWB_holdings.csv`. To refresh index metrics for *all* listings in the database, pass `scope=all`.

**Upgrading existing databases** (pre–`MarketIndex` FK): if `index_members` still has a legacy `market_index` text column, insert the matching `market_indexes` row if needed, backfill `market_index_id`, then drop the old column after verifying rows.

The refresh and the cap-ranked rebuild (`FAT50`, `FAT100`, `FAT1000`) run together as one job: the rebuild creates or reuses each `MarketIndex` row and replaces its `IndexMember` rows (weights sum to 100%). All three indexes are rebuilt together — there is no single-index selector.

```bash
# The whole sequence, against a local database
APP_RUN_MODE=index-load mvn spring-boot:run

# Refresh all listings in the DB, not just Russell 1000 / IWB tickers
APP_RUN_MODE=index-load INDEX_LOAD_SCOPE=all mvn spring-boot:run

# Smoke test: refresh one ticker, then rebuild
APP_RUN_MODE=index-load INDEX_LOAD_TICKER=AAPL mvn spring-boot:run

# Target a calendar year (defaults to the current year)
APP_RUN_MODE=index-load INDEX_LOAD_YEAR=2025 mvn spring-boot:run
```

**Scheduled run (Fargate):** this runs nightly as an ephemeral Fargate task (`APP_RUN_MODE=index-load` via `IndexLoadRunner`, scheduled after the hist-load task so fresh `DailyPrice` rows exist; see `deploy/terraform/README.md`). The runner skips the rebuild and exits `1` if the refresh processes fewer than `INDEX_LOAD_MIN_PROCESSED` listings — the rebuild deletes members by index code, so rebuilding after a mostly-failed refresh (SEC outage, empty new calendar year) would shrink the live indexes.

List available indexes and fetch constituents:

```bash
curl "http://localhost:8080/indexes"
curl "http://localhost:8080/index-members?code=FAT50"
curl "http://localhost:8080/index-members?code=FAT100"
curl "http://localhost:8080/index-members?code=FAT1000"
```

### Quarterly Fundamentals (XBRL frames sync)

`EdgarService.syncFrames(startYear)` populates `Quarter` rows from the SEC XBRL **frames** API: for
each concept and period it pulls one payload covering every filer, keeps the CIKs matching a
non-fund `Asset`, derives missing quarters from annual totals, and upserts by (asset, year,
quarter).

**Scheduled run (Fargate):** runs nightly as an ephemeral task (`APP_RUN_MODE=fundamentals-load` via
`FundamentalsLoadRunner`; see `deploy/terraform/README.md`), scheduled clear of the hist and index
loads because the SEC rate limiter is per-process and overlapping tasks earn 403s for both.

The nightly run covers only `FUNDAMENTALS_LOAD_YEARS_BACK` (default 1) years back through the
current year, rather than the full 2009→present history. Frames are fetched per (concept,
period), so full history is thousands of multi-megabyte requests almost entirely re-reading settled
filings; two calendar years absorbs amendments and late filers, and the upsert key makes restating a
year overwrite in place. Set `FUNDAMENTALS_LOAD_START_YEAR=2009` for a one-off backfill.

The runner exits `1` only when *every* frame request failed — the signature of a missing
`SEC_CONTACT_EMAIL` (403 "Undeclared Automated Tool" on all of them), which would otherwise persist
nothing and still report success. Partial failures exit `0`, since SEC legitimately 404s
concept/period combinations nobody tagged.

### Price Adjustments (Splits & Dividends)

Corporate action detection and price adjustment runs automatically at the end of the nightly
Fargate hist-load task (`APP_RUN_MODE=hist-load`; disable with env `HIST_LOAD_ADJUST_ENABLED=false`,
property `app.hist-load.adjust-enabled`).

**Detection scope.** Automatic SEC detection is limited to members of one index, `FAT1000` by
default (property `app.price-adjustment.detection-index-code`). The price universe is the IEX HIST
symbol set: roughly 24k distinct tickers, including ETFs, warrants, rights, units, preferred series,
test symbols and every delisted ticker ever ingested, since `daily_prices` is append-only. About
two thirds of those have no SEC counterpart at all, and re-scanning them at the SEC rate limit takes
days rather than hours. Scoping to the index bounds the nightly SEC work to something that fits in
one run. Set the property blank to scan every ticker with prices; if the configured index has no
members the run logs an error and skips automatic detection rather than falling back to the full
universe. `force=true` bypasses the scope entirely and remains the way to re-fetch everything.

Within that scope, and without `force`, SEC detection refreshes on a rolling cadence: each run
re-detects the stalest ~1/7 of in-scope tickers (tracked via `listings.last_sec_detection_at`, so
every in-scope ticker is re-scanned about weekly), and any in-scope ticker with a >25% overnight move
among its most recent closes is re-detected immediately (split signature; the check scans the last
few rows so a multi-day catch-up load cannot bury the break). A failed SEC fetch does **not** stamp
`last_sec_detection_at`, so the ticker retries the next run instead of waiting out the interval.

First-time detection goes through the same rolling cap, not around it. A ticker that has never been
detected sorts first in the staleness queue (null stamps rank oldest), so a fresh index drains a
1/7 slice per night like any other refresh. Do not add a separate "never detected" trigger: it
un-defers exactly the tickers the cap just deferred, so the cap bounds nothing and the run grows to
the entire in-scope backlog, which is what makes the nightly task overrun its window.

Price adjustment itself still covers every ticker with unadjusted rows regardless of scope:
out-of-scope tickers get their adjusted columns filled from raw prices (or from actions already
stored), so they drain out of the backlog instead of being re-examined every night. Price rows are
only written when an adjusted value actually changes, so the nightly run writes just the new rows
unless detection changed an action.

The scheduled task sets `HIST_LOAD_EQUITY_ONLY=true` (property `app.hist-load.equity-only`,
Terraform variable `hist_load_equity_only`), so the nightly adjustment covers equities only. ETFs
have no XBRL dividend facts, so `EtfCorporateActionService` brute-force fetches the filing index and
hundreds of documents per fund against the SEC rate limit; leaving funds in stretched nightly runs
past 17 hours. The property defaults to `false`, so a local `hist-load` run still covers everything.

**ETF corporate actions and adjusted prices are currently deferred.** Nothing maintains them: the
nightly job is equity-only and the `etfOnly` trigger went with the admin routes, so fund adjusted
values freeze where they are. Restoring coverage means flipping `hist_load_equity_only` to `false`
and accepting the runtime, or building a separate ETF job.

To reproduce a single ticker while developing, run the adjustment locally against a dev database or
call `PriceAdjustmentService` directly. There is no HTTP trigger and no per-ticker task equivalent —
the nightly run recomputes the full adjusted series for every ticker it touches anyway.

Dividend ingestion behavior:
- Normalizes dividend facts to per-quarter payouts before persistence.
- Prefers true quarterly facts over cumulative rows for the same period end date (with form-priority tie-breaks) and skips annual-only period-end leakage.
- Derives missing fiscal Q4 payouts from annual 10-K totals when quarter facts are absent, with guardrails to reject implausible derived values.
- Carries **fiscal quarter-end** (XBRL) and **ex-dividend date** separately through detection; split-adjustment applies forward-split factors using ex-dates (Yahoo-aligned), not quarter-end alone.
- **Anchors ex-dates to declaration tuples first**: 8-K / press-release text is mined for (per-share amount, record date, payable date, ex-date) tuples; a tuple whose amount matches the XBRL event (declared basis or split-restated) supplies the record/ex dates directly. Direct ex-date extraction and the DP record-date assignment remain as fallbacks.
- **Promotes freshly declared dividends before the next 10-Q**: a high-confidence declaration tuple that no XBRL event claims, with a recent record date past the newest reported fiscal period, becomes a provisional tuple-anchored dividend event immediately (amount bounded against the latest regular payout). When the XBRL fact lands a quarter later, the normal upsert matches and updates the same row in place, so adjusted prices never lag a declared dividend by a reporting cycle.
- Derives ex-dates from SEC 8-K record-date disclosures using weighted candidate extraction (primary filing + exhibit scan), filing-date gates, and global quarter-sequence assignment; when filings state an explicit **ex-dividend date**, that date is preferred when it plausibly follows the fiscal period end.
- Uses confidence-aware inferred fallback ex-dates (cadence/lag based) when no reliable record date can be extracted, instead of persisting quarter-end placeholders.
- **Persists ex-date provenance** (`ex_date_source` column, V3 migration): `TUPLE_MATCHED` / `DIRECT_EX_TEXT` / `RECORD_DP` / `SYNTHETIC` for dividends, `PRICE_BREAK` / `FILING_TEXT` / `SHARE_FACT` for splits, with matching `confidenceScore`. A weaker-grounded re-detection never moves a date set by a stronger path (protects tuple-anchored dates from transient filing-fetch failures), and declaration-anchored rows (`TUPLE_MATCHED` / `DIRECT_EX_TEXT`) are excluded from orphan pruning so a degraded re-detection cannot delete-and-reinsert around that guard.
- **Degraded-scan circuit breaker**: when a meaningful share of filing fetches fail during the record-date scan, the run suppresses synthetic-dated inserts and orphan pruning entirely; missing declarations are treated as missing evidence, not evidence of absence.
- **Corroborates splits against raw IEX closes**: the split effective date is snapped to the observed overnight price break (price break > filing text > share-fact date); XBRL split candidates with full price coverage and no matching break are rejected as buyback/issuance artifacts; a same-ratio split within ±90 days is re-dated in place instead of duplicated.
- **Detects missed splits from price breaks**: unexplained overnight moves that snap to a split multiplier and persist across the surrounding days are persisted as `SEC_PRICE_CORROBORATED` when a SEC filing split-date candidate is within ±14 days, or on price evidence alone for ≥5x moves. This makes the >25% overnight-jump re-detection catch a fresh split the same night.
- Derives split effective dates from SEC 8-K filing/exhibit text when available (including archived SEC submission bundles), and falls back to SEC shares-jump detection dates with low-confidence logging when unresolved. **Non-standard** forward ratios (e.g. 3:2, 4:3) are persisted when a filing-derived split effective date aligns with the shares jump **or** a corroborating price break exists (guards against buybacks/secondaries). The split filing scan is **lazy**: filing split-date candidates are only fetched when an XBRL share-count ratio jump or an unexplained price break exists, so the typical never-split ticker skips that HTTP block entirely.
- **Persists per-accession extraction results** (`filing_extractions` table, V4 migration): filings are immutable, so each accession's best record-date candidate, ex-date candidates, declaration tuples, and best split-date candidate are stored once (per-section extractor versions invalidate results when extraction logic changes). Re-scans consume persisted extractions at zero HTTP cost and spend the per-run fetch budget (250 dividend / 400 split documents) only on unextracted accessions, newest first, so older filing history backfills over successive runs and steady-state weekly re-detection fetches only the handful of new filings.
- During a single equity ticker load, SEC filing HTTP responses are additionally deduped in-memory (per thread) so overlapping split/dividend scans do not download the same accession documents twice.
- Uses bounded SEC retries with configurable timeouts to prevent indefinite hangs during long reconciliation scans.
- Stores both SEC raw and split-adjusted dividend values per event (`rawDividend`, `adjustedDividend`), while keeping `ratio` as a compatibility alias to adjusted values.
- Allows multiple same-date dividend rows when amounts differ (e.g., regular + special) via uniqueness on (`ticker`, `action_type`, `effective_date`, `ratio`) plus a lookup index on (`ticker`, `action_type`, `effective_date`).
- Converts older dividend amounts to current-share basis using detected forward splits, with canonical split-ratio snapping and 4-decimal rounding. When the issuer already restated pre-split facts to post-split scale, the adjusted amount is kept as reported and `rawDividend` is **un-restated** back to the declared cash scale so the price factor divides a pre-split cash amount by the pre-split raw close.
- Applies dividend price-adjustment factors using **`rawDividend`** (cash per share as of the ex-date), which matches the raw `priorTradingClose` denominator from `DailyPrice.closePrice`. If `rawDividend` is absent (legacy or ETF rows), falls back to `adjustedDividend` or `ratio` when those match the cash scale. **`adjustedDividend`** remains stored for display and API comparison (`GET /dividends`); it is not used for the factor when `rawDividend` is present.
- Uses `force=true` to reconcile existing corporate action rows with newly detected SEC data.
- Supports ETF SEC filing extraction from ETF forms (`497`, `485*`, `N-CSR/N-CSRS`) with confidence gating (`minConfidence`) and optional ETF-only/equity-only batch targeting.
- For ETF runs, scans primary filings plus a capped set of likely exhibit/attachment docs (for better recall on funds like VOO where distributions are often in exhibits).
- Uses scored ETF identity matching (ticker/class ticker/series-class IDs/name signals) instead of strict single-token gating.
- ETF date extraction normalizes filing HTML/text, supports additional date formats (`MM-DD-YYYY`, `MM/DD/YY`, `YYYY/MM/DD`, abbreviated month labels), and can use low-confidence pay-date/filing-date fallbacks when explicit ex/record dates are absent.
- Logs ETF diagnostics at the end of a run:
  - single-fund ticker mode: `etfDiagnostics`
  - batch mode: `etfDiagnosticsSummary`
  - both include skip-reason buckets and capped sample rows for troubleshooting misses (including separate `date_missing` and `below_confidence` reasons).
- Logs equity diagnostics at the end of a run:
  - single-equity ticker mode: `equityDiagnostics`
  - batch mode: `equityDiagnosticsSummary`
  - includes split/date-path counters and dividend parser-path counters (facts parsed, normalized events, record-date path usage, fallback usage, inserts/updates), plus split price-corroboration counters (`priceCorroborated`, `priceSnapped`, `priceRejected`, `priceOnlyDetected`, `priceOnlyUnconfirmed`), ex-date assignment-path counters (`declarationTuples`, `tupleMatchedAssignments`, `directExAssignments`, `dpAssignments`, `syntheticAssignments`, `promotedTupleEvents`), and filing-scan health (`scanFailedFilings`, `scanDegraded` per ticker; `scanFailedFilings` / `scanDegradedTickers` in the batch summary).
- Actions dated on non-trading days (weekends, holidays, inferred ex-dates) are snapped forward to the next trade date instead of being silently dropped; batch output reports `totalSnappedActions`, single-ticker output reports `snappedActions`.
- Per-ticker batch failures leave adjusted columns NULL so the ticker is retried on the next run (no raw-as-adjusted freeze); batch output reports `failedTickers`, plus `scheduledDetections` and `jumpTriggeredDetections` for the rolling re-detection.
- Optional yfinance validation report (`validateWithYfinance=true`) is diagnostics-only:
  - Spring Boot reads yfinance reference data from Django public endpoints (`/portfolio/api/asset-dividends/`, `/portfolio/api/asset-splits/`) configured by `DJANGO_PORTFOLIO_BASE_URL`.
  - ticker mode adds `validationReport`
  - batch mode adds `validationSummary`
  - mismatch taxonomy: `missing_in_sec`, `extra_in_sec`, `date_drift`, `amount_drift`
  - ticker mode also adds `priceValidationReport` and batch mode `priceValidationSummary` (see below)
  - **No yfinance values are written to DB**; SEC remains source of truth.
- Adjusted-price validation runs weekly as the `validate-prices` task (see "Run modes" below), comparing stored `adjustedClose` against the yfinance adjusted-close reference (`/portfolio/api/asset-prices/`), normalized at the latest common date:
  - reports `tickersChecked`, `tickersSkipped`, `tickersOutOfTolerance` (0.5% level threshold)
  - reports ratio breaks: dates where the sec/yf ratio steps day-over-day by more than 1%, each with the nearest stored corporate action within ±7 days; every break localizes one missing, extra, or misdated event
  - index-scoped, read-only, emailed via SNS; never persisted, never called by the nightly job.
- Public comparison endpoints exposed for UI validation views:
  - `GET /dividends?ticker=...` returns persisted internal dividend actions.
  - `GET /splits?ticker=...` returns persisted internal split actions (`ratio` is `old_shares / new_shares`).

### Corporate Action Reconciliation Runbook

Use this sequence for safe subset-to-batch reconciliation when load quality drifts:

This is a **development** workflow, run locally against a dev database — the reconciliation
affordances it uses (forced re-detection, yfinance validation, subset targeting) are dev-only and
have no production trigger.

1. **Subset triage first**: run a small ticker cohort with `force=true` and yfinance validation on.
2. **Inspect diagnostics**: prioritize `missing_in_sec` and `amount_drift` mismatches and high-volume skip reasons.
3. **Tune + retest**: apply parser/date-mapping fixes, rerun the same subset, and confirm mismatch reduction.
4. **Promote to batch**: run the full batch with `force=true` plus yfinance validation.
5. **Record outcomes**: capture `equityDiagnosticsSummary`, `etfDiagnosticsSummary`, and `validationSummary` snapshots for regression tracking.

Nothing in a production run ever sets `validateWithYfinance` — the nightly job hardcodes it `false`,
and the weekly `validate-prices` task writes nothing at all.

### Asset load (SEC ticker universe + ETF identity)

Loads every US ticker from the SEC ticker endpoints (`company_tickers.json` and
`company_tickers_mf.json`) into `Asset` / `Listing`, then enriches ETF listings with SEC
series/class identity. Runs monthly as the `asset-load` task:

```bash
# Load equities/funds from SEC and enrich ETF series/class identity fields
APP_RUN_MODE=asset-load mvn spring-boot:run

# Overwrite previously resolved ETF identity rows during load
APP_RUN_MODE=asset-load ASSET_LOAD_OVERWRITE_EXISTING=true mvn spring-boot:run
```

The runner exits `1` if it persisted zero tickers — the signature of a missing `SEC_CONTACT_EMAIL`,
which earns a 403 "Undeclared Automated Tool" on every request and would otherwise look like a
successful run that happened to find nothing.

### 10-K Filing Summaries (frozen)

Existing summaries are served from `filing_summaries`; **the generator is retired**. `GET
/filing-summaries` and the whole React read path are unchanged, the table and its Envers audit table
are untouched, and no migration was needed — but nothing writes a new row, so a ticker whose 10-K
post-dates the last generation run returns an empty list. That is correct behavior now, not a bug.

The schema is retained deliberately so revisiting summarization later costs nothing.

Retrieve stored summaries via the public endpoint:

```bash
curl "http://localhost:8080/filing-summaries?ticker=AAPL"
```

Already-summarized filings (by accession number) are skipped on re-runs.

### FRED Economic Data

Public endpoint (no auth) returning FRED observation series, keyed by series id. Requires `FRED_API_KEY`. Each item may set `computeYoy: true` to request year-over-year percent change (`units=pc1`). Responses are cached in memory for 24 hours per series/units combination; nothing is persisted.

```bash
curl -X POST http://localhost:8080/fred-data \
  -H 'Content-Type: application/json' \
  -d '[{"seriesId": "UNRATE"}, {"seriesId": "CPIAUCSL", "computeYoy": true}]'
# => {"UNRATE": [{"date": "1948-01-01", "value": 3.4}, ...], "CPIAUCSL": [...]}
```

FRED data is provided by the Federal Reserve Bank of St. Louis (https://fred.stlouisfed.org/docs/api/fred/) and is served to the UI without persistence.

### Run Tests

```bash
mvn test
```

Tests run from `target/` (surefire `workingDirectory`), so the optional `.env` import in `application.properties` never leaks real secrets into test contexts. JaCoCo runs with the suite: the HTML report lands in `target/site/jacoco/index.html`, and `mvn verify` enforces a minimum line-coverage floor (`jacoco:check`). Repository tests use `@DataJpaTest` against the in-memory H2 database (Boot 4 artifact `spring-boot-data-jpa-test`).

### Code Quality

```bash
./mvnw spotless:apply    # auto-fix formatting (imports, whitespace, indentation)
./mvnw spotless:check    # what CI checks
./mvnw checkstyle:check  # lint rules
./mvnw verify            # everything CI runs: gates + tests + coverage floor
```

| Gate | Phase | Config |
|---|---|---|
| Maven enforcer (Maven 3.9+, Java 25+) | `validate` | `pom.xml` |
| Spotless | `validate` | `pom.xml` (inline) |
| Checkstyle | `validate` | `config/checkstyle/checkstyle.xml` |
| SpotBugs + FindSecBugs | `verify` | `config/spotbugs/exclude.xml` |
| PMD | `verify` | `config/pmd/ruleset.xml` |
| Error Prone | `compile` | `pom.xml` (`errorprone.checks.*` properties) |
| JaCoCo coverage floor | `verify` | `pom.xml` (`jacoco.line.minimum`) |

Error Prone runs as a javac plugin, so unlike the others it cannot be turned off with a `<skip>` parameter. It lives in a profile that deactivates whenever `-Dquality.skip` is passed, which is what keeps it out of the Docker build.

**Main sources compile warning-free under `-Werror`.** Any javac warning *or* Error Prone WARNING-tier finding fails the build, so nothing accumulates unnoticed and no per-check promotion list has to be maintained. `-Xlint:deprecation` is enabled deliberately; `rawtypes`/`unchecked` are not, since Spring and JPA generics would make them noise. Test sources are exempt from `-Werror` (see below).

Because of this, **use `JsonNode.asString()`, never `asText()`** — Jackson 3 deprecated the latter, and the build now rejects it.

Four WARNING-tier checks are additionally promoted to ERROR in the `errorprone.checks.*` properties. On main sources `-Werror` already covers them; they are kept because they document which findings matter most, they survive `-Werror` being removed, and `errorprone.checks.common` also applies to test sources:

| Check | Scope | Why |
|---|---|---|
| `UnusedMethod` | main | Dead private methods |
| `UnusedVariable` | main | Dead fields — **the only gate that catches an unused constructor-injected field**, since PMD counts `this.x = x;` as a use and SpotBugs does not analyse fields |
| `UnusedNestedClass` | main | Dead nested records/classes |
| `JavaTimeDefaultTimeZone` | main + test | A zone-less `LocalDate.now()`/`Year.now()` reads the host default zone, so "today" differs between a laptop, CI and Fargate — silently shifting trading-day and filing-window boundaries |

The dead-code trio is advisory on test sources only, and test sources do not get `-Werror`. Mockito's `@InjectMocks` is fed by `@Mock` fields that are never read when a test neither stubs nor verifies them: load-bearing (dropping one injects `null`) but indistinguishable from dead code to Error Prone, which cannot see field injection. `testsupport/TestJwtTokens` is likewise stuck with `java.util.Date`, which JJWT's API requires.

For `now()` calls, pass a constant from `util/MarketTime.java` rather than an inline zone: `MarketTime.MARKET` (`America/New_York`) for anything answering "what trading day or filing year is it", `MarketTime.STORAGE` (UTC) for audit timestamps that are only ever compared to other stored timestamps.

To silence a SpotBugs finding, silence a whole **category** in `config/spotbugs/exclude.xml` with a written rationale, or a single intentional **site** with `@SuppressFBWarnings(value = "...", justification = "...")` at the narrowest scope. The justification is not optional; reviewers should reject entries without one. No block in that file is accepted risk any more. Do not add an exclusion there for dead code — Error Prone gates it at compile time now.

### Filing-text regexes

The corporate-action date patterns combine lazy bounded quantifiers (`.{0,900}?`) with large month-name alternations, which FindSecBugs flags as REDOS. Runtime is bounded by `corporateaction/support/BoundedRegexInput`, a `CharSequence` that aborts a match once it exceeds `DEFAULT_BUDGET_MILLIS`; a timeout is treated as "no further matches", the same path an unmatched filing already takes.

The 19 flagged patterns reach the guard through three funnels, one per class: `CorporateActionFilingDateService.extractDatedCandidates` (14), `DividendDeclarationTupleExtractor.closestLabeledDate` (4) and `EtfDateExtractor.collectLabeledDateCandidates` (1).

**A new pattern pairing a lazy or bounded quantifier with a large alternation must match through the same guard.** That is the shape FindSecBugs flags, and the exclusion in `config/spotbugs/exclude.xml` is package-wide, so a new one is suppressed the moment it is written rather than reported. Patterns without that shape — simple greedy negated classes like `EtfAmountExtractor`'s, or the sentence-level triggers — do not need it. Guarding the input keeps every pattern byte-identical, so extraction accuracy is provably unchanged, unlike possessive quantifiers (which break the deliberate *nearest*-date semantics of the lazy `?`) or truncating input (which could drop a date near the end of a long filing). The FindSecBugs finding itself cannot be cleared, since it inspects pattern construction rather than the matcher call.

CI runs `mvn verify`, so the coverage floor is enforced on every PR. The floor lives in the `jacoco.line.minimum` property; raise it deliberately as coverage improves and never lower it to make a build pass. It is overridable on the command line (`-Djacoco.line.minimum=0.95`) purely so the gate itself can be tested.

Checkstyle owns semantics; Spotless owns whitespace, import order and line length. Never add whitespace, wrapping or `LineLength` modules to `checkstyle.xml`: two owners of the same concern produce a build that cannot be made green. The ruleset is curated rather than inherited from `sun_checks.xml` or `google_checks.xml`, and its header comment records what was excluded and why.

Wildcard imports are banned in both `src/main` and `src/test`. Static wildcard imports (`import static org.mockito.Mockito.*`) remain allowed, which is what keeps the existing test style legal.

Formatting is 4-space indent with a 120-column soft limit (see `/.editorconfig`). Spotless normalizes imports, indentation and trailing whitespace but **never reflows code**: brace placement and line wrapping stay as written, so `spotless:apply` will not churn unrelated lines in your diff.

Import order is `java`, `javax`, `jakarta`, `org`, `com`, everything else, then static imports.

Every gate shares one kill switch, `-Dquality.skip=true`. It exists solely so `Dockerfile` can build the jar without the repo's config files in its build context. Don't use it locally.

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## License

MIT

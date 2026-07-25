# SecFilingsAPI

A Spring Boot 3.4 microservice providing SEC EDGAR financial data for all publicly traded US companies.

## Features

- Fetches quarterly financial data from SEC EDGAR XBRL filings
- Bulk sync of all XBRL frames (2009 to present)
- Derives missing quarterly values from annual totals
- Calculates Trailing Twelve Months (TTM) metrics and Year-over-Year (YoY) growth
- Calculates financial ratios (net margin, gross margin, ROA, debt-to-assets)
- Ticker/CIK mapping from SEC ticker endpoints (including SEC mutual fund ticker index for ETF/fund coverage)
- Downloads and parses IEX HIST TOPS pcap/pcapng files into daily OHLCV prices (built-in binary parser, no external dependencies)
- Detects stock splits and dividends from SEC EDGAR `EntityCommonStockSharesOutstanding` and `CommonStockDividendsPerShareDeclared`
- Normalizes SEC dividend facts into quarterly payouts (handles cumulative YTD reporting and fiscal-Q4 edge cases)
- Derives dividend ex-dates from SEC 8-K record-date disclosures and settlement-regime logic (T+3 before 2017-09-05, T+2 through 2024-05-27, T+1 after)
- Applies cumulative backward price adjustment factors for split/dividend-adjusted OHLCV (decoupled from IEX load — runs as a separate process)
- Fetches 10-K filings from SEC EDGAR, extracts the MD&A section, and generates ~500 word summaries via a local LLM (llama.cpp server)
- Serves FRED (Federal Reserve Economic Data) observation series via public `POST /fred-data` (per-series optional year-over-year `pc1` units, 24h in-memory cache; powers the Economic Indicators page)
- **Market indexes (Spring-only — Django does not serve these routes):** `MarketIndex` (one row per index: `code` + `display_name`), `Listing` + `ListingIndexMetrics` (one row per listing per **calendar year**; IEX daily prices + SEC companyfacts for shares/float), and `IndexMember` rows (FK to `MarketIndex`). Public `GET /index-members` (no auth for now), `GET /iwb-reference-holdings` (bundled IWB CSV tickers + fund weights for UI benchmark), plus admin `POST /admin/indexes/refresh-stocks` and `POST /admin/indexes/rebuild` (`Authorization: Bearer` Django JWT for user id `1`, optional `code`, optional `year`). **Fattore 50** / **Fattore 100** / **Fattore 1000** are Russell-style (float-adjusted cap rank) top-50 / top-100 / top-1000 proxies (`FAT50` / `FAT100` / `FAT1000`), not official FTSE Russell products; run `refresh-stocks` first (or pass `refreshMetrics=true`) so metrics exist for the target year. Legacy paths `rebuild-fattore-50` / `rebuild-fattore-100` / `rebuild-fattore-1000` remain as aliases.

## Java package layout

Application code under `com.fattorestreet.sec_api`: `client` (SEC HTTP / `WebService`), `index`, `corporateaction` and `corporateaction.support`, `economic` (FRED client/cache), `fundamentals`, `listing`, `filing`, `marketdata`, plus shared `controller`, `repository`, `model`, `config`, and `util`. The `index` package holds index membership listing, SEC facts parsing, and metrics refresh. Tests mirror those package names under `src/test/java`.

## Stack

- Java 17, Spring Boot 3.4.2
- Spring Data JPA + Hibernate (PostgreSQL)
- **Hibernate Envers** — all JPA entities are audited (`@Audited`). Hibernate creates a `revinfo` table and per-entity `*_AUD` tables (for example `assets_AUD`, `daily_prices_AUD`). Expect **large** audit table growth on high-churn data, especially `daily_prices` ingests; plan disk and retention accordingly. The inverse `Asset.listings` collection is `@NotAudited` so listing history is tracked only via `listings_AUD`.
- Spring Security OAuth2 Resource Server (JWT): admin routes verify Django SimpleJWT access tokens (HS256, same `SECRET_KEY`; only `user_id` claim `1` is granted admin)
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

- Java 17
- Maven
- PostgreSQL

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/springboot` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `POSTGRES_PASSWORD` | `postgres` | Database password (same key Django uses; also the postgres image's init var) |
| `SHOW_JPA_SQL` | `false` | When `true`, logs Hibernate-generated SQL. Use locally; leave unset or `false` in production. |
| `SECRET_KEY` | (required) | Must match Django `SECRET_KEY` — used to verify `Authorization: Bearer` JWTs (SimpleJWT HS256). Only access tokens whose `user_id` claim is `1` may call `/admin/**`. |
| (property) `fattore50.rebuild.top-n` | `50` | How many names the Fattore 50 rebuild includes. Set in `.env` as `fattore50.rebuild.top-n=50` if needed. |
| (property) `fattore100.rebuild.top-n` | `100` | How many names the Fattore 100 rebuild includes. Set in `.env` as `fattore100.rebuild.top-n=100` if needed. |
| (property) `fattore1000.rebuild.top-n` | `1000` | How many names the Fattore 1000 rebuild includes. Set in `.env` as `fattore1000.rebuild.top-n=1000` if needed. |
| `INDEX_LOAD_YEAR` | `0` (current year) | Target calendar year for the one-shot index load (`APP_RUN_MODE=index-load`) |
| `INDEX_LOAD_SCOPE` | `russell1000` | Index-load metrics refresh scope: `russell1000` (IWB universe) or `all` |
| `INDEX_LOAD_SKIP_REFRESH` | `false` | When `true`, the index load skips the metrics refresh and only rebuilds |
| `INDEX_LOAD_TICKER` | (empty) | When set, the index load refreshes just this ticker (smoke-test mode) before rebuilding |
| `INDEX_LOAD_MIN_PROCESSED` | `800` | Minimum refreshed listings before the index load rebuilds; below it the task keeps existing members and exits `1` |
| `LLM_SERVER_URL` | `http://localhost:8081` | URL of the llama.cpp server for 10-K summarization |
| `DJANGO_PORTFOLIO_BASE_URL` | `http://localhost:8000/portfolio` | Base URL for Django portfolio API used by diagnostics-only yfinance validation |
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

`springboot/.env` is auto-imported at startup (`spring.config.import=optional:file:.env[.properties]`) for local runs. Keep values unquoted. Set `SECRET_KEY` to the **same value as Django** (JWT signing). Property `app.django-jwt-secret` defaults to `${SECRET_KEY}` in `application.properties`.

**CORS (browser → Spring from the Vite dev server or production):** `WebConfig` registers a `CorsConfigurationSource` and `SecurityConfig` enables `http.cors` so `OPTIONS` preflight succeeds before JWT filters. Allowed origins include local dev (`http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost`, `http://localhost:80`) and production (`https://fattorestreet.com`, `https://www.fattorestreet.com`). Edit `WebConfig` if you add more hosts.

**Admin curl calls:** obtain an access token from Django (`POST /users/api/token/`), then pass `Authorization: Bearer <access>`. The token subject must be Django user **primary key 1**.

The service starts on port **8080** by default.

### Run with Docker

```bash
docker build -t sec-api .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/springboot \
  -e DB_USERNAME=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e SECRET_KEY=same-value-as-django-secret-key \
  sec-api
```

### IEX Price Data

The service downloads IEX HIST TOPS pcap files, parses them in-process using a custom binary parser, and writes OHLCV data directly to the database. Trigger via the admin endpoint:

```bash
# export ACCESS_TOKEN='<Django access JWT for user id 1>'  # from POST /users/api/token/

# Load ~1 year of IEX TOPS data (default 252 trading days)
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/load-hist

# Or specify a smaller window
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/load-hist?days=30"
```

Dates already in the database are skipped automatically. IEX load saves raw prices only — price adjustments run as a separate process.

### Market index metrics (IEX + SEC)

`ListingIndexMetrics` is built from the latest IEX-derived `DailyPrice` per ticker, SEC companyfacts (`EntityCommonStockSharesOutstanding`, `EntityPublicFloat`), and SEC **submissions** JSON for jurisdiction: `country_incorp` / `country_hq` plus `state_incorp` / `state_hq` (US state codes are split out so country columns are real countries). Run after listings and daily prices exist. **Dual-listed share classes (same CIK):** SEC shares outstanding is consolidated per issuer; for Alphabet **GOOG** / **GOOGL** only, `market_cap` and `free_float_market_cap` are scaled by **½** per listing so the two lines together approximate the issuer total (see `DualClassIndexCapSplit`).

**Ticker scope:** `refresh-stocks` defaults to Russell 1000 / IWB tickers from `src/main/resources/data/IWB_holdings.csv` (equity rows only), packaged as `classpath:/data/IWB_holdings.csv`. To refresh index metrics for *all* listings in the database, pass `scope=all`.

**Upgrading existing databases** (pre–`MarketIndex` FK): if `index_members` still has a legacy `market_index` text column, insert the matching `market_indexes` row if needed, backfill `market_index_id`, then drop the old column after verifying rows.

```bash
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/indexes/refresh-stocks
# Optional: target a calendar year (defaults to current year)
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/indexes/refresh-stocks?year=2025"
#
# Optional: refresh all listings in the DB (not just Russell 1000 / IWB tickers)
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/indexes/refresh-stocks?scope=all"
```

Rebuild cap-ranked indexes (`FAT50`, `FAT100`, `FAT1000`): creates or reuses each `MarketIndex` row, replaces `IndexMember` rows (weights sum to 100%). Optional `code` selects one index; omit to rebuild all (FAT100, FAT1000, FAT50). Optional `refreshMetrics=true` refreshes metrics once for that year first; optional `year` defaults to the current calendar year.

```bash
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/indexes/rebuild
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/indexes/rebuild?code=FAT50"
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/indexes/rebuild?code=FAT1000"
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/indexes/rebuild?refreshMetrics=true"
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/indexes/rebuild?year=2025&refreshMetrics=true"

# Legacy aliases (singular `rebuild` in JSON response)
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/indexes/rebuild-fattore-50
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/indexes/rebuild-fattore-100
curl -X POST -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/indexes/rebuild-fattore-1000
```

**Scheduled run (Fargate):** the same refresh + rebuild-all sequence runs nightly as an ephemeral Fargate task (`APP_RUN_MODE=index-load` via `IndexLoadRunner`, scheduled after the hist-load task so fresh `DailyPrice` rows exist; see `deploy/terraform/README.md`). The runner skips the rebuild and exits `1` if the refresh processes fewer than `INDEX_LOAD_MIN_PROCESSED` listings — the rebuild deletes members by index code, so rebuilding after a mostly-failed refresh (SEC outage, empty new calendar year) would shrink the live indexes. The admin endpoints above remain for manual runs.

List available indexes and fetch constituents:

```bash
curl "http://localhost:8080/indexes"
curl "http://localhost:8080/index-members?code=FAT50"
curl "http://localhost:8080/index-members?code=FAT100"
curl "http://localhost:8080/index-members?code=FAT1000"
```

### Price Adjustments (Splits & Dividends)

Corporate action detection and price adjustment runs automatically at the end of the nightly
Fargate hist-load task (`APP_RUN_MODE=hist-load`; disable with env `HIST_LOAD_ADJUST_ENABLED=false`,
property `app.hist-load.adjust-enabled`). Without `force`, SEC detection refreshes on a rolling
cadence: each run re-detects the stalest ~1/7 of tickers (tracked via `listings.last_sec_detection_at`,
so every ticker is re-scanned about weekly), and any ticker with a >25% overnight move among its
most recent closes is re-detected immediately (split signature; the check scans the last few rows so
a multi-day catch-up load cannot bury the break). A failed SEC fetch does **not** stamp
`last_sec_detection_at`, so the ticker retries the next run instead of waiting out the interval.
Price rows are only written when an adjusted value actually changes, so the nightly run writes just
the new rows unless detection changed an action. The endpoints below stay available for manual runs:

```bash
# Adjust all tickers (rolling SEC re-detection: stalest ~1/7 of tickers + price-jump triggers)
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/adjust-prices

# Force re-fetch from SEC for all tickers (catches new splits/dividends)
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/adjust-prices?force=true"

# Adjust ETFs only (SEC filing-derived ETF actions)
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/adjust-prices?etfOnly=true&minConfidence=75"

# Adjust a single ticker
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/adjust-prices?ticker=AAPL"

# Optional diagnostics-only validation against yfinance reference events (via Django portfolio API)
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/adjust-prices?ticker=AAPL&force=true&validateWithYfinance=true"

# Diagnostics-only adjusted-price comparison against the yfinance adjusted-close reference
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/validate-adjusted-prices?ticker=AAPL"
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/validate-adjusted-prices?minDate=2020-01-01"
```

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
- Returns ETF diagnostics in `/admin/adjust-prices` output:
  - single-fund ticker mode: `etfDiagnostics`
  - batch mode: `etfDiagnosticsSummary`
  - both include skip-reason buckets and capped sample rows for troubleshooting misses (including separate `date_missing` and `below_confidence` reasons).
- Returns equity diagnostics in `/admin/adjust-prices` output:
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
- Adjusted-price validation (`GET /admin/validate-adjusted-prices?ticker=&minDate=`) compares stored `adjustedClose` against the yfinance adjusted-close reference (`/portfolio/api/asset-prices/`), normalized at the latest common date:
  - reports `meanAbsDeviation`, `maxAbsDeviation`, `datesOverThreshold` (0.5% level threshold)
  - reports `breaks`: dates where the sec/yf ratio steps day-over-day by more than 1%, each with the nearest stored corporate action within ±7 days; every break localizes one missing, extra, or misdated event
  - omit `ticker` for a batch summary (`worstTickers`, `sampleBreaks`); diagnostics-only, never persisted, never called by the nightly job.
- Public comparison endpoints exposed for UI validation views:
  - `GET /dividends?ticker=...` returns persisted internal dividend actions.
  - `GET /splits?ticker=...` returns persisted internal split actions (`ratio` is `old_shares / new_shares`).

### Corporate Action Reconciliation Runbook

Use this sequence for safe subset-to-batch reconciliation when load quality drifts:

1. **Subset triage first**: run a small ticker cohort with `force=true&validateWithYfinance=true`.
2. **Inspect diagnostics**: prioritize `missing_in_sec` and `amount_drift` mismatches and high-volume skip reasons.
3. **Tune + retest**: apply parser/date-mapping fixes, rerun the same subset, and confirm mismatch reduction.
4. **Promote to batch**: run full batch with `force=true` (optionally `etfOnly=true` or `equityOnly=true`) plus `validateWithYfinance=true`.
5. **Record outcomes**: capture `equityDiagnosticsSummary`, `etfDiagnosticsSummary`, and `validationSummary` snapshots for regression tracking.

Asset load with integrated ETF identity enrichment:

```bash
# Load equities/funds from SEC and enrich ETF series/class identity fields
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/asset-load"

# Overwrite previously resolved ETF identity rows during load
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/asset-load?overwriteExisting=true"
```

### 10-K Filing Summaries

Fetches 10-K filings from SEC EDGAR, extracts the Management's Discussion and Analysis (Item 7) section, and generates ~500 word summaries using a local Qwen 2.5-7B model via llama.cpp.

Requires the llama.cpp server to be running (see `llm/run-server.sh`):

```bash
# Summarize all equities with a CIK
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/admin/summarize-filings

# Summarize a single ticker
curl -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/admin/summarize-filings?ticker=AAPL"
```

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
| Maven enforcer (Maven 3.9+, Java 17+) | `validate` | `pom.xml` |
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

To silence a SpotBugs finding, silence a whole **category** in `config/spotbugs/exclude.xml` with a written rationale, or a single intentional **site** with `@SuppressFBWarnings(value = "...", justification = "...")` at the narrowest scope. The justification is not optional; reviewers should reject entries without one. One block in that file is marked `REVISIT` because it is accepted risk rather than a false positive. Do not add an exclusion there for dead code — Error Prone gates it at compile time now.

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

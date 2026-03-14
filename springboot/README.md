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
- Derives dividend ex-dates from SEC 8-K record-date disclosures and settlement-regime logic (pre-2024-05-28 T+2, post-cutover T+1)
- Applies cumulative backward price adjustment factors for split/dividend-adjusted OHLCV (decoupled from IEX load — runs as a separate process)
- Fetches 10-K filings from SEC EDGAR, extracts the MD&A section, and generates ~500 word summaries via a local LLM (llama.cpp server)
- Also supports loading pre-generated OHLCV CSV files

## Stack

- Java 17, Spring Boot 3.4.2
- Spring Data JPA + Hibernate (PostgreSQL)
- Spring WebFlux (reactive HTTP client for SEC APIs)
- Bean Validation (`spring-boot-starter-validation`)
- Jackson CSV for IEX price CSV ingestion
- Custom pcap/pcapng + IEX-TP binary parser using `ByteBuffer` for HIST TOPS trade extraction
- Maven build, Docker multi-stage image

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
| `DB_URL` | `jdbc:postgresql://localhost:5432/sec-api` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `ADMIN_API_KEY` | `spike` | Key for `X-Admin-Key` header on admin endpoints |
| `IEX_DATA_DIR` | `./data/iex_prices` | Directory containing IEX daily OHLCV CSV files |
| `LLM_SERVER_URL` | `http://localhost:8081` | URL of the llama.cpp server for 10-K summarization |
| `SEC_HTTP_CONNECT_TIMEOUT_MS` | `15000` | SEC API connect timeout (milliseconds) |
| `SEC_HTTP_READ_TIMEOUT_MS` | `120000` | SEC API read timeout per request (milliseconds) |
| `SEC_HTTP_RETRY_MAX_ATTEMPTS` | `3` | Max attempts for transient SEC failures (429/5xx/timeout) |
| `SEC_HTTP_RETRY_BASE_BACKOFF_MS` | `1000` | Base backoff for SEC retries; multiplied by attempt |
| `SEC_HTTP_MIN_INTERVAL_MS` | `250` | Minimum delay between SEC requests to reduce throttling |

### Run Locally

```bash
mvn spring-boot:run
```

The service starts on port **8080** by default.

### Run with Docker

```bash
docker build -t sec-api .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/sec-api \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  sec-api
```

### IEX Price Data

The service downloads IEX HIST TOPS pcap files, parses them in-process using a custom binary parser, and writes OHLCV data directly to the database. Trigger via the admin endpoint:

```bash
# Load ~1 year of IEX TOPS data (default 252 trading days)
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/load-hist

# Or specify a smaller window
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/load-hist?days=30"
```

Dates already in the database are skipped automatically. IEX load saves raw prices only — price adjustments run as a separate process.

### Price Adjustments (Splits & Dividends)

Corporate action detection and price adjustment is decoupled from IEX loading. Run it separately:

```bash
# Adjust all tickers (skips SEC re-fetch for tickers with existing actions)
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/adjust-prices

# Force re-fetch from SEC for all tickers (catches new splits/dividends)
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/adjust-prices?force=true"

# Adjust ETFs only (SEC filing-derived ETF actions)
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/adjust-prices?etfOnly=true&minConfidence=75"

# Adjust a single ticker
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/adjust-prices?ticker=AAPL"

# Optional diagnostics-only validation against yfinance reference events
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/adjust-prices?ticker=AAPL&force=true&validateWithYfinance=true"
```

Dividend ingestion behavior:
- Normalizes dividend facts to per-quarter payouts before persistence.
- Prefers true quarterly facts over cumulative rows for the same period end date (with form-priority tie-breaks) and skips annual-only period-end leakage.
- Derives missing fiscal Q4 payouts from annual 10-K totals when quarter facts are absent, with guardrails to reject implausible derived values.
- Derives ex-dates from SEC 8-K record-date disclosures using weighted candidate extraction (primary filing + exhibit scan), filing-date gates, and global quarter-sequence assignment.
- Uses confidence-aware inferred fallback ex-dates (cadence/lag based) when no reliable record date can be extracted, instead of persisting quarter-end placeholders.
- Derives split effective dates from SEC 8-K filing/exhibit text when available (including archived SEC submission bundles), and falls back to SEC shares-jump detection dates with low-confidence logging when unresolved.
- Uses bounded SEC retries with configurable timeouts to prevent indefinite hangs during long reconciliation scans.
- Stores both SEC raw and split-adjusted dividend values per event (`rawDividend`, `adjustedDividend`), while keeping `ratio` as a compatibility alias to adjusted values.
- Allows multiple same-date dividend rows when amounts differ (e.g., regular + special) via uniqueness on (`ticker`, `action_type`, `effective_date`, `ratio`) plus a lookup index on (`ticker`, `action_type`, `effective_date`).
- Converts older dividend amounts to current-share basis using detected forward splits, with canonical split-ratio snapping and 4-decimal rounding.
- Uses the adjusted dividend value when applying dividend price-adjustment factors.
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
  - includes split/date-path counters and dividend parser-path counters (facts parsed, normalized events, record-date path usage, fallback usage, inserts/updates).
- Optional yfinance validation report (`validateWithYfinance=true`) is diagnostics-only:
  - ticker mode adds `validationReport`
  - batch mode adds `validationSummary`
  - mismatch taxonomy: `missing_in_sec`, `extra_in_sec`, `date_drift`, `amount_drift`
  - **No yfinance values are written to DB**; SEC remains source of truth.
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
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/asset-load"

# Overwrite previously resolved ETF identity rows during load
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/asset-load?overwriteExisting=true"
```

You can also load pre-generated CSV files:

```bash
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/load-prices
```

### 10-K Filing Summaries

Fetches 10-K filings from SEC EDGAR, extracts the Management's Discussion and Analysis (Item 7) section, and generates ~500 word summaries using a local Qwen 2.5-7B model via llama.cpp.

Requires the llama.cpp server to be running (see `llm/run-server.sh`):

```bash
# Summarize all equities with a CIK
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/summarize-filings

# Summarize a single ticker
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/summarize-filings?ticker=AAPL"
```

Retrieve stored summaries via the public endpoint:

```bash
curl "http://localhost:8080/filing-summaries?ticker=AAPL"
```

Already-summarized filings (by accession number) are skipped on re-runs.

### Run Tests

```bash
mvn test
```

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## License

MIT

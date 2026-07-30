# API Reference

The backend is split across two services:
- **Django** (REST Framework) -- base URL for local development: `http://localhost:8000`
- **Spring Boot** (SEC EDGAR microservice) -- base URL for local development: `http://localhost:8080`

## 📚 API documentation

There is no machine-generated OpenAPI/Swagger in this repo right now. Use the endpoint tables below for Django and Spring Boot.

## 🔐 Authentication (Users)

All protected endpoints require a valid JWT Access Token in the header:
`Authorization: Bearer <access_token>`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/users/api/users/` | Register a new user. |
| `POST` | `/users/api/token/` | Login - Obtain Access and Refresh tokens. |
| `POST` | `/users/api/token/refresh/` | Refresh an expired Access token. |

## 📈 Portfolio (Assets)

Management of financial assets and retrieving market data.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/portfolio/api/assets/` | List all assets for the authenticated user. |
| `POST` | `/portfolio/api/assets/` | Track a new asset. |
| `GET` | `/portfolio/api/assets/<id>/` | Retrieve details of a specific asset. |
| `DELETE` | `/portfolio/api/assets/<id>/` | Remove an asset from portfolio. |
| `GET` | `/portfolio/api/asset-info/` | Fetch live metadata for a ticker (e.g., name, sector). |
| `GET` | `/portfolio/api/asset-prices/` | Get historical adjusted-close price data for charting (yfinance). |
| `GET` | `/portfolio/api/asset-dividends/` | Get historical dividend events per ticker from yfinance. |
| `GET` | `/portfolio/api/asset-splits/` | Get historical split events per ticker from yfinance. |
| `GET` | `/portfolio/api/quote/` | Get the latest price quote. |

## 🍽 Restaurants (Reviews)

Social feature for reviewing and sharing restaurant experiences.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/restaurants/api/restaurant-list-create/` | List or create restaurants. |
| `GET` | `/restaurants/api/review-list/` | List all reviews. |
| `POST` | `/restaurants/api/review-create/` | Submit a new review. |
| `GET` | `/restaurants/api/review/<id>/` | View a specific review. |
| `PUT` | `/restaurants/api/review-update/<id>/` | Edit an existing review. |
| `GET` | `/restaurants/api/restaurant-recommend/` | Get recommendations based on user history. |

## 🤖 Chatbot

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/chatbot/api/chatbot/` | Send a message to the AI investing assistant. |

## 🧭 Changeflow (Tickets)

Feedback and issue intake for authenticated users.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/changeflow/api/tickets/` | Submit a new feedback ticket for the logged-in user. |

## 📝 Blog

Public blog posts (no authentication required).

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/blog/api/posts/` | List published blog posts (supports `search`, `category`, `tag`, `page`, `page_size`). |
| `GET` | `/blog/api/posts/<slug>/` | Retrieve a single published blog post by slug. |
| `GET` | `/blog/api/categories/` | List blog categories. |
| `GET` | `/blog/api/tags/` | List blog tags. |

---

# Spring Boot -- SEC EDGAR Microservice

## 📊 Market indexes

**All index traffic is handled by Spring Boot (`sec-api`)** — Django is not involved in these routes or tables. Data lives in the `springboot` PostgreSQL database. Metrics are refreshed from **IEX-derived** `DailyPrice` rows and **SEC** companyfacts (see `springboot/README.md`). Public routes use the same root-level style as other Spring endpoints (e.g. `/quarters`).

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/indexes` | None | List available market indexes (`code`, `displayName`). |
| `GET` | `/index-members` | None | List index members with nested `stock` payload (listing + metrics). Optional query param `code` filters to a single index (e.g. `?code=FAT50`, `?code=FAT100`, or `?code=FAT1000`). Each `stock` includes `countryIncorp`, `countryHQ`, `stateIncorp`, `stateHQ` (US state/territory codes or SEC opaque codes when country is unknown), plus float/mkt cap fields — see `IndexMemberApiService.StockRow` in Spring Boot. |
| `GET` | `/iwb-reference-holdings` | None | Bundled iShares **IWB** (Russell 1000 ETF) equity rows from `classpath:/data/IWB_holdings.csv` (`springboot/src/main/resources/data/IWB_holdings.csv`): JSON array of `{ "ticker", "weightPercent" }` (fund-reported weight % of NAV). Used by the Indexes page to compare **FAT1000** vs the reference file. |
| `POST` | `/admin/indexes/refresh-stocks` | `Authorization: Bearer` (Django JWT) | Recompute `ListingIndexMetrics` for a calendar **year** using IEX daily prices and SEC companyfacts. Optional query param `year` (defaults to current calendar year). Optional query param `scope` controls ticker universe: `russell1000` (default; tickers in bundled `classpath:/data/IWB_holdings.csv`, a Russell 1000 / IWB export) or `all` (all listings in the DB). Response includes `year`, `scope`, `skipReasonCounts` (aggregate counts by `skippedTickers` reason), `processed`, `skipped`, and `skippedTickers`. |
| `POST` | `/admin/indexes/rebuild` | `Authorization: Bearer` (Django JWT) | Rebuild cap-ranked indexes (`FAT50`, `FAT100`, `FAT1000`). Optional query param `code` (case-insensitive) selects one index; omit or leave blank to rebuild **all** configured indexes (order: `FAT100`, `FAT1000`, `FAT50`). Optional `year` (defaults to current calendar year). Optional `refreshMetrics=true` runs `refresh-stocks` for that year once before rebuilds. Response JSON: `year`, `rebuilds` (array of objects: `indexCode`, `year`, `memberCount`, `partial`, `totalFreeFloatMarketCap`, `tickers`), optional `refresh`, `duration`. Unknown `code` returns 400. Not an official FTSE Russell index. |
| `POST` | `/admin/indexes/rebuild-fattore-50` | `Authorization: Bearer` (Django JWT) | **Legacy alias** for `POST /admin/indexes/rebuild?code=FAT50`. Response uses singular `rebuild` instead of `rebuilds`. |
| `POST` | `/admin/indexes/rebuild-fattore-100` | `Authorization: Bearer` (Django JWT) | **Legacy alias** for `POST /admin/indexes/rebuild?code=FAT100`. Response uses singular `rebuild` instead of `rebuilds`. |
| `POST` | `/admin/indexes/rebuild-fattore-1000` | `Authorization: Bearer` (Django JWT) | **Legacy alias** for `POST /admin/indexes/rebuild?code=FAT1000`. Response uses singular `rebuild` instead of `rebuilds`. |

## 📈 FRED Economic Data

Economic observation series from the Federal Reserve Bank of St. Louis (FRED). Previously served by Django at `/portfolio/api/fred-data/`; now handled entirely by Spring Boot. Requires `FRED_API_KEY` on the Spring Boot service. Responses are cached in memory for 24h per series/units combination; nothing is persisted.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/fred-data` | None | Fetch one or more FRED observation series. Request body: JSON array of `{ "seriesId": "UNRATE", "computeYoy": false }` (`computeYoy: true` requests year-over-year percent change, FRED `units=pc1`). Response: object keyed by series id, each value an ascending-date array of `{ "date": "YYYY-MM-DD", "value": number }` with FRED's `"."` missing markers dropped. |

## 📄 SEC Financial Data

Quarterly financial data sourced from SEC EDGAR XBRL filings.

### List Quarters

`GET /quarters?ticker={TICKER}`

Returns all stored quarters for a specific ticker.

**Parameters:**
- `ticker` (Required): Stock ticker symbol (e.g., `AAPL`). Max 10 chars, uppercase.

**Response:**
```json
{
  "ticker": "AAPL",
  "cik": "320193",
  "quarters": [
    {
      "year": 2024, "quarter": 4, "periodStart": "2024-10-01", "periodEnd": "2024-12-31",
      "revenues": 0, "netIncomeLoss": 0, "operatingIncomeLoss": 0, "grossProfit": 0,
      "epsBasic": 0.0, "epsDiluted": 0.0,
      "assets": 0, "liabilities": 0, "equity": 0, "cash": 0, "receivables": 0, "inventory": 0,
      "ocf": 0, "dividends": 0, "buybacks": 0
    }
  ]
}
```

### Company Fact Sheet

`GET /company-fact-sheet?ticker={TICKER}`

Provides a summary of trailing twelve months (TTM) performance and the latest balance sheet status.

**Parameters:**
- `ticker` (Required): Stock ticker symbol (e.g., `AAPL`). Max 10 chars, uppercase.

**Response:**
- `ticker`, `cik`
- **TTM metrics:** `ttmNetIncome`, `ttmRevenue`, `ttmOperatingCashFlow`, `ttmOperatingIncome`, `ttmGrossProfit`
- **YoY growth:** `ttmNetIncomeYoY`, `ttmRevenueYoY` (e.g., `"15.50%"`)
- **Latest balance sheet:** `latestAssets`, `latestLiabilities`, `latestEquity`, `latestInventory`, `latestCash`, `latestEps`
- **Ratios:** `netMargin`, `grossMargin`, `roA`, `debtToAssets`, `cashToLiabilities` (percentages), `ocfToNetIncome`
- `latestQuarterEnd`: Date of the most recent quarter (`YYYY-MM-DD`)

## 📈 IEX Daily Prices

Historical daily OHLCV price data sourced from IEX exchange HIST files.

### List Prices

`GET /prices?ticker={TICKER}&start={DATE}&end={DATE}`

Returns daily OHLCV prices for a specific ticker.

**Parameters:**
- `ticker` (Required): Stock ticker symbol (e.g., `AAPL`). Max 10 chars, uppercase.
- `start` (Optional): Start date in `YYYY-MM-DD` format.
- `end` (Optional): End date in `YYYY-MM-DD` format.

**Response:**
```json
{
  "ticker": "AAPL",
  "prices": [
    {
      "date": "2025-03-15",
      "open": 172.50,
      "high": 174.20,
      "low": 171.80,
      "close": 173.90,
      "adjustedOpen": 171.95,
      "adjustedHigh": 173.65,
      "adjustedLow": 171.25,
      "adjustedClose": 173.35,
      "volume": 45230
    }
  ]
}
```

Adjusted prices account for stock splits and dividends detected from SEC EDGAR filings. If no corporate actions have been detected for a ticker, adjusted values equal the raw values.

### List Dividends

`GET /dividends?ticker={TICKER}`

Returns stored internal dividend events (corporate actions) for a specific ticker.

**Parameters:**
- `ticker` (Required): Stock ticker symbol (e.g., `AAPL`). Max 10 chars, uppercase.

**Response:**
```json
{
  "ticker": "AAPL",
  "dividends": [
    {
      "date": "2025-02-10",
      "rawValue": 0.25,
      "adjustedValue": 0.25,
      "value": 0.25,
      "source": "SEC_EQUITY_XBRL",
      "formType": "8-K",
      "accessionNumber": "0000320193-25-000010",
      "recordDate": "2025-02-10",
      "payDate": "2025-02-13",
      "confidenceScore": 87.0
    }
  ]
}
```

`value` is a backward-compatible alias of `adjustedValue`. Internally, dividend ingestion keeps both SEC raw quarterly values and split-adjusted values. Metadata fields (`source`, `formType`, `accessionNumber`, `recordDate`, `payDate`, `confidenceScore`) are optional and may be `null` for older rows.

### List Splits

`GET /splits?ticker={TICKER}`

Returns stored internal split events (corporate actions) for a specific ticker.

**Parameters:**
- `ticker` (Required): Stock ticker symbol (e.g., `AAPL`). Max 10 chars, uppercase.

**Response:**
```json
{
  "ticker": "AAPL",
  "splits": [
    {
      "date": "2020-08-31",
      "ratio": 0.25,
      "value": 0.25,
      "source": "SEC_EQUITY_XBRL",
      "formType": "8-K",
      "accessionNumber": "0000320193-20-000096",
      "confidenceScore": 93.0
    }
  ]
}
```

`ratio` is the canonical split adjustment factor (`old_shares / new_shares`; e.g., `0.25` for a 4:1 forward split). `value` is provided as a compatibility alias.

## 🔧 Admin Endpoints

All admin endpoints require `Authorization: Bearer <access_token>` where the token is a **Django SimpleJWT access JWT** (HS256, signed with the same `SECRET_KEY` configured on the Spring Boot service). The JWT `user_id` claim must be **`1`** (Django user primary key); other authenticated users receive **403** on `/admin/**`. Missing or invalid tokens yield **401**.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/admin/asset-load?overwriteExisting=false` | Load all US tickers from SEC ticker endpoints (`company_tickers.json` and `company_tickers_mf.json`) into the database, then enrich ETF listings with SEC series/class identity mapping. |
| `GET` | `/admin/sync-frames` | Sync XBRL frame data from SEC (all frames since 2009) into `Quarter` rows. The same sync runs nightly as a Fargate task over a recent year window instead of full history (`APP_RUN_MODE=fundamentals-load`, `FUNDAMENTALS_LOAD_YEARS_BACK`); this endpoint remains the way to force a full-history backfill. |
| `GET` | `/admin/load-hist?days=N` | Download IEX HIST TOPS PCAPs, parse trades, and insert raw OHLCV into DB. Default 252 days (~1 year). Does **not** trigger price adjustments. |
| `GET` | `/admin/adjust-prices?ticker=X&force=false&etfOnly=false&equityOnly=false&minConfidence=70&validateWithYfinance=false` | Detect splits/dividends from SEC and apply adjustment factors to OHLCV prices. Supports ETF-only or equity-only batch mode and a minimum confidence threshold for ETF actions extracted from ETF filing forms (`497`, `485*`, `N-CSR/N-CSRS`). ETF detection scans the primary filing plus a capped set of likely exhibit/attachment documents, applies scored identity matching (ticker + class/series signals), and ranks amount/date candidates from sentence and table-like layouts. ETF date extraction now normalizes HTML/text blocks, supports additional formats (`MM-DD-YYYY`, `MM/DD/YY`, `YYYY/MM/DD`, abbreviated month labels), and can use low-confidence pay-date/filing-date fallbacks when explicit ex/record dates are missing. Dividend facts are normalized to quarterly payouts (including guarded fiscal-Q4 derivation from annual 10-K totals), and ex-dividend dates are mapped from SEC 8-K record dates (T+3 before 2017-09-05, T+2 through 2024-05-27, T+1 after) using filing metadata and exhibit fallback parsing. When record dates cannot be reliably recovered, SEC-only fallback infers ex-dates from cadence/lag (with diagnostics) instead of quarter-end placeholders. Split factors are snapped to canonical ratios before dividend back-adjustment, and split effective dates are resolved price-break-first: the raw IEX close series is searched for the overnight break matching the split multiplier (price break > SEC filing text > shares-fact date), XBRL split candidates with full price coverage but no matching break are rejected, same-ratio splits within ±90 days are re-dated in place, and unexplained persistent split-like price breaks are persisted as `SEC_PRICE_CORROBORATED` when a filing split-date candidate confirms them (or on price evidence alone for ≥5x moves). Equity dividend ex-dates are anchored to amount-matched 8-K declaration tuples (record/payable/ex dates stated next to the per-share amount) before falling back to direct ex-date extraction and DP record-date assignment; each row records its `ex_date_source` provenance and a matching confidence score, weaker-grounded re-detections never move dates set by stronger paths, and declaration-anchored rows are excluded from orphan pruning. Recent high-confidence declaration tuples that no XBRL event claims are promoted to provisional dividend events (closing the 8-K-to-10-Q blind window); per-accession extraction results are persisted in `filing_extractions` so re-scans only fetch documents for new accessions; and when too many filing fetches fail, the run suppresses synthetic inserts and pruning instead of degrading stored data. Persisted dividend rows keep both `rawValue` and `adjustedValue`; adjusted prices use `adjustedValue`. SEC fetches use bounded retries and configurable timeouts to avoid indefinite hangs during long scans. Omit `ticker` to adjust all. Without `force`, automatic SEC detection is scoped to members of one index (`FAT1000` by default, property `app.price-adjustment.detection-index-code`, env `PRICE_ADJUSTMENT_DETECTION_INDEX`); the price universe is the full IEX HIST symbol set (~24k tickers, about two thirds with no SEC counterpart), which takes days to re-scan at the SEC rate limit. Within that scope, detection refreshes on a rolling cadence: each batch run re-detects the stalest ~1/7 of in-scope tickers (tracked via `listings.last_sec_detection_at`, so every in-scope ticker is re-scanned about weekly) plus any in-scope ticker with a >25% overnight move among its most recent closes (split signature); a failed SEC fetch does not refresh the staleness stamp, so the ticker retries next run. Price adjustment still covers every ticker with unadjusted rows: out-of-scope tickers get adjusted columns filled from raw prices or already-stored actions, so they drain out of the backlog. A configured index with no members skips automatic detection rather than falling back to the full universe; `force=true` bypasses the scope. Actions dated on non-trading days are snapped forward to the next trade date; per-ticker failures leave adjusted columns NULL so the next run retries. Set `force=true` to re-fetch SEC data for all tickers (reconciles existing actions and catches new splits/dividends). Set `validateWithYfinance=true` for diagnostics-only mismatch reporting against yfinance reference events (no writes from yfinance). This same batch adjustment also runs automatically at the end of the nightly Fargate hist-load task (`APP_RUN_MODE=hist-load`, disable with `HIST_LOAD_ADJUST_ENABLED=false`), always without yfinance validation. |
| `GET` | `/admin/validate-adjusted-prices?ticker=X&minDate=2016-01-01` | Diagnostics-only comparison of stored `adjustedClose` against the yfinance adjusted-close reference served by Django (`/portfolio/api/asset-prices/`). Ratios are normalized at the latest common date; the report includes `meanAbsDeviation`, `maxAbsDeviation`, `datesOverThreshold` (0.5% level threshold), and `breaks` (dates where the ratio steps day-over-day by more than 1%, each annotated with the nearest stored corporate action within ±7 days — every break localizes one missing, extra, or misdated event). Omit `ticker` for a batch summary across all price tickers (`worstTickers`, `sampleBreaks`). Nothing is persisted and the nightly job never calls it. |

When ticker mode is used for a fund (`/admin/adjust-prices?ticker=VOO`), response may include:
- `etfDiagnostics`: per-run ETF extraction diagnostics (`filingsConsidered`, `filingsFetched`, `candidateDocumentsScanned`, `identityMatched`, `amountExtracted`, `dateExtracted`, `belowConfidence`, `duplicates`, `saved`, `identityScoreBuckets`, `amountSourceCounts`, `dateResolutionPathCounts`, `dateSourceCounts`, `skipReasons`, `sampleSkips`, `sampleCreated`)
  - `skipReasons` may include `date_missing` (no usable date signal found) and `below_confidence` (date found but filtered by `minConfidence`).
- `equityDiagnostics`: per-run equity detection diagnostics (`split` and `dividend` parser-path counters, inserts/updates, and failure reason if SEC fetch fails).
- `validationReport` (only when `validateWithYfinance=true`): diagnostics-only mismatch report with taxonomy `missing_in_sec`, `extra_in_sec`, `date_drift`, `amount_drift`.
- `priceValidationReport` (only when `validateWithYfinance=true`): the adjusted-price comparison for the ticker (same shape as `/admin/validate-adjusted-prices`).
- `snappedActions`: count of corporate actions whose effective date fell on a non-trading day and was applied on the next trade date.

When batch mode is used, response may include:
- `etfDiagnosticsSummary`: aggregate ETF diagnostics across scanned fund tickers with the same fields plus `fundTickersScanned`.
- `equityDiagnosticsSummary`: aggregate equity parser-path counters and reconciliation activity across scanned equity tickers.
- `validationSummary` (only when `validateWithYfinance=true`): aggregate mismatch totals and sample mismatch rows.
- `priceValidationSummary` (only when `validateWithYfinance=true`): aggregate adjusted-price comparison (`tickersOverThreshold`, `worstTickers`, `sampleBreaks`).
- `failedTickers`, `totalSnappedActions`, `scheduledDetections`, `jumpTriggeredDetections`: per-run adjustment/re-detection counters.
| `GET` | `/admin/summarize-filings?ticker=X` | Fetch 10-K filings from SEC EDGAR, extract MD&A, and generate LLM summaries. Omit `ticker` for all equities. Requires llama.cpp server running. |
| `GET` | `/admin/test` | Debug: fetch raw financial facts for Apple Inc. (CIK 320193). |

## 📝 Filing Summaries

### List Filing Summaries

`GET /filing-summaries?ticker={TICKER}`

Returns LLM-generated summaries of 10-K filings for a specific ticker.

**Parameters:**
- `ticker` (Required): Stock ticker symbol (e.g., `AAPL`). Max 10 chars, uppercase.

**Response:**
```json
{
  "ticker": "AAPL",
  "summaries": [
    {
      "filingDate": "2024-10-31",
      "accessionNumber": "0000320193-24-000123",
      "summary": "Apple reported strong revenue growth..."
    }
  ]
}
```

Summaries are generated from the Management's Discussion and Analysis (Item 7) section of each 10-K filing using a local Qwen 2.5-7B model via llama.cpp.

# API Reference

The backend is split across two services:
- **Django** (REST Framework) -- base URL for local development: `http://localhost:8000`
- **Spring Boot** (SEC EDGAR microservice) -- base URL for local development: `http://localhost:8080`

## 🔐 Authentication (Users)

All protected endpoints require a valid JWT Access Token in the header:
`Authorization: Bearer <access_token>`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/users/api/users/` | Register a new user. |
| `POST` | `/users/api/token/` | Login - Obtain Access and Refresh tokens. |
| `POST` | `/users/api/token/refresh/` | Refresh an expired Access token. |
| `POST` | `/users/api/send-email/` | Send system emails (e.g., password reset). |

## 📈 Portfolio (Assets)

Management of financial assets and retrieving market data.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/portfolio/api/assets/` | List all assets for the authenticated user. |
| `POST` | `/portfolio/api/assets/` | Track a new asset. |
| `GET` | `/portfolio/api/assets/<id>/` | Retrieve details of a specific asset. |
| `DELETE` | `/portfolio/api/assets/<id>/` | Remove an asset from portfolio. |
| `GET` | `/portfolio/api/asset-info/` | Fetch live metadata for a ticker (e.g., name, sector). |
| `GET` | `/portfolio/api/asset-prices/` | Get historical price data for charting. |
| `GET` | `/portfolio/api/quote/` | Get the latest price quote. |
| `GET` | `/portfolio/api/fred-data/` | Fetch economic data from Federal Reserve API. |

## 🍽 Restaurants (Reviews)

Social feature for reviewing and sharing restaurant experiences.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/restaurants/api/restaurant-list-create/` | List or create restaurants. |
| `GET` | `/restaurants/api/review-list/` | List all reviews. |
| `POST` | `/restaurants/api/review-create/` | Submit a new review. |
| `GET` | `/restaurants/api/review/<id>/` | View a specific review. |
| `PUT` | `/restaurants/api/review-update/<id>/` | Edit an existing review. |
| `GET` | `/restaurants/api/yelp-load/` | Load data from Yelp API. |
| `GET` | `/restaurants/api/restaurant-recommend/` | Get recommendations based on user history. |

## 🤖 Chatbot

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/chatbot/api/chatbot/` | Send a message to the AI investing assistant. |

## 📊 Indexes

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/indexes/api/index_members/` | List members of tracked market indexes. |
| `PUT` | `/indexes/api/index_members_update/<pk>/` | Update index member data. |

---

# Spring Boot -- SEC EDGAR Microservice

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

## 🔧 Admin Endpoints

All admin endpoints require the `X-Admin-Key` header.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/admin/load` | Load all US tickers from NASDAQ + SEC into the database. |
| `GET` | `/admin/sync-frames` | Sync XBRL frame data from SEC (all frames since 2009). |
| `GET` | `/admin/load-prices` | Load IEX daily OHLCV CSV files from the configured data directory. |
| `GET` | `/admin/load-hist?days=N` | Download IEX HIST TOPS PCAPs, parse trades, and insert raw OHLCV into DB. Default 252 days (~1 year). Does **not** trigger price adjustments. |
| `GET` | `/admin/adjust-prices?ticker=X&force=false` | Detect splits/dividends from SEC and apply adjustment factors to OHLCV prices. Omit `ticker` to adjust all. Set `force=true` to re-fetch SEC data for all tickers (catches new splits/dividends). |
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

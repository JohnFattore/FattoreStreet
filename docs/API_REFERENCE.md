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

## 🔧 Admin Endpoints

All admin endpoints require the `X-Admin-Key` header.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/admin/load` | Load all US tickers from NASDAQ + SEC into the database. |
| `GET` | `/admin/sync-frames` | Sync XBRL frame data from SEC (all frames since 2009). |
| `GET` | `/admin/test` | Debug: fetch raw financial facts for Apple Inc. (CIK 320193). |

# API Documentation

This document outlines the available API endpoints for the SecFilingsAPI.

## Admin Endpoints

These endpoints are used for administrative tasks such as loading data into the database.

### Load S&P 500 Tickers
`GET /admin/load`
Downloads the list of Nasdaq and other listed tickers, filters them for S&P 500 companies, and populates the `assets` and `listings` tables.

### Update All Financials
`GET /admin/quarters`
Fetches the latest financial data for all S&P 500 companies stored in the database by calling the SEC EDGAR API.

### Sync XBRL Frames
`GET /admin/sync-frames`
Syncs bulk data from the SEC XBRL Frames API.

**Parameters:**
- `period` (Optional): The period to sync (e.g., `CY2023Q4`).
- `year` (Optional): The year to sync (e.g., `2023`).
- `full` (Optional, Default: `false`): If `true`, syncs all frames since 2009.

### Debug Load Test
`GET /admin/test`
A debug endpoint that fetches and returns raw financial facts for Apple Inc. (CIK 320193).

---

## Data Endpoints

These endpoints provide access to the processed financial data.

### List Quarters
`GET /quarters?ticker={ticker}`
Returns a detailed list of all stored quarters for a specific ticker.

**Parameters:**
- `ticker` (Required): The stock ticker symbol (e.g., AAPL).

**Returns:**
A JSON object containing the ticker, CIK, and an array of `quarters` including:
- `year`, `quarter`, `periodStart`, `periodEnd`
- **Income Statement**: `revenues`, `netIncomeLoss`, `operatingIncomeLoss`, `grossProfit`, `epsBasic`, `epsDiluted`
- **Balance Sheet**: `assets`, `liabilities`, `equity`, `cash`, `receivables`, `inventory`
- **Cash Flow**: `ocf`, `dividends`, `buybacks`

### Company Fact Sheet
`GET /company-fact-sheet?ticker={ticker}`
Provides a summary of trailing twelve months (TTM) performance and the latest balance sheet status.

**Parameters:**
- `ticker` (Required): The stock ticker symbol (e.g., AAPL).

**Returns:**
- `ticker`: The company ticker.
- `cik`: The Central Index Key.
- `ttmNetIncome`, `ttmRevenue`, `ttmOperatingCashFlow`, `ttmOperatingIncome`, `ttmGrossProfit`
- `ttmNetIncomeYoY`, `ttmRevenueYoY`: Percentage growth (e.g., "15.50%").
- `latestAssets`, `latestLiabilities`, `latestEquity`, `latestInventory`, `latestCash`, `latestEps`
- `netMargin`, `grossMargin`, `roA`, `debtToAssets`, `cashToLiabilities`: Calculated ratios.
- `ocfToNetIncome`: Operating Cash Flow / Net Income.
- `latestQuarterEnd`: Date of the most recent quarter (YYYY-MM-DD).
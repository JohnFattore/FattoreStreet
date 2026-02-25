# React Front End

SPA built with [React 18](https://react.dev/) + [TypeScript](https://www.typescriptlang.org/) + [Vite](https://vitejs.dev/).

## Stack

| Concern | Library |
|---------|---------|
| Build & dev server | Vite |
| Language | TypeScript (strict mode) |
| Routing | React Router v6 |
| State management | Redux Toolkit + RTK Query |
| State persistence | redux-persist |
| HTTP client | Axios (interceptors for JWT refresh) + RTK Query `axiosBaseQuery` |
| Styling | React Bootstrap 5 + custom Sass theme (`custom.scss`) |
| Forms | react-hook-form + yup validation |
| Charts | Recharts |
| Maps | Leaflet |
| Testing | Vitest + React Testing Library + MSW |

## Pages & Routes

| Route | Page | Description |
|-------|------|-------------|
| `/` | Home | Landing page with feature cards |
| `/portfolio` | Portfolio | Holdings overview, account balances, pie charts |
| `/watchlist` | WatchList | Live price table with benchmarks |
| `/asset/:ticker` | AssetView | Individual asset detail with equity/ETF info |
| `/account/:id` | AccountView | Account holdings breakdown |
| `/sec-edgar/:ticker` | SECData | SEC EDGAR financials, quarterly comparison, 10-K filing summaries |
| `/iex-prices/:ticker` | IexPricesView | IEX daily OHLCV prices, IEX vs YFinance comparison |
| `/visualizer` | Visualizer | Chart comparison tool |
| `/economic-indicators` | EconomicIndicators | FRED macroeconomic data charts |
| `/chatbot` | Chatbot | Boglehead AI financial advisor |
| `/restaurants` | Restaurants | Restaurant reviews and map |
| `/entertainment` | Entertainment | Music and media |
| `/user` | User | User profile and settings |
| `/react-admin` | Admin | Admin panel for triggering backend jobs |

## Key Components

| Component | Used on | Purpose |
|-----------|---------|---------|
| `FilingSummaries` | SECData | Expandable table of 10-K MD&A summaries from LLM |
| `QuarterlyComparison` | SECData | Side-by-side SEC vs YFinance quarterly data |
| `YFinanceQuartersTable` | SECData | YFinance quarterly financials table |
| `PriceComparison` | IexPricesView | IEX vs YFinance daily close comparison |
| `SortableTable` | Multiple | Reusable sortable table with column config |
| `AccountList` | Portfolio | Accounts with calculated balances |
| `WatchListTable` | WatchList | Live-updating price grid |

## API Layer (RTK Query)

All API calls go through a single RTK Query API slice (`src/functions/api.ts`) using a custom `axiosBaseQuery`. Endpoints are split across two backends:

- **Django** (`VITE_APP_DJANGO_PORTFOLIO_URL`): assets, accounts, quotes, asset-prices, FRED data, quarterly data, asset-info
- **Spring Boot** (`VITE_APP_SPRINGBOOT_URL`): SEC EDGAR fact sheets, quarters, IEX prices, filing summaries

The `transformResponse` functions handle snake_case → camelCase conversion.

## Styling

React Bootstrap with a custom Sass theme using CSS custom properties (`--primary`, `--secondary`, `--tertiary`, `--quaternary`) and dark-mode support.

```bash
# Compile once
sass src/styles/custom.scss src/styles/custom.css

# Watch mode (preferred)
sass --watch src/styles/custom.scss:src/styles/custom.css
```

## Development

```bash
npm run dev        # local dev server
npm run staging    # staging mode
npm run build      # production build (tsc + vite)
```

## Testing

```bash
npx vitest --run   # single run
npx vitest         # watch mode
npm run test       # watch + UI + coverage
```

Tests use Vitest + React Testing Library + MSW. MSW handlers live in `__tests__/mocks/handlers.ts`. Test utilities (`renderWithProviders`, `createTestStore`) are in `__tests__/testutils.tsx`.

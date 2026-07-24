# React Front End

SPA built with [React 18](https://react.dev/) + [TypeScript](https://www.typescriptlang.org/) + [Vite](https://vitejs.dev/).

## Stack

| Concern            | Library                                                                       |
| ------------------ | ----------------------------------------------------------------------------- |
| Build & dev server | Vite                                                                          |
| Language           | TypeScript (strict mode)                                                      |
| Routing            | React Router v6                                                               |
| State management   | Redux Toolkit + RTK Query                                                     |
| State persistence  | redux-persist                                                                 |
| HTTP client        | Axios (interceptors for JWT refresh) + RTK Query `axiosBaseQuery`             |
| Styling            | React Bootstrap 5 + custom Sass theme (`custom.scss`)                         |
| Forms              | react-hook-form + yup validation                                              |
| Charts             | Recharts                                                                      |
| Maps               | Leaflet                                                                       |
| Testing            | Vitest + React Testing Library + MSW                                          |
| Linting            | ESLint 10 + typescript-eslint v8 (aligned with the TypeScript version in use) |
| Formatting         | Prettier 3 (all tracked files) + Stylelint 17 (css/scss, standard-scss)       |

## Pages & Routes

| Route                      | Page               | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| -------------------------- | ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/`                        | Home               | Landing page with feature cards                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `/portfolio`               | Portfolio          | Holdings overview, account balances, pie charts                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `/watchlist`               | WatchList          | Live price table with benchmarks                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `/asset/:ticker`           | AssetView          | Individual asset detail with equity/ETF info                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `/account/:id`             | AccountView        | Account holdings breakdown                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `/indexes`                 | Indexes            | Browse computed indexes (e.g. Fattore 50, Fattore 100, Fattore 1000) and view constituents, weights, and key metrics                                                                                                                                                                                                                                                                                                                                                                               |
| `/sec-edgar/:ticker`       | SECData            | SEC EDGAR financials, quarterly comparison, 10-K filing summaries                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `/iex-prices/:ticker`      | IexPricesView      | IEX daily adjusted OHLCV prices plus side-by-side adjusted price, dividend, and split comparisons vs YFinance                                                                                                                                                                                                                                                                                                                                                                                      |
| `/react-admin/success-bar` | AdminSuccessBar    | Per-ticker corporate-action success overview comparing Spring Boot vs YFinance price/dividend alignment using persisted manual ticker list                                                                                                                                                                                                                                                                                                                                                         |
| `/visualizer`              | Visualizer         | Chart comparison tool                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `/economic-indicators`     | EconomicIndicators | FRED macroeconomic data charts (served by Spring Boot `POST /fred-data`) with chart-level latest readings (date + value)                                                                                                                                                                                                                                                                                                                                                                           |
| `/chatbot`                 | Chatbot            | Boglehead AI financial advisor                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `/restaurants`             | Restaurants        | Restaurant reviews and map                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `/entertainment`           | Entertainment      | Music and media                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `/user`                    | User               | User profile and settings                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `/react-admin`             | Admin              | Admin panel for Spring sec-api jobs: asset load, frame sync, IEX ingest, price adjustments, 10-K summaries, index metrics refresh (`ListingIndexMetrics`), and cap-ranked index rebuild via `POST .../admin/indexes/rebuild` with optional index `code` (`MarketIndex` / `IndexMember`); when not logged in, shows `LoginRequired` with sign-in modal (`LoginModal` + `LoginForm`); Spring calls use the Redux-stored Django JWT (`Authorization: Bearer`); backend allows only Django user id `1` |

## Key Components

| Component                            | Used on                | Purpose                                                                                                                |
| ------------------------------------ | ---------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `FilingSummaries`                    | SECData                | Expandable table of 10-K MD&A summaries from LLM                                                                       |
| `QuarterlyComparison`                | SECData                | Side-by-side SEC vs YFinance quarterly data                                                                            |
| `YFinanceQuartersTable`              | SECData                | YFinance quarterly financials table                                                                                    |
| `PriceComparison`                    | IexPricesView          | IEX vs YFinance daily adjusted-close comparison                                                                        |
| `DividendComparison`                 | IexPricesView          | Internal corporate-action dividends vs YFinance dividends (nearest-date matching)                                      |
| `SplitComparison`                    | IexPricesView          | Internal SEC split events vs YFinance split events (date-window matching with normalized split ratios)                 |
| `AdminSuccessBar`                    | AdminSuccessBar        | Aggregated per-ticker success bars for Spring Boot vs YFinance price/dividend parity                                   |
| `SortableTable`                      | Multiple               | Reusable sortable table with column config                                                                             |
| `IndexMembersTable`                  | Indexes                | Constituent weights with `countryHQ` / `countryIncorp` and `stateHQ` / `stateIncorp` from Spring `ListingIndexMetrics` |
| `Fattore1000Russell1000CompareTable` | Indexes (FAT1000 only) | Ticker-level weights vs bundled IWB file; overlap % and mean absolute weight gap (`GET /iwb-reference-holdings`)       |
| `AccountList`                        | Portfolio              | Accounts with calculated balances                                                                                      |
| `WatchListTable`                     | WatchList              | Live-updating price grid                                                                                               |
| `TicketForm`                         | User                   | Authenticated feedback ticket submission form                                                                          |

## API Layer (RTK Query)

All API calls go through RTK Query API slices using a custom `axiosBaseQuery`. Endpoints are split across two backends:

- **Django** (`VITE_APP_DJANGO_URL`): React derives per-app API bases (`/users/api/`, `/portfolio/api/`, `/restaurants/api/`, `/chatbot/api/`, `/changeflow/api/`, `/blog/api/`) from this single base; endpoints include assets, accounts, quotes, asset-prices, asset-dividends, asset-splits, quarterly data, asset-info, tickets, and blog
- **Spring Boot** (`VITE_APP_SPRINGBOOT_URL`): SEC EDGAR fact sheets, quarters, IEX prices, dividends, splits, filing summaries, indexes (`/indexes`, `/index-members` with nested `stock` including `stateHQ` / `stateIncorp`), IWB reference weights (`/iwb-reference-holdings`), FRED economic data (`POST /fred-data`)

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
npm run dev          # local dev server
npm run staging      # staging mode
npm run build        # production build (tsc + vite)
npm run lint         # ESLint (ts/tsx, max-warnings 0)
npm run lint:styles  # Stylelint (css/scss)
npm run format:check # Prettier, report only
npm run format       # Prettier, rewrite in place
```

Linting is not part of `npm run build`, and there are no local git hooks, so these
run either on demand or in CI. All four checks (`lint`, `lint:styles`,
`format:check`, `build`) must pass before merge.

`src/styles/custom.css` is the compiled output of `custom.scss` and is excluded
from both Prettier and Stylelint; edit the `.scss` source instead.

## Testing

```bash
npx vitest --run   # single run
npx vitest         # watch mode
npm run test       # watch + UI + coverage
```

Tests use Vitest + React Testing Library + MSW. MSW handlers live in `__tests__/mocks/handlers.ts`. Test utilities (`renderWithProviders`, `createTestStore`) are in `__tests__/testutils.tsx`.

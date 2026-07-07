# React: test-suite improvements + dead-code removal

## Context (updated after the RTK Query migration, PR #77)

The thunk→RTK Query migration landed: `axiosFunctions.tsx` and the `reviews`/`restaurants`/`restaurantRecommend`/`chatbot` slices are gone, and it shipped with new tests (`AuthForms.test.tsx`: LoginForm + WatchListForm; `RestaurantComponents.test.tsx`: RestaurantTable + ReviewTable + ReviewForm; rewritten `ChatbotIntegration.test.tsx` incl. the optimistic send flow) plus MSW handlers for token-refresh, chatbot, review mutations, and recommendations. That obsoleted the old plan's thunk/reducer state tests (old steps 2–4) and most of the restaurants flows (old step 8).

Fresh coverage run: **63.5% statements / 53.2% branches** (was 57/48). Remaining weak spots:

| Area | Coverage | Notes |
|---|---|---|
| `functions/api/springbootApi.ts` | 19% | admin endpoints + `getSecEdgarDataBatch`/`adminAssetLoad` queryFns untested |
| `pages/Admin.tsx` | 39% | job cards, param plumbing, `formatError` |
| `pages/AssetView.tsx`, `pages/Entertainment.tsx` | 0% | + all `components/entertainment/*` at 0% |
| `WatchListTable.tsx` 26%, `YFinanceQuartersTable.tsx` 15% | | |
| `restaurants/ReviewMap.tsx` | 0% | needs leaflet mock |
| `AssetSoldTable.tsx` | 0% | AssetView child |
| `pages/AdminSuccessBar.tsx` 46%, `EconomicIndicators.tsx` 51% | | |
| `RegisterForm.tsx` 73% | submit path (postUser→login chain) untested | |
| reducers: `locationReducer` 43%, `userReducer` 53%, `watchListReducer` 76%, `adminSuccessBar` 75% | | user-slice matchers only partially exercised |

Tiny 0% components (`LogoutButton`, `ErrorPage`, `CompanyLogo`, `ETFInfo`, `EquityInfo`, `TickerHeader`): cover through parent flows where natural; don't chase them individually.

Stack per `.cursor/skills/react-tests/SKILL.md`: Vitest + RTL + MSW + jsdom; tests in `react-app/__tests__/`; `renderWithProviders`/`createTestStore` in `__tests__/testutils.tsx`; handlers in `__tests__/mocks/handlers.ts`. Run with `cd react-app && npx vitest --run`; coverage via `npx vitest run --coverage`.

### Source quirks tests must pin (verified — flag, don't fix)
1. `RatioTable.tsx:28-32` headers are copy-pasted from AlbumTable ("Name/Artist/Year") — assert ratio *content*, not headers.
2. Chatbot optimistic update (`djangoApi.ts` `postChatbot.onQueryStarted`) no-ops if the `getChatbot` cache hasn't fulfilled yet — tests must await the history render before submitting (pattern already in `ChatbotIntegration.test.tsx`).
3. `SortableTable` sorts ascending by the first column on mount; the first header click toggles to *descending* (pattern already in `RestaurantComponents.test.tsx`).
4. The recommendations feature (`RestaurantRecommend`, `RestaurantRecommendTable`) is migrated but dormant — commented out in `Restaurants.tsx` by user decision. Test the components directly, not via the page.

## Step 0 — Promote `renderWithRoute` into `__tests__/testutils.tsx`

Move the local helper from `Pages.test.tsx:31-45` into `testutils.tsx`, adding `extraRoutes?: { path: string; element: ReactElement }[]` so navigation is asserted with probe routes (e.g. `/asset/:ticker` → `<div>asset-probe</div>`) instead of mocking react-router. Update `Pages.test.tsx` to import it.

## Step 1 — Spring Boot admin MSW handlers in `__tests__/mocks/handlers.ts`

Text responses via `new Response("...", {status:200})`; echo `new URL(request.url).search` in the body so param assertions read the success Alert:
- GET `admin/asset-load` → `"asset-load ok" + search`; GET `admin/load` → `"legacy load ok"`; GET `admin/sync-frames`; GET `admin/load-hist` + search; GET `admin/adjust-prices` + search; GET `admin/summarize-filings` + search; POST `admin/indexes/refresh-stocks` + search; POST `admin/indexes/rebuild` + search.

(Chatbot, review-mutation, recommendations, and token-refresh handlers already exist.) Error variants stay per-test `server.use(...)` overrides.

## Step 2 — `__tests__/StateSlices.test.tsx` (slimmed-down state coverage)

Pure dispatch (the `AdminSuccessBarState.test.tsx` pattern):
- `userReducer`: `logout` clears username/access/refresh but **not** `darkMode`; `setUserDarkMode`.
- `locationReducer`: `setLocation` replaces state/city.
- `watchListReducer`: `loadTickers` seeds `["VTI","SPY"]` into empty localStorage and reads an existing value; `removeTicker` updates state + localStorage (add/error/clear already covered via WatchListForm).
- `adminSuccessBarReducer`: remaining branches (`removeTicker`, `clearError`, invalid-format `setValidationError` path).

Matcher coverage via real mutations against MSW (`createTestStore()` + `store.dispatch(djangoApi.endpoints.X.initiate(...))`):
- `refreshLogin` fulfilled → access replaced, refresh kept; rejected (override handler → 401) → access **and** refresh cleared.
- `login` rejected → refresh cleared. (login fulfilled already covered by `AuthForms.test.tsx`.)

## Step 3 — `__tests__/Admin.test.tsx`

Auth gates: no access → "Sign in to React Admin"; wrong username → error state; spike → welcome.
Per-job tests (assert success Alert text = handler echo):
- Asset Load: success; overwrite switch → `overwriteExisting=true`; **404 fallback** — override `admin/asset-load` → 404, expect `"legacy load ok"` (covers the `adminAssetLoad` queryFn fallback in `springbootApi.ts`); both-fail → danger Alert.
- `formatError` 401 branch: override `admin/sync-frames` → 401 → Alert matches `/SEC API returned 401/` (`Admin.tsx:18-23`).
- Sync Frames success; Load Hist `days=30`; Adjust Prices `ticker=AAPL&force=true&etfOnly=true&minConfidence=70` + ETF/Equity switch mutual exclusion; Refresh index metrics — default sends no params, scope=all, ticker wins + disables scope select; Rebuild indexes `code=FAT100&refreshMetrics=true` and default.
- Navigation to success bar via probe route; optional loading-state test with msw `delay`.

## Step 4 — `__tests__/AssetView.test.tsx` + Entertainment in `Pages.test.tsx`

AssetView (`renderWithRoute`, path `/asset/:ticker`, entry `/asset/AAPL`): missing-ticker Alert; loading modal "Loading AAPL Data..."; happy path (TickerHeader "Apple Inc.", asset rows incl. `AssetSoldTable`, chart card label — never SVG internals); error path (override `asset-info/` → 500 `{detail}`); navigation buttons via probe routes.
Entertainment (static, add to `Pages.test.tsx`): section headings; a link href from `ExternalLinks`; ratio content rows (quirk 1); album rows.

## Step 5 — `__tests__/WatchListComponents.test.tsx`

WatchListTable (props-driven; preload `watchList.tickers`): performance view formatting (`$500.00`, `2.00%` via `formatString`); Remove → store tickers shrink + localStorage; View → probe route; "SEC Edgar" toggle → batch query renders rows (override `company-fact-sheet` keyed off `?ticker=`); batch 404-filter branch (one ticker fails → one row, no crash — covers the `getSecEdgarDataBatch` queryFn filter); loading Spinner / error Alert passthrough.
YFinanceQuartersTable: happy path ("2024 Q4", `$124.30 Billion`, `$2.41`); null-fields row (pins `?? undefined` branches); error Alert.

## Step 6 — Remaining flows: `__tests__/RestaurantsFlows.test.tsx` + RegisterForm

- ReviewMap: module-scope `vi.mock("leaflet", ...)` (map/tileLayer/marker/AwesomeMarkers.icon returning chainable spies) + `vi.mock("leaflet.awesome-markers", ...)` stub; assert `L.map('map')` and `bindPopup("Mike's Ice Cream: 3")`; recommendations markers stay absent until the recommendations cache is primed (it reads `useQueryState`, never fetches).
- RestaurantRecommend (dormant, tested directly): empty state button; click → loading → "Recommended Restaurants" + Sonic row (uses the existing `restaurant-recommend/` handler); error override → Alert.
- LocationForm: submit dispatches `setLocation` (assert store), yup required errors.
- Restaurants page: logged-out gate (`LoginRequired` + public RestaurantTable); "Show Map" toggle.
- RegisterForm (add to `AuthForms.test.tsx`): password-mismatch yup error (`role="password2Error"`); successful submit chains postUser→login (store gains tokens, form disappears); postUser 400 override (`{username: [...]}`) → Alert.

## Step 7 — Dead-code deletion (last, guarded by the full suite)

Verified-unused (grep shows only definitions/re-exports):
1. Delete `src/components/entertainment/Fees.tsx`, `SpikeHeadImg.tsx`, and `src/images/spike_head.png` (only SpikeHeadImg imports it).
2. `djangoApi.ts`: remove `getAsset`, `getBlogCategories`, `getBlogTags` endpoints + their hook exports (here and in `functions/api/index.ts`). Keep `IBlogTaxonomy`/`IRawAsset` (still used).
3. Remove unused hook exports introduced by the migration (endpoints stay): `useRefreshLoginMutation` (App.tsx uses `initiate` directly), `useGetRestaurantRecommendationsQuery` (only the lazy variant is used).
4. **Decision to flag**: `patchReview` has zero callers anywhere (the old ReviewTable "update" column was already dead). Either delete the endpoint + its MSW handler + hook export, or keep for wire parity — ask the user; default to deleting.

## Verification

```bash
cd /Users/spike/GitHub/FattoreStreet/react-app
npx vitest --run              # full suite green
npx vitest run --coverage     # springbootApi/Admin/AssetView/Entertainment/WatchListTable/QuartersTable/ReviewMap rise materially (overall 63.5% → ~75%+)
npm run lint                  # max-warnings=0
npm run build                 # tsc proves dead-code removal left no dangling references
```

## Gotchas

- `vi.clearAllMocks()` in setupTests afterEach clears leaflet spy call history between tests — assert within each test.
- RTK Query resolves fast against MSW; for spinner assertions use `server.use` + msw `delay`, no fake timers.
- react-hook-form + yup validation is async — `findBy*`/`waitFor` after submit.
- Each test gets a fresh store from `createTestStore` — no `resetApiState` needed; caches never leak across tests.
- MSW handler base URLs come from `import.meta.env.VITE_APP_DJANGO_URL` / `VITE_APP_SPRINGBOOT_URL` (pinned in `vitest.config.ts`); the react-tests SKILL.md mention of `VITE_APP_DJANGO_PORTFOLIO_URL` is stale — follow the code.

# Contract: HTTP API Changes

Spring Boot's external HTTP surface after this feature. All changes are removals, and every
removal is under `/admin/`.

## Removed: every `/admin/**` route

| Method | Path | Query params | Was gated by |
|---|---|---|---|
| GET | `/admin/asset-load` | `overwriteExisting` | `hasRole("ADMIN")` |
| GET | `/admin/test` | — | `hasRole("ADMIN")` |
| GET | `/admin/load-hist` | `days` | `hasRole("ADMIN")` |
| GET | `/admin/adjust-prices` | `ticker`, `force`, `etfOnly`, `equityOnly`, `validateWithYfinance` | `hasRole("ADMIN")` |
| GET | `/admin/validate-adjusted-prices` | `ticker`, `minDate` | `hasRole("ADMIN")` |
| GET | `/admin/summarize-filings` | `ticker` | `hasRole("ADMIN")` |
| GET | `/admin/sync-frames` | — | `hasRole("ADMIN")` |
| POST | `/admin/indexes/refresh-stocks` | `year`, `scope`, `ticker` | `hasRole("ADMIN")` |
| POST | `/admin/indexes/rebuild` | `refreshMetrics`, `year`, `code` | `hasRole("ADMIN")` |
| POST | `/admin/indexes/rebuild-fattore-50` | `refreshMetrics`, `year` | `hasRole("ADMIN")` |
| POST | `/admin/indexes/rebuild-fattore-100` | `refreshMetrics`, `year` | `hasRole("ADMIN")` |
| POST | `/admin/indexes/rebuild-fattore-1000` | `refreshMetrics`, `year` | `hasRole("ADMIN")` |

After removal, any request to `/admin/*` returns **404**, not 401/403 — the security matcher
goes away with the routes.

**Breaking-change assessment**: none externally. The sole caller is the React admin page,
deleted in the same change. These routes were never public API.

## Unchanged public routes

Every public route keeps its exact current behavior, response shape, and `permitAll` status:

`GET /quarters`, `GET /company-fact-sheet`, `GET /prices`, `GET /dividends`, `GET /splits`,
`GET /filing-summaries`, `GET /indexes`, `GET /index-members`,
`GET /iwb-reference-holdings`, `POST /fred-data`

### `GET /filing-summaries` — retained deliberately

Earlier drafts removed this alongside the summarization feature. Revised: only the *generator*
is retired. The endpoint, its `permitAll` matcher, its repository, and its React consumer all
stay exactly as they are.

`PublicController.filingSummaries` reads via
`FilingSummaryRepository.findByTickerOrderByFilingDateDesc(ticker)` and touches nothing being
deleted, so it requires no code change.

**Behavioral note, not a contract change**: with no generator, the underlying table stops
growing. A ticker whose 10-K post-dates the last generation run returns
`{"ticker": "X", "summaries": []}`. That response shape is already what the endpoint returns
for an unsummarized ticker today — the contract is unchanged, only the data is frozen.

## Authentication surface

**Before**: `/admin/**` required `Authorization: Bearer <Django SimpleJWT access token>` with
claim `user_id = 1`, verified HS256 against `SECRET_KEY`.

**After**: no authenticated route exists anywhere on the service. The resource-server
configuration is removed entirely, along with Spring Boot's `SECRET_KEY` dependency
(`research.md` §7). The retained `SecurityFilterChain` keeps only CORS, CSRF-disable, and
stateless session policy.

An `Authorization` header on any request is now simply ignored rather than parsed.

### Contract tests

1. `/admin/asset-load` with a valid admin JWT returns **404**. Asserts the route is genuinely
   gone rather than merely unguarded.
2. Every public route above returns its normal response with **`SECRET_KEY` unset** in the
   environment (SC-007). The old `jwtDecoder` bean threw `IllegalStateException` on a blank
   secret at startup; that failure mode must be gone, not merely unreachable.
3. `GET /filing-summaries?ticker=AAPL` returns 200 with the same shape as before the change.

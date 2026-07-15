# Move fred-data endpoint from Django to Spring Boot

## Context

The `/portfolio/api/fred-data/` endpoint (FRED economic observations powering the Economic Indicators page) currently lives in Django: `FredDataRetrieveView` (`django/portfolio/views.py:201`) loops over requested series and calls `get_fred_data` (`django/portfolio/helper.py:166`), which hits `https://api.stlouisfed.org/fred/series/observations` and caches results in Redis for 24h. Spring Boot is the service that already owns external market data (SEC, IEX), so FRED belongs there. This change ports the endpoint to Spring Boot, points React at it, and removes the Django implementation. FRED is a preferred commercially-free source per `.claude/rules/data-licensing-commercial-free.md`; data stays cache-only (never persisted), so no licensing concerns.

## API contract (Spring Boot)

`POST /fred-data` (public, no auth) — mirrors the Django shape but with camelCase request keys per Spring Boot conventions:

- Request body: `[{ "seriesId": "CPIAUCSL", "computeYoy": true }, ...]` (`computeYoy` defaults to false)
- Behavior: for each item, fetch FRED observations (`units=pc1` when `computeYoy`), drop FRED's `"."` missing-value markers, sort by date
- Response: `{ "<seriesId>": [{ "date": "1962-01-02", "value": 4.06 }, ...], ... }` (unchanged from Django, so charts need no changes)

## Spring Boot changes (`springboot/`)

1. **`src/main/resources/application.properties`** — add `fred.api-key=${FRED_API_KEY:}`.
2. **New `economic/FredService.java`** (`com.fattorestreet.sec_api.economic`):
   - Constructor injection: `@Value("${fred.api-key}")` plus a package-visible constructor accepting a `RestTemplate` for tests (same pattern as `client/WebService.java`). Do NOT reuse `WebService` — it is SEC-specific (User-Agent, throttling, retries).
   - `public record FredObservation(LocalDate date, double value)` — Boot serializes `LocalDate` as `"yyyy-MM-dd"` by default.
   - `getSeries(String seriesId, boolean computeYoy)`: check an in-memory TTL cache (`ConcurrentHashMap` keyed `seriesId|computeYoy`, 24h TTL, matching Django's Redis TTL — springboot has no Redis; restart-clearing is acceptable), else GET the FRED observations URL with `series_id`, `api_key`, `file_type=json`, and `units=pc1` when yoy; parse JSON with Jackson, skip observations whose `value` is not numeric (FRED uses `"."` for missing), sort ascending by date, cache, return.
3. **`controller/PublicController.java`** — add `POST /fred-data`: accepts `List<FredSeriesItem>` (record with `@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9]{0,30}$") String seriesId`, `Boolean computeYoy` treated as false when null), builds a `LinkedHashMap<String, List<FredObservation>>`, returns 200. Controller stays thin; service does the work.
4. **`config/SecurityConfig.java`** — add `"/fred-data"` to the explicit `permitAll` request-matcher list. (CORS in `WebConfig` already covers `/**` with POST.)
5. **Tests** (per springboot-tests skill):
   - `src/test/java/.../economic/FredServiceTest.java` — Mockito with injected mock `RestTemplate`: happy path, `"."` values dropped, `units=pc1` sent only when yoy, cache hit avoids second HTTP call.
   - `controller/PublicControllerTest.java` — add `@MockitoBean FredService` cases: 200 with mapped body, 400 on invalid `seriesId`.

## React changes (`react-app/`)

1. **`src/functions/api/springbootApi.ts`** — add `getFredData` query: `builder.query<Record<string, IFredObservation[]>, { seriesId: string; computeYoy?: boolean }[]>` with `url: "fred-data"`, `method: "POST"`, `data: seriesList`. Export `useGetFredDataQuery`.
2. **`src/interfaces.ts`** — move `IFredObservation` here (from `djangoApi.ts:63`), since springbootApi imports its types from `interfaces.ts`.
3. **`src/functions/api/djangoApi.ts`** — remove `getFredData` endpoint, `IFredObservation`, and the `useGetFredDataQuery` export.
4. **`src/functions/api/index.ts`** — re-export `useGetFredDataQuery` from the springboot block instead of the django block (component import path stays `../functions/api`, so `EconomicIndicators.tsx` only changes its arg shape).
5. **`src/pages/EconomicIndicators.tsx`** — change `seriesList` entries from `{ series_id, compute_yoy }` to `{ seriesId, computeYoy }`.
6. **`__tests__/mocks/handlers.ts`** — move the `fred-data/` mock from `http.post(portfolioApiBaseUrl.concat("fred-data/"), ...)` to `http.post(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("fred-data"), ...)` (same response payload; matches the pattern of the other springboot handlers). The existing `EconomicIndicators` test in `__tests__/render.test.tsx` should then pass unchanged.

## Django changes (`django/`)

Remove the FRED implementation entirely:

1. `portfolio/views.py` — delete `FredDataRetrieveView` and the `FredSeriesItemSerializer` / `get_fred_data` imports.
2. `portfolio/serializers.py` — delete `FredSeriesItemSerializer`.
3. `portfolio/urls.py` — delete the `api/fred-data/` path (watch the trailing comma on the previous entry).
4. `portfolio/helper.py` — delete `get_fred_data` (requests/pandas are still used by other helpers; keep imports that remain in use).
5. Tests — delete `FredDataTests` from `tests/test_helper.py` (and its `get_fred_data` import) and `FredDataTest` from `tests/test_portfolio.py`.

## Docs & env plumbing

- **`CLAUDE.md`** — move `FRED_API_KEY` from the Django env-vars list to the Spring Boot list; drop "FRED economic data" from the `portfolio/` app description; add `economic/` to the Spring Boot package tree.
- **`docs/API_REFERENCE.md:37`** — remove the Django `fred-data` row; add `POST /fred-data` to the Spring Boot section (method, path, body, response shape, note it was previously `/portfolio/api/fred-data/`).
- **`django/README.md`** — remove FRED mentions (lines 12, 19, 39) and `FRED_API_KEY` if listed.
- **`springboot/README.md`** — add FRED economic data to features, `FRED_API_KEY` to the env-vars table, and a short usage example.
- **`react-app/README.md`** — line 36/65: Economic Indicators now consumes the Spring Boot API; remove "FRED data" from the Django endpoint list.
- **`deploy/docker-compose.dev.yml`** — add `FRED_API_KEY` to the springboot service env (dev placeholder with a comment, same style as `GOOGLE_API_KEY` in the django block).
- **`deploy/run.sh`** — production config comes from the single shared `fattorestreet/env` secret exported to both containers, so no launch-command change is needed; add `"FRED_API_KEY"` to the example secret JSON comment so the key is documented.

## Verification

1. `cd springboot && mvn test` — new FredService/PublicController tests plus existing suite green.
2. `cd django && uv run python manage.py test` — suite green after removals.
3. `cd react-app && npm run lint && npx vitest --run && npm run build`.
4. End-to-end: start Spring Boot with a real `FRED_API_KEY` in `springboot/.env` (`mvn spring-boot:run`), then `curl -X POST http://localhost:8080/fred-data -H 'Content-Type: application/json' -d '[{"seriesId":"UNRATE"},{"seriesId":"CPIAUCSL","computeYoy":true}]'` and confirm sorted `{date, value}` arrays; repeat the call to confirm the cached second response is fast. Then `npm run dev` in react-app and load `/economic-indicators` to confirm all charts render (webapp-testing skill if a browser check is wanted).

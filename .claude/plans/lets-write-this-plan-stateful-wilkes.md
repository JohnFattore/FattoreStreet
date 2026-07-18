# Adjusted-Price Accuracy + Nightly Integration Plan

## Context

The Fargate one-shot price load (`HistLoadRunner` → `IexHistService.loadHistData`) writes new `DailyPrice` rows with NULL `adjusted_*` columns. A separate, manual admin job (`GET /admin/adjust-prices` → `PriceAdjustmentService`) detects SEC corporate actions (splits/dividends) and back-computes adjusted prices with the standard CRSP/Yahoo factor method. Today:

1. **Accuracy is measured at the wrong level.** `CorporateActionValidationService` diffs SEC events against yfinance events (via Django reference endpoints), but nothing compares the final `adjustedClose` series to yfinance's adjusted close, which is what users actually see. One missed split makes all earlier prices ~N× wrong and the event diff can still look fine.
2. **Detection goes stale.** With `force=false`, a ticker that already has any corporate actions never re-fetches SEC (`shouldFetchSec` short-circuits), so new splits/dividends are missed until someone runs `force=true`.
3. **Errors get frozen.** On per-ticker exceptions, `adjustAllTickers` writes raw prices into the adjusted columns, which removes the ticker from `findTickersWithUnadjustedPrices` and hides the failure permanently.
4. **Actions on non-trading days are silently dropped.** `applyAdjustments` matches actions to price rows by exact date; a weekend/holiday effective date matches nothing and the split/dividend is never applied.
5. **Adjustment isn't automated.** Until the admin endpoint is run, the newest rows serve NULL `adjustedClose`.

Goal: build a price-level accuracy comparator (yfinance stays dev-only diagnostics per `.claude/rules/data-licensing-commercial-free.md`), fix the structural adjustment bugs, then fold adjustment + a 7-day rolling SEC re-detection into the nightly hist-load job.

Decisions already made with the user:
- Re-detection cadence: **7-day rolling refresh** (per-listing `last_sec_detection_at`, stalest ~1/7 of tickers per nightly run).
- Comparator exposure: **both** a standalone `GET /admin/validate-adjusted-prices` endpoint and inclusion in `/admin/adjust-prices` output when `validateWithYfinance=true`.

All Spring Boot work; **no Django changes needed** (the yfinance reference endpoint `/portfolio/api/asset-prices/` already exists: `AssetHistoricalPricesRetrieveView` in `django/portfolio/views.py`, 25-year window, returns `[{date, value}]` of adjusted closes).

---

## Phase 1 — Adjusted-price comparator (measurement first)

### New: `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/AdjustedPriceValidationService.java`

Mirror the structure/DI of `CorporateActionValidationService` (same `DJANGO_PORTFOLIO_BASE_URL` property, same `HttpClient` + package-private test constructor, same `fetchJsonArray` pattern):

- Fetch yfinance adjusted closes from `{base}/api/asset-prices/?ticker=X`.
- Load our series via `DailyPriceRepository.findByTickerOrderByTradeDateAsc(ticker)` using `adjustedClose` (skip NULLs).
- Intersect on common dates (configurable `minDateInclusive`, default 2016-01-01 to match event validation).
- Compute per-date ratio `r(d) = secAdjClose(d) / yfAdjClose(d)`, normalized by `r(latestCommonDate)` to remove any level offset.
- Report (`PriceValidationReport` record with `toMap()`, like `ValidationReport`):
  - `commonDates`, `meanAbsDeviation`, `maxAbsDeviation` (of `|r-1|`),
  - `datesOverThreshold` (default threshold 0.5%),
  - `breaks`: top N (≤20) dates where the ratio steps day-over-day by more than the threshold, each with date, step magnitude, and nearest stored `CorporateAction` within ±7 days (or "none"). Each break localizes one bad/missing/misdated event.
- `summarizeBatch(List<PriceValidationReport>)` analogous to the event summarizer.

### Wire-up

- `AdminController` (`springboot/.../controller/AdminController.java`):
  - New `GET /admin/validate-adjusted-prices?ticker=&minDate=` (ticker optional; without it, iterate tickers from `dailyPriceRepository.findDistinctTickers()` and return the batch summary).
  - In the existing adjust-prices path, when `validateWithYfinance=true`, `PriceAdjustmentService` adds `priceValidationReport` (single ticker) / `priceValidationSummary` (batch) beside the existing `validationReport` keys.
- Guardrail: this is diagnostics-only, ephemeral, dev-only usage of yfinance. Nothing from the report is persisted; the nightly job never sets `validateWithYfinance`.

## Phase 2 — Correctness fixes in `PriceAdjustmentService`

File: `springboot/.../corporateaction/PriceAdjustmentService.java`, method `applyAdjustments` + `adjustAllTickers`.

1. **Snap actions to trading days.** Before grouping `actionsByDate`, remap each action's `effectiveDate` to the first trade date `>=` it (binary search over the ascending price dates). Actions dated after the last trade date stay pending (no-op). Log at debug when snapping occurs.
2. **Stop freezing failures.** In the `adjustAllTickers` catch block, remove the `setRawAsAdjusted(ticker)` call for exceptions (leave adjusted columns NULL so the ticker is retried next run). Keep `setRawAsAdjusted` only for the genuine no-SEC-asset path. Downstream already tolerates NULL `adjustedClose` (`IndexMetricsRefreshService` falls back to `closePrice`).
3. Track and return a `snappedActionDates` / `failedTickers` count in the batch summary map so nightly logs surface both.

## Phase 3 — Rolling re-detection + nightly integration

### 3a. `last_sec_detection_at` on Listing

- Flyway migration `springboot/src/main/resources/db/migration/V2__listing_last_sec_detection_at.sql`: `ALTER TABLE listings ADD COLUMN last_sec_detection_at timestamp NULL;` (match V1's naming/types; `ddl-auto=validate` so the migration is required).
- Add the field + accessors to `Listing` entity (`springboot/.../model/Listing.java`).
- Stamp it (via `ListingRepository`) after each successful `detectAndPersist*` call for that ticker, in both `adjustTicker` and `adjustAllTickers`.

### 3b. Staleness-driven `shouldFetchSec`

In `PriceAdjustmentService.adjustAllTickers` (and mirrored in `adjustTicker`):

- `shouldFetchSec = force || !hasExistingActions || stale`, where `stale` = `last_sec_detection_at` NULL or older than 7 days (constant, e.g. `SEC_REDETECTION_INTERVAL_DAYS = 7`).
- Add stale tickers to `tickersToProcess` (today only NULL-adjusted tickers are processed without force).
- **Per-run cap** so the first deploy (all NULLs) doesn't scan everything: order stale candidates by oldest `last_sec_detection_at` (NULLs first) and re-detect at most `ceil(totalTickers / 7)` per run; the rest keep their existing actions and are still re-adjusted. This converges to a weekly rolling refresh.
- Keep the existing `Thread.sleep(100)` SEC throttle.

### 3c. Hook into the hist-load one-shot

File: `springboot/.../marketdata/HistLoadRunner.java`:

- After a successful `loadHistData`, call `priceAdjustmentService.adjustAllTickers(false)` (never `validateWithYfinance` here) behind a property `app.hist-load.adjust-enabled` (default `true`) for an escape hatch.
- Log the summary (tickers processed, splits/dividends, prices updated, failed tickers).
- Exit codes: load failure keeps current semantics; adjustment throwing (or reporting zero processed with failures) exits `1` so the Fargate task is marked failed, since both steps are idempotent and retry next night.
- No Terraform changes required (same task, same schedule); bump nothing in `springboot/deploy/terraform/` besides possibly the README.

## Tests (per auto-update-tests rule, mirror existing patterns)

- New `AdjustedPriceValidationServiceTest` modeled on `CorporateActionValidationServiceTest` (mocked `HttpClient`): exact-match series → zero deviation; injected missing-split series → one break at the split date; NULL adjusted rows skipped.
- `PriceAdjustmentServiceTest`: weekend `effectiveDate` gets applied on next trade date; exception path leaves adjusted NULL (no `setRawAsAdjusted`); staleness logic (fresh timestamp → no SEC fetch, 8-day-old → fetch, per-run cap respected).
- `HistLoadRunnerTest`: verify `adjustAllTickers(false)` runs after a successful load, is skipped when `app.hist-load.adjust-enabled=false`, and exit-code behavior on adjustment failure.
- Admin controller test for the new endpoint (follow existing `AdminController` test conventions: `@WebMvcTest` + `@MockitoBean` + JWT via `TestJwtTokens`).
- Repository test only if a new query method is added to `ListingRepository`.

## Docs (per auto-update-docs rule)

- `springboot/README.md`: new admin endpoint, `app.hist-load.adjust-enabled`, note that the nightly job now adjusts + rolling-refreshes detection.
- `docs/API_REFERENCE.md`: `GET /admin/validate-adjusted-prices`.
- Root `CLAUDE.md` scheduled-jobs note: hist-load task now also runs price adjustment.
- `springboot/deploy/terraform/README.md` if it describes the job's behavior.

## Verification

1. `cd springboot && mvn test` (all new + existing tests; JaCoCo floor must hold).
2. End-to-end local run: start Django (`uv run python manage.py runserver`) and Spring Boot (`mvn spring-boot:run`), then:
   - `GET /admin/validate-adjusted-prices?ticker=AAPL` → report with near-zero deviation expected for a well-covered ticker; pick one known-bad ticker and confirm `breaks` line up with a real event date.
   - `GET /admin/adjust-prices?ticker=AAPL&validateWithYfinance=true` → both event and price reports present.
3. One-shot mode locally: `APP_RUN_MODE=hist-load mvn spring-boot:run` against the local DB; confirm load → adjust runs, logs the summary, exits 0, and `adjusted_close` is non-NULL for the latest trade date.
4. Flyway: boot against an existing local Postgres and confirm `V2` applies and `ddl-auto=validate` passes.

## Suggested execution order

Phase 1 first (measurement), snapshot baseline deviation for a canary list (reuse the canary ideas in `.claude/skills/sec-equity-dividend-accuracy-pass/SKILL.md`), then Phase 2, re-measure, then Phase 3. Detection-quality tuning (ex-date heuristics, ETF extraction) stays out of scope here; it continues via the existing accuracy-pass skills, now with the price-level KPI.

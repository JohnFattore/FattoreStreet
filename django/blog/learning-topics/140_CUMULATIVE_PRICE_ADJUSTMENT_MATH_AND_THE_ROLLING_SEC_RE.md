# Cumulative price adjustment math and the rolling SEC re-detection scheduler

_FattoreStreet @ [`60f885ad`](https://github.com/JohnFattore/FattoreStreet/tree/60f885ade8bc9525d4d665ee7a0f5c7bc566b219) — 2026-07-26_

_Source: [#140](https://github.com/JohnFattore/FattoreStreet/issues/140)_

## Overview

`PriceAdjustmentService` in the Spring Boot SEC microservice is the piece that turns raw daily OHLCV prices into split/dividend-adjusted prices used everywhere else in the app. It's worth understanding closely for two separable reasons: (1) it implements the standard "walk backward through time accumulating a cumulative factor" algorithm that every price data provider uses for back-adjustment, with real edge cases (actions landing on non-trading days, a split and dividend sharing an effective date, dirty-row-only writes), and (2) it's the concrete implementation of the nightly rolling refresh described in the root `CLAUDE.md` ("re-detects SEC actions for the stalest ~1/7 of tickers per night... plus any ticker with a >25% overnight move") — a scheduling pattern that bounds SEC API load while still keeping every ticker's corporate-action data no more than a week stale, with a fast-path escape hatch for the case a naive weekly cron would miss (a split that just went effective).

## Files to read

- `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/PriceAdjustmentService.java` — the whole file (754 lines, but skim the diagnostics-summarizing tail quickly); focus on:
  - Lines 42–45: the two constants that drive the whole scheduling policy (`SEC_REDETECTION_INTERVAL_DAYS = 7`, `JUMP_REDETECTION_RETURN_THRESHOLD = 0.25`)
  - Lines 176–324 (`adjustAllTickers`): the nightly batch entry point — how it unions "tickers with unadjusted prices," "tickers scheduled for stale re-detection," and (if `force`) "every ticker," then loops with a per-ticker try/catch that intentionally leaves `adjusted*` columns `NULL` on failure (lines 293–301) instead of freezing raw prices as adjusted
  - Lines 326–350 (`scheduleStaleDetections`): the actual "roughly 1/7th of the universe per night, oldest-detected-first" cap logic
  - Lines 385–402 (`hasOvernightPriceJump`): the same-run escape hatch that forces re-detection when a recent overnight return exceeds 25%, checking the last 6 rows (not just the newest pair) so a jump buried by a multi-day catch-up load still triggers
  - Lines 408–512 (`applyAdjustments`): the core back-adjustment algorithm — walking prices newest→oldest, accumulating a `BigDecimal` `cumulativeFactor`, multiplying in split ratios and `1 - dividend/priorClose` factors as it crosses each action's date
  - Lines 436–467: how actions dated on non-trading days get "snapped" forward to the next trade date, and the logged warning when a split and dividend share an effective date (the dividend factor can be mis-scaled relative to the split)
- `springboot/src/main/java/com/fattorestreet/sec_api/model/CorporateAction.java` — the `ActionType` enum (`SPLIT`/`DIVIDEND`), `ratio`, `rawDividend`, `adjustedDividend`, `effectiveDate` fields that `applyAdjustments` reads
- `springboot/src/main/java/com/fattorestreet/sec_api/model/Listing.java:53` — the `lastSecDetectionAt` timestamp that both `isDetectionStale` (single-ticker path) and `scheduleStaleDetections` (batch path) key off of
- `springboot/deploy/terraform/` — skim for how `adjustAllTickers` actually gets invoked nightly (EventBridge Scheduler → Fargate one-shot task, run after the IEX HIST price load, per root `CLAUDE.md`)

## Questions to answer while reading

1. Why does the algorithm walk **newest-to-oldest** accumulating a cumulative factor, rather than oldest-to-newest? What would break if the direction were reversed?
2. Why is `cumulativeFactor` a `BigDecimal` (`MathContext.DECIMAL64`) instead of a `double`, given that the OHLC values themselves are stored as `Double`?
3. In `scheduleStaleDetections`, why cap the nightly batch at `ceil(universe / 7)` instead of just re-detecting anything older than 7 days each night — what failure mode does the cap prevent, and what's the tradeoff?
4. `stampDetectionIfSucceeded` (lines 368–379) deliberately does *not* stamp `lastSecDetectionAt` when the equity detection report carries a `failureReason`. Trace what happens to that ticker on the *next* nightly run if the stamp were applied unconditionally instead.
5. Why does `hasOvernightPriceJump` check the last 6 rows in a loop instead of only comparing the two most recent closes?

## Primer: back-adjusting a price series for splits and dividends

Raw close prices aren't directly comparable across a corporate action: a 2-for-1 split halves the raw price overnight with zero economic loss, and a dividend payment transfers value from the stock price into the shareholder's pocket, so the ex-dividend open is expected to gap down by roughly the dividend amount. "Adjusted" prices remove both distortions so that historical returns and charts read correctly. The standard technique computes a per-action multiplicative factor — `ratio` for a split (e.g. 0.5 for a 2:1 split), `1 - dividend/priorClose` for a cash dividend — and applies the *cumulative product* of every factor for actions on or after a given date to that date's raw price. Walking from the newest price backward means each day only needs the running product of factors for actions strictly after it, so the multiply-in happens exactly once per action, in a single backward pass, rather than recomputing a product per row.

## External references

- Investopedia, "Adjusted Closing Price": https://www.investopedia.com/terms/a/adjusted_closing_price.asp
- CRSP's methodology note on price/return adjustment for splits and dividends (a widely-cited academic-grade description of the same cumulative-factor technique): https://www.crsp.org/wp-content/uploads/guides/CRSP_US_Stock_Indices_Methodology_Guide.pdf

## Exercise (optional)

Pick a ticker in the local DB that has at least one recorded `SPLIT` action. By hand (or a scratch script), compute the expected `adjustedClose` for a price row a few days before the split's effective date using the formula in `applyAdjustments`, then compare it against what's actually stored in `daily_price`. Separately, try temporarily lowering `JUMP_REDETECTION_RETURN_THRESHOLD` in a local run and see how many additional tickers get flagged for re-detection on your test data.

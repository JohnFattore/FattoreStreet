---
name: sec-dividend-accuracy-pass
description: Hardens SEC dividend accuracy for equities (XBRL detection) or ETFs (filing extraction) against yfinance reference diagnostics in time-boxed iterative loops. Use when equity or ETF dividend amounts, dates, or event coverage are off, ETF events are missing or confidence-gated, or the user asks for a dividend accuracy pass while keeping SEC as the source of truth.
---

# SEC Dividend Accuracy Pass

## Purpose

Improve dividend accuracy from SEC ingestion with small, measurable iterations. Equities and ETFs share the loop below; each has its own debug focus.

Guardrail: SEC data remains the source of truth. yfinance is validation/debug only: never an ingestion source, never written to SEC dividend rows (see `data-licensing-commercial-free.md`).

## Inputs to Gather First

- Asset type: equity or ETF. Don't mix them in one pass.
- Timebox (recommended: 60-120 minutes).
- Canary list (10-20 tickers, mixed easy/hard). For equities, start from `CorporateActionValidationCanaryTest`.
- Acceptance rules: how much date drift is tolerated (for example up to 5 days) vs amount mismatch. Primary focus is amount correctness and missing events.
- ETF only: current threshold settings (for example `minConfidence`).
- Validation reference endpoints (Django, yfinance-backed, reference only):
  - `/portfolio/api/asset-dividends/`
  - `/portfolio/api/asset-splits/`

## Preflight (Before Loop 1)

- Confirm Spring Boot and Django are reachable.
- Confirm the validation path uses the Django endpoints for reference data (never a direct yfinance fetch from Spring Boot).
- Refresh canary data once (force SEC detection on the canary list) so the baseline is not stale.
- Capture a baseline per ticker plus aggregate KPI.

## KPI

Track per loop and overall:

- `missing_in_sec`
- `amount_drift`
- `date_drift` (secondary)
- weighted error: `3*missing_in_sec + 2*amount_drift + 1*date_drift`
- ETF only, extraction counters: `identityMatched`, `amountExtracted`, `dateExtracted`, `belowConfidence`, and the skip-reason distribution (`identity_mismatch`, `amount_missing`, `date_missing`, `below_confidence`)

## Iteration Workflow

Repeat until the timebox expires or there is no weighted-error improvement in 2 consecutive loops.

1. **Snapshot**: run validation for the canary list (ETF: plus the diagnostics summary). Capture aggregate counts and top mismatch samples or skip reasons.
2. **Pick one dominant failure pattern** (order below per asset type). One primary hypothesis per loop.
3. **Apply the smallest safe code change**: targeted parser/normalizer/assigner/upserter or extraction changes; no broad rewrites.
4. **Run focused tests**: Spring Boot tests for the touched code first, broader tests only if needed.
5. **Re-run canary validation**: compare KPI vs the prior loop, record gains/regressions, move to the next highest-impact issue.

## Equity Focus

Priority: `missing_in_sec`, then `amount_drift`, then `date_drift`. Within `missing_in_sec`, `coverage_missing` before `date_window_miss`.

Classify each dominant mismatch before coding:

- `coverage_missing`: no or too-few SEC events loaded for the ticker/period.
- `date_window_miss`: values look plausible but miss the matching window.
- `amount_basis_miss`: amounts off due to cumulative/quarter/split-basis handling.
- `special_dividend_miss`: special dividends merged, swapped, or misdated.
- `upsert_reconciliation_miss`: wrong-row update, duplicate overwrite, or same-date conflict.

Checklist:

- Are comparisons using `rawDividend` vs `adjustedDividend` correctly for the target use case?
- Is fallback matching too permissive and causing wrong-row updates?
- Is annual-to-quarter derivation inflating or deflating quarter values?
- Are special dividends separated from regular dividends correctly?
- Are duplicate/same-date events merged or overwritten incorrectly?
- Are whole-ticker misses caused by the ex-date assignment path (record-date candidates absent or low confidence)?

## ETF Focus

Priority: identity gating failures, then amount extraction misses, then date extraction/resolution misses, then over-strict confidence gating.

- **Identity resolution**: verify `secSeriesId` / `secClassContractId` matching and score buckets; confirm best-document selection picks the expected filing document.
- **Amount extraction**: check whether patterns miss table formats or wording variants; verify the amount is the per-share cash distribution, not an unrelated total.
- **Date extraction**: validate ex/record/pay date sources and fallback usage; keep the effective date consistent with the intended event semantics.
- **Confidence gating**: investigate `below_confidence` volume before lowering thresholds. Prefer improving signal quality over lowering a threshold globally, and adjust one only with diagnostics evidence.

## Safe Change Rules

- Keep SEC extraction logic authoritative; do not ingest from yfinance.
- Keep changes reversible and scoped; preserve behavior outside the identified mismatch category.
- Avoid broad regex expansions without canary evidence. A new lazy/bounded regex with a large alternation goes through `BoundedRegexInput`.
- If changing matching tolerance, fallback behavior, or a confidence threshold, report the KPI tradeoff and affected categories.

## Deliverable Format

At the end of the pass, report:

- Timebox used and loops run.
- Before/after KPI table for the canary list, with per-loop deltas.
- ETF: top skip reasons before vs after.
- Files changed and the issue each change addressed.
- Remaining top mismatch or skip categories, and the next recommended loop target.

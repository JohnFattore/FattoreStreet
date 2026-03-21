---
name: sec-equity-dividend-accuracy-pass
description: Hardens SEC equity dividend detection accuracy against yfinance reference diagnostics in iterative loops. Use when equity dividend amounts or event coverage are off, or when the user asks to run a time-boxed accuracy pass while keeping SEC as the source of truth.
---

# SEC Equity Dividend Accuracy Pass

## Purpose

Improve equity dividend accuracy from SEC ingestion with small, measurable iterations.

Guardrail: SEC data remains the source of truth. yfinance is validation/debug only and must not be used as an ingestion source.

## When to Use

- User reports equity dividend amount mismatches.
- Validation shows high `missing_in_sec` or `amount_drift` for equities.
- User asks for a time-boxed hardening pass.

## Inputs to Gather First

- Timebox (recommended: 60-120 minutes).
- Canary ticker list (10-20 equity tickers, mixed easy/hard).
- Acceptance rules:
  - Date drift tolerance (for example: up to 5 days tolerated).
  - Primary focus: amount correctness and missing events.
 - Validation source endpoints (Django yfinance reference only):
   - `/portfolio/api/asset-dividends/`
   - `/portfolio/api/asset-splits/`

## Preflight (Before Loop 1)

- Confirm Spring Boot and Django are reachable.
- Confirm validation path uses Django endpoints for reference data (never direct yfinance fetch from Spring Boot).
- Refresh canary data once (force SEC detect on canary list) so baseline is not stale.
- Capture baseline per ticker plus aggregate KPI.

## KPI

Track per loop and overall:

- `missing_in_sec`
- `amount_drift`
- `date_drift` (secondary)
- weighted error: `3*missing_in_sec + 2*amount_drift + 1*date_drift`

## Mismatch Triage Taxonomy

Classify each dominant mismatch before coding:

- `coverage_missing`: no or too-few SEC events loaded for ticker/period.
- `date_window_miss`: values look plausible but miss matching window.
- `amount_basis_miss`: amounts off due to cumulative/quarter/split-basis handling.
- `special_dividend_miss`: special dividends merged, swapped, or misdated.
- `upsert_reconciliation_miss`: wrong-row update, duplicate overwrite, or same-date conflict.

## Iteration Workflow

Repeat until timebox expires or no improvement in 2 consecutive loops.

1. **Baseline / current snapshot**
   - Run validation for canary list.
   - Capture aggregate counts and top mismatch samples.

2. **Pick one dominant failure pattern**
   - Prioritize in this order:
     1) `missing_in_sec`
     2) `amount_drift`
     3) `date_drift`
   - Within `missing_in_sec`, prioritize:
     1) `coverage_missing`
     2) `date_window_miss`

3. **Apply smallest safe code change**
   - Prefer targeted parser/normalizer/assigner/upserter changes.
   - Avoid broad rewrites in one loop.
   - One primary hypothesis per loop.

4. **Run focused tests**
   - Run Spring Boot tests relevant to touched code first.
   - Run broader tests only if needed.

5. **Re-run canary validation**
   - Compare KPI vs prior loop.
   - Record gains/regressions and move to next highest-impact issue.
   - Stop after 2 consecutive loops with no weighted-error improvement.

## Equity-Specific Debug Focus

Use this checklist before coding:

- Are comparisons using raw dividend (`rawDividend`) vs adjusted dividend (`adjustedDividend`) correctly for the target use case?
- Is fallback matching too permissive and causing wrong-row updates?
- Is annual-to-quarter derivation introducing inflated or deflated quarter values?
- Are special dividends being separated from regular dividends correctly?
- Are duplicate/same-date events being merged or overwritten incorrectly?
- Are whole-ticker misses caused by ex-date assignment path (record-date candidates absent/low confidence)?

## Safe Change Rules

- Keep SEC extraction logic authoritative.
- Do not ingest from yfinance.
- Keep changes reversible and scoped.
- Preserve existing behavior outside the identified mismatch category.
- If changing matching tolerance or fallback behavior, explicitly report KPI tradeoff and affected categories.

## Deliverable Format

At end of pass, report:

- Timebox used and number of loops run.
- Before/after metrics for canary list.
- Per-loop KPI deltas (before and after each loop).
- Files changed and what issue each change addressed.
- Remaining top mismatch categories.
- Next recommended loop target.


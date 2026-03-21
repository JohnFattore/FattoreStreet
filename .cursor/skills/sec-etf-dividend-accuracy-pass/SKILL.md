---
name: sec-etf-dividend-accuracy-pass
description: Hardens SEC ETF dividend extraction accuracy using filing diagnostics and yfinance reference checks in iterative loops. Use when ETF dividend events are missing, confidence-gated, or amount/date extraction quality needs improvement while keeping SEC as the source of truth.
---

# SEC ETF Dividend Accuracy Pass

## Purpose

Improve ETF dividend accuracy from SEC filing extraction with diagnostics-led iteration.

Guardrail: SEC filing extraction is the source of truth. yfinance is only a reference for validation/debugging and must not be used to populate SEC dividend rows.

## When to Use

- User reports ETF dividend mismatches or low coverage.
- Diagnostics show many skips (`identity_mismatch`, `amount_missing`, `date_missing`, `below_confidence`).
- User requests a time-boxed ETF accuracy hardening cycle.

## Inputs to Gather First

- Timebox (recommended: 60-120 minutes).
- Canary ETF list (10-20 symbols across easy/hard extraction cases).
- Current threshold settings (for example `minConfidence`).
- Acceptance rules (how much date drift is tolerated vs amount mismatch).

## KPI

Track per loop and overall:

- `missing_in_sec`
- `amount_drift`
- `date_drift`
- ETF extraction counters:
  - `identityMatched`
  - `amountExtracted`
  - `dateExtracted`
  - `belowConfidence`
  - skip reason distribution
- weighted error: `3*missing_in_sec + 2*amount_drift + 1*date_drift`

## Iteration Workflow

Repeat until timebox expires or no improvement in 2 consecutive loops.

1. **Baseline / diagnostics snapshot**
   - Run canary validation and ETF diagnostics summary.
   - Rank dominant skip reasons and mismatch categories.

2. **Pick one bottleneck**
   - Typical order:
     1) identity gating failures
     2) amount extraction misses
     3) date extraction/resolution misses
     4) over-strict confidence gating

3. **Apply smallest safe fix**
   - Tune document selection heuristics.
   - Improve extraction patterns or resolution logic.
   - Adjust threshold only with evidence from diagnostics.

4. **Run focused tests**
   - Execute relevant Spring Boot tests for touched service/controller code.

5. **Re-run canary validation and diagnostics**
   - Compare KPI and skip-reason counts.
   - Keep improvements that reduce weighted error without introducing major regressions.

## ETF-Specific Debug Focus

- **Identity resolution**
  - Verify `secSeriesId` / `secClassContractId` matching and score buckets.
  - Confirm best-document selection picks the expected filing document.

- **Amount extraction**
  - Check whether regex/source patterns miss table formats or wording variants.
  - Verify extracted amount is per-share cash distribution, not unrelated totals.

- **Date extraction**
  - Validate ex/record/pay date sources and fallback path usage.
  - Ensure effective date choice is consistent with intended event semantics.

- **Confidence gating**
  - Investigate `below_confidence` volume before lowering thresholds.
  - Prefer improving signal quality over reducing threshold globally.

## Safe Change Rules

- SEC filing data stays authoritative.
- Do not import yfinance dividends into SEC records.
- Avoid broad regex expansions without canary evidence.
- Keep each loop to one primary hypothesis.

## Deliverable Format

At end of pass, report:

- Timebox used and loops completed.
- Before/after KPI table.
- Top skip reasons before vs after.
- Files changed and rationale.
- Remaining highest-impact mismatch/skip category.


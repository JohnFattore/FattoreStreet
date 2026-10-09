# Derived Metrics

## Why

Raw quarterly figures are hard to read on their own: one quarter is noisy, and a revenue number
means little without its margin or its growth. Derived metrics turn the
[quarterly financials](quarterly-financials.md) into the handful of numbers a long-term investor
actually compares. Because they come only from filed figures, they stay auditable and free to show.

## Requirements

### Trailing-twelve-month totals sum the latest four quarters
A single quarter swings with seasonality. Revenue, net income, gross profit, operating income and
operating cash flow are shown as the sum of the four most recent quarters. (inferred)

### Growth compares with the same twelve months a year earlier
Comparing against the previous quarter mixes in seasonality. Year-over-year growth compares the
latest trailing-twelve-month total with the one ending four quarters earlier. (inferred)

### Ratios are shown only when they can be computed honestly
Net margin, gross margin, return on assets, debt to assets, cash to liabilities and operating cash
flow to net income are shown when their inputs exist. A ratio with a missing or zero denominator is
left blank, never shown as zero, because a fake zero reads as a real result. (inferred)

### Every derived number traces back to filed figures
The vision promises auditability. Every metric is computed only from stored quarterly figures,
with no outside inputs, so anyone can reproduce it from the filings. (inferred)

## Open questions

- **Missing quarters inside the window.** When one of the latest four quarters is missing, should
  the trailing total be shown from three quarters, blanked, or marked as incomplete? Today a
  missing value counts as zero, and a company with fewer than four quarters shows a total of zero,
  which conflicts with *Ratios are shown only when they can be computed honestly* above.
- **Price-based ratios.** Should valuation ratios such as price to earnings be added, built from the
  platform's own adjusted prices?

# Derived Metrics

## Why

Raw quarterly figures are hard to read on their own: one quarter is noisy, and a revenue number
means little without its margin or its growth. Derived metrics turn the
[quarterly financials](quarterly-financials.md) into the handful of numbers a long-term investor
actually compares. Because they come only from filed figures, they stay auditable and free to show.

## Requirements

### Trailing-twelve-month totals sum the latest four quarters
A single quarter swings with seasonality. Revenue, net income, gross profit, operating income and
operating cash flow are shown as the sum of the four most recent quarters. If any of those four is
missing, the total is left blank, and so is every ratio or growth figure built on it. Today a
missing quarter counts as zero, which has to change.

### Growth compares with the same twelve months a year earlier
Comparing against the previous quarter mixes in seasonality. Year-over-year growth compares the
latest trailing-twelve-month total with the one ending four quarters earlier.

### Ratios are shown only when they can be computed honestly
Net margin, gross margin, return on assets, debt to assets, cash to liabilities and operating cash
flow to net income are shown when their inputs exist. A ratio with a missing or zero denominator is
left blank, never shown as zero, because a fake zero reads as a real result.

### Valuation ratios are built from the platform's own prices
Fundamentals alone can't say whether a company is cheap or expensive. Price to earnings, price to
sales and similar ratios combine the latest close from [market data](../market-data/README.md) with
trailing-twelve-month figures, and follow the same blank-when-missing rule. These aren't built yet.

### Every ratio explains itself in plain language
A ratio a newcomer can't interpret is just a number. Each ratio shown comes with a short plain-language
definition of what it measures and how to read a high or low value. Today those definitions sit on
a hard-coded, unlinked page instead of next to the ratios.

### Every derived number traces back to filed figures
The vision promises auditability. Every metric is computed only from stored quarterly figures,
with no outside inputs beyond the platform's own prices, so anyone can reproduce it from the
filings and the exchange records.

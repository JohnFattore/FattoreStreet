# Quarterly Financials

## Why

Every metric in [fundamentals](README.md) is built from quarterly figures, so they are the evidence
the rest of the branch rests on. They have to cover the market, line up across companies, and stay
current as companies file.

## Requirements

### Each company's headline figures are kept per quarter
Revenue, net income, operating income, gross profit, earnings per share, total assets, liabilities,
equity, cash, receivables, inventory, operating cash flow, dividends paid and buybacks are kept for
every quarter a company reports them. These are enough to judge growth, profitability and balance
sheet health. (inferred)

### Quarters line up by calendar, whatever a company's fiscal year
Companies end their fiscal years in different months, and comparing one company's fiscal Q1 with
another's misleads. Each quarter is placed on the calendar quarter it covers, and its period start
and end dates are kept. (inferred)

### A missing fourth quarter is derived from the annual total
Many companies report only an annual figure for their final quarter, which would otherwise leave a
hole in every trailing-twelve-month total. Where a flow figure is missing for a quarter, it is
derived from the annual total minus the other quarters. Per-share figures are never derived this
way, because they don't add up across quarters. (inferred)

### Restatements and late filings overwrite in place
Companies amend filings and some file late. Each run revisits a recent window of periods, at least
the last two calendar years, and a restated quarter replaces the old figures rather than sitting
next to them. (inferred)

### Fundamentals are current within a day of the SEC publishing them
A company page showing last quarter's numbers weeks after earnings is the kind of staleness users
notice. Newly published figures are loaded on the next daily run with no human action. (inferred)

### A run where every request fails is a failure
The SEC routinely has nothing for some figure and period combinations, so individual misses are
normal. But a run where every request failed points at a blocked or misconfigured client, and must
raise an alert rather than report success. (inferred)

### Older history can be backfilled on demand
The daily run covers only recent years to stay within the SEC's rate limit. Loading the full
history, back to when structured data became usable, remains possible as a deliberate one-off run.
(inferred)

## Open questions

- **How far back should history go** for users? Full history from when structured data began, or a
  fixed number of years?

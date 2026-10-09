# Fundamentals

## Why

The vision promises that a user can see any US-listed company's fundamentals and trust them without
cross-checking a commercial site. Commercial fundamentals feeds forbid reuse, and free aggregators
hide where their figures come from. The companies' own SEC filings are free, primary and auditable,
so the platform builds its fundamentals from them. See *Primary sources are the truth* in
[principles](../principles.md).

Fundamentals also feed other branches: [indexes](../indexes/README.md) need shares outstanding and
public float to size companies, and the [portfolio](../portfolio/README.md) security page shows a
company's financial health next to its price.

The figures come from the SEC's structured financial data (XBRL), which publishes each reported
figure for every filer in one place per period. Loading them is a scheduled one-shot job on the
market-data service, like every other refresh, so it costs nothing between runs and needs no one
to start it. (inferred)

This branch has three parts:

- [Quarterly financials](quarterly-financials.md): what each company reported, quarter by quarter.
- [Derived metrics](derived-metrics.md): trailing-twelve-month totals and ratios built from them.
- [Filing summaries](filing-summaries.md): plain-language summaries of annual reports.

## Requirements

### Fundamentals cover operating companies, not funds
Funds don't file income statements or balance sheets in the same form, and asking for them wastes
requests against the SEC's rate limit. Fundamentals are loaded and shown for operating companies
only. (inferred)

## Non-goals

- **Analyst estimates or forecasts.** The platform shows what companies reported, not what anyone
  expects. (inferred)
- **Full financial statements line by line.** A focused set of headline figures is enough for a
  long-term investor. (inferred)

## Open questions

- **Non-free figures on the company page.** The company page also shows a quarterly table and a
  side-by-side comparison drawn from yfinance. *Only commercially free data is stored or shown* in
  [principles](../principles.md) forbids that. Remove them, or move them behind a development-only
  switch?

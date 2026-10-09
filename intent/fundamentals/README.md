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
to start it.

This branch has two parts:

- [Quarterly financials](quarterly-financials.md): what each company reported, quarter by quarter.
- [Derived metrics](derived-metrics.md): trailing-twelve-month totals and ratios built from them.

## Requirements

### Fundamentals cover operating companies, not funds
Funds don't file income statements or balance sheets in the same form, and asking for them wastes
requests against the SEC's rate limit. Fundamentals are loaded and shown for operating companies
only.

### Comparisons with third-party figures exist only in development
The company page's quarterly table drawn from yfinance, and its side-by-side comparison of SEC
figures with yfinance's, break *Only commercially free data is stored or shown* in
[principles](../principles.md). The table is removed from the public page, and the comparison is
reachable only in development as a diagnostic.

## Non-goals

- **Analyst estimates or forecasts.** The platform shows what companies reported, not what anyone
  expects.
- **Full financial statements line by line.** A focused set of headline figures is enough for a
  long-term investor.
- **Filing summaries.** Machine-written summaries of annual reports were shown on the company page,
  but generation was retired and a frozen, ageing summary misleads more than it helps. They are
  removed from the page, and the stored summaries go with them.

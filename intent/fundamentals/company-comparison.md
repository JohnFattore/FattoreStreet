# Company Comparison

## Why

A [derived metric](derived-metrics.md) means most next to another company's: a 25% net margin is
strong or weak only against a peer. Without a side-by-side view, a user flips between two company
pages and compares numbers from memory, which is slow and error-prone. The comparison puts two
companies' headline fundamentals in one place, drawn from the same filed figures as everything else
in [fundamentals](README.md).

## Requirements

### Any two operating companies can be compared on the same figures
A comparison that shows different figures for each side can't be read across. Both companies are
shown on the same trailing-twelve-month totals, latest balance-sheet figures and ratios, row by row.

### The stronger figure is marked only where better has one direction
Marking a winner where none exists misleads a newcomer. Growth, margins, return on assets, cash to
liabilities and operating cash flow to net income favour the higher value, and debt to assets
favours the lower. Size figures such as revenue or total assets are never marked, because bigger is
not better. A tie or a blank figure marks neither side. (inferred)

### A comparison can be shared as a link
A comparison that has to be re-entered can't be sent to someone else or revisited. The two
companies being compared are part of the page's address. (inferred)

### A company that can't be loaded fails the comparison visibly
Half a comparison next to an empty column reads as if the missing company had no figures. If either
company can't be loaded, the page says so instead of showing one side. (inferred)

## Non-goals

- **Comparing against third-party figures.** See *Comparisons with third-party figures exist only in
  development* in [fundamentals](README.md).

## Open questions

- **More than two companies.** Is a comparison always a pair, or should it grow to a small peer
  group (three to five)? The page compares exactly two today.
- **Valuation ratios.** Once *Valuation ratios are built from the platform's own prices* in
  [derived metrics](derived-metrics.md) lands, should they join the comparison?

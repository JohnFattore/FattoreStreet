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

### Each company's size is shown as its latest market cap
Fundamentals alone hide how big a company is in the market, which a reader needs to judge whether
two companies are true peers. Each side shows the same market cap the [indexes](../indexes/market-cap.md)
use, built from SEC share counts and exchange closes, summed across the company's listed share
classes and labelled with the date of the close it uses. Like other size figures it marks no winner.
A company the platform hasn't sized shows no figure rather than a guess.

### A written analysis sums up the comparison from the figures shown
Twenty rows of figures tell a newcomer which number is higher but not what the differences add up
to. On request, the page adds a short plain-language analysis written by the
[advisor](../advisor/README.md)'s model from exactly the figures on the page, so it never cites a
number the reader can't see and check. It is written fresh for each comparison from current figures
and not stored, which keeps it from going stale the way the retired filing summaries did.

### The analysis is written only when the reader asks for it
Every analysis is a paid model call and uses up one of the reader's daily advisor questions, and many
readers only want the table. Nothing is generated until the reader asks for it on that comparison.

### The analysis explains; it never recommends or forecasts
A model asked to compare two companies drifts toward naming the better buy, which is the stock tip
the root's *Not investment advice* rules out. The analysis describes what the figures show (scale,
growth, profitability, balance-sheet strength) and says nothing about which to buy, price targets
or future results. See the *Analyst estimates or forecasts* non-goal in [fundamentals](README.md).

### The analysis is for signed-in users and shares the advisor's spend ceiling
Each analysis is a hosted-model call on the same per-token bill as the advisor. Only a signed-in
user gets one, and it counts toward the same per-user daily limit and monthly cap as an advisor
question (see *Spend has a hard ceiling* in [advisor](../advisor/README.md)). A signed-out reader
still sees the full comparison, with a prompt to sign in for the analysis. (inferred)

### A missing analysis never hides the comparison
The figures are the primary data and the analysis is only a gloss on them. If the analysis can't be
written (the model is down, a limit is reached), the comparison still shows and the analysis area
says why. (inferred)

### A company that can't be loaded fails the comparison visibly
Half a comparison next to an empty column reads as if the missing company had no figures. If either
company can't be loaded, the page says so instead of showing one side. (inferred)

## Non-goals

- **Comparing against third-party figures.** See *Comparisons with third-party figures exist only in
  development* in [fundamentals](README.md).

## Open questions

- **Market cap coverage.** Sizes are only computed for the companies the index refresh covers, so a
  company outside it shows no market cap. Should the refresh widen to every operating company, or
  should the comparison size a company on demand?

- **More than two companies.** Is a comparison always a pair, or should it grow to a small peer
  group (three to five)? The page compares exactly two today.
- **Valuation ratios.** Once *Valuation ratios are built from the platform's own prices* in
  [derived metrics](derived-metrics.md) lands, should they join the comparison?

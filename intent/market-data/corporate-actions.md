# Corporate Actions

## Why

Adjusted prices are only as correct as the list of splits and dividends behind them. A missed split
shows up as a crash, and a misdated dividend shifts returns into the wrong period. The free, primary
record of what a company did is its own SEC filings. But filings are unstructured, often late, and
expensive to fetch at the SEC's rate limit, so the requirements here cover accuracy and also how much
evidence each action needs and how much fetching the platform may do.

## Requirements

### Splits and dividends come from SEC filings
Filings are the free, primary record of what a company did. Raw price history may confirm or date an
action, since it's also a free primary source. Third-party data never creates, changes or removes an
action. The weekly accuracy check in [adjusted prices](adjusted-prices.md) is how drift gets caught.

### Every action records how well its date is supported
Dates come from evidence of varying strength, ranging from an explicit statement in a filing to an
inference from cadence. Each stored action records where its date came from and how confident the
platform is in it, and a later detection backed by weaker evidence never moves a date set by
stronger evidence.

### A split is dated to the day the price actually changed
Adjustments key on the first day traded at the new price level, so dating the split anywhere else
puts a false jump in the adjusted series. When the price history shows an overnight break matching
the split ratio, that day is the split's date. A split implied by filings that has no matching break,
in a period with full price coverage, is rejected as a false positive.

### Fresh actions take effect quickly
An adjusted series that lags a split by a quarter is wrong for that whole quarter. A split is
reflected the night it shows up in prices. A declared dividend is reflected once its declaration is
filed, without waiting for the next quarterly report.

### A failed fetch never removes actions
This applies *Missing evidence is not evidence of absence* from [principles](../principles.md). When
a detection run can't reach a meaningful share of its filings, it deletes no existing actions and adds
none on weak evidence alone. The security is retried on a later run.

### Heavy detection is limited to securities users care about
The price universe is about 24k symbols, most with no SEC filer behind them, and scanning them all at
the SEC's rate limit takes days. Filing-based detection runs automatically only for members of the
main index. Each member is re-checked at least weekly, and immediately after a large overnight price
move. Securities outside that scope still get adjusted values from whatever actions are already
stored.

### Each filing is read once
Filings never change once filed, so fetching one again wastes the SEC rate budget. What's been
extracted from a filing is kept, and later runs fetch only filings they haven't read yet. The
exception is when the extraction logic itself has changed.

## Non-goals

- **Stock or non-cash dividends, spin-offs, mergers and rights issues.** Only splits and cash
  dividends are tracked. (inferred)

## Open questions

- **ETFs and funds.** Fund distributions aren't in structured filing data, and detecting them nightly
  would push the run past 17 hours, so fund actions, and with them fund adjusted prices, are
  currently frozen. Should funds be in scope? If so, what freshness is acceptable: a separate slower
  job, or a weekly pass?
- **Detection scope.** Is "main index members" the right definition of the securities users care
  about? Should a security held in any user's portfolio also be in scope?

# Performance

## Why

The core audience is index-minded, so the question that matters is not "did I make money" but "did
I beat just owning the market". [Portfolio](README.md) answers that for each position and for the
whole portfolio. A return measured without dividends or splits, or against the wrong benchmark,
tells a passive investor the wrong story.

## Requirements

### Each position shows its return from purchase to sale or to today
A user needs to see what each decision earned. Open positions are valued at the latest price,
closed ones at their sale price. (inferred)

### Each position is compared with the broad market over the same dates
Comparing a holding with the market over a different period is meaningless. Each position's return
is shown next to the return of a broad US market benchmark from the same buy date to the same sale
date or today. (inferred)

### Returns account for splits and dividends
A split looks like a crash and a dividend payer looks like it lagged if raw prices are used. Returns
are computed from [adjusted prices](../market-data/adjusted-prices.md), so a position's return is
its real return. (inferred)

### The portfolio can be seen as a whole
Individual positions don't show concentration. A user can see the portfolio's total value and how
it's split across holdings. (inferred)

### A user can see how common benchmark funds have performed
A passive investor judges their choices against the broad funds they could have bought instead.
Recent and multi-year returns for a fixed set of broad stock, bond and international benchmarks are
shown alongside the portfolio. (inferred)

## Open questions

- **Are dividends counted today?** Position returns currently compare share prices only, which
  leaves out dividends unless the prices used are adjusted. Which price series is the target?
- **Which benchmark?** Positions are compared with an S&P 500 fund. Should that be a Fattore index
  instead, now that the platform builds its own (see [indexes](../indexes/README.md))?

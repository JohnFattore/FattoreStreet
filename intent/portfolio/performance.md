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

### Each position is compared with a benchmark over the same dates
Comparing a holding with the market over a different period is meaningless. Each position's return
is shown next to a benchmark's return from the same buy date to the same sale date or today.

### The user picks the benchmark from a small set, defaulting to the total US market
Different investors measure against different things, but an open-ended choice invites cherry-picking.
A short list of broad benchmarks is offered, including a Fattore index so a comparison can rest
entirely on the platform's own data (see [indexes](../indexes/README.md)). The default is a total
US market fund, the natural yardstick for a passive investor. Today only an S&P 500 fund is offered.

### Returns account for splits and dividends
A split looks like a crash and a dividend payer looks like it lagged if raw prices are used. Returns
are computed from [adjusted prices](../market-data/adjusted-prices.md), so a position's return is
its total return. Today they compare raw share prices, which leaves dividends out.

### The portfolio can be seen as a whole
Individual positions don't show concentration. A user can see the portfolio's total value and how
it's split across holdings.

### A user can see how common benchmark funds have performed
A passive investor judges their choices against the broad funds they could have bought instead.
Recent and multi-year returns for a fixed set of broad stock, bond and international benchmarks are
shown alongside the portfolio.

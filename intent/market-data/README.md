# Market Data

## Why

The vision promises price history that a user can trust without cross-checking a commercial site.
Every return, chart, ratio and index on the platform is built on that history, so it has to be
accurate and free to use, and it has to account for splits and dividends. Raw prices alone are
misleading: a 4-for-1 split looks like a 75% crash, and a stock that pays dividends looks worse than
it actually performed.

Commercial providers sell adjusted prices, but their licenses forbid reuse. So the platform builds
its own from two free primary sources: an exchange's historical trade records for raw prices, and
companies' SEC filings for splits and dividends. See *Only commercially free data is stored or
shown* and *Primary sources are the truth* in [principles](../principles.md).

This branch has three parts:

- [Raw prices](raw-prices.md): what traded, stored as it happened.
- [Corporate actions](corporate-actions.md): the splits and dividends found in filings.
- [Adjusted prices](adjusted-prices.md): raw prices corrected for those actions, so returns are real.

Users depend on it when viewing a security's price history and total return. Other branches depend
on it too: indexes rank by market value, fundamentals ratios use price, and portfolios are valued at
it.

## Non-goals

- **Intraday or real-time prices.** End of day is enough for long-term investing.
- **Non-US securities.**

# Portfolio

## Why

The vision promises that a user can track their own portfolio and accounts against the platform's
data. That is where primary-source data turns into something personal: a long-term investor wants
to know what they hold, across which accounts, and whether owning it beat simply holding the
market. Without this branch the platform is a reference site, and users go back to a broker's
summary they can't audit.

Holdings belong to one signed-in user, so they live in the main API (Django) alongside accounts,
where ownership is enforced, and they're valued using the market data the rest of the platform
builds. See [data ownership](../accounts/data-ownership.md) and [market data](../market-data/README.md).
(inferred)

This branch has four parts:

- [Holdings](holdings.md): a user's accounts and the positions in them.
- [Performance](performance.md): what those positions returned, and how that compares with the market.
- [Watch list](watch-list.md): securities a user follows but doesn't hold.
- [Security page](security-page.md): the per-security view every other page links to.

## Requirements

### Every number comes from the platform's own free data
Applies *Only commercially free data is stored or shown* in [principles](../principles.md), with no
exception for this branch. Prices, returns, dividends, splits and company details are taken from
[market data](../market-data/README.md) and [fundamentals](../fundamentals/README.md). Today most of
them still come from yfinance and current quotes from Finnhub. That is a known violation being worked
off, not an allowed state, and live quotes are expected to be the last piece to move.

### The current price is the latest end-of-day close
No free source of live quotes is known, and the root's *Not real-time* says end of day is enough.
Wherever a page shows a security's current price, it's the most recent close from
[raw prices](../market-data/raw-prices.md), labelled with its date.

## Non-goals

- **Brokerage imports, for now.** Holdings are entered by hand. Importing them from a broker is
  wanted eventually; it's deferred, not ruled out.
- **Tax reporting, for now.** Account types are recorded for context, not to compute tax. Tax views
  are wanted eventually; deferred, not ruled out.
- **Transaction-level tracking.** A holding with buy and sell dates is the model. Individual lots,
  cost-basis records and a simulated cash balance are not planned, and the unused records for them
  should be removed.
- **Real-time valuation.** End-of-day values are enough, per the root's *Not real-time*.

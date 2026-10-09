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

## Non-goals

- **Brokerage connections or imports.** Holdings are entered by the user. See the root's *Not a
  trading platform*. (inferred)
- **Tax reporting.** Account types are recorded for context, not to compute tax. (inferred)
- **Real-time valuation.** End-of-day values are enough, per the root's *Not real-time*.

## Open questions

- **Non-free data on user pages (major).** Most numbers on the portfolio, security page and watch
  list (prices at buy and sell dates, current prices, returns, dividends, splits, company details)
  come from yfinance, and current quotes from Finnhub. *Only commercially free data is stored or
  shown* in [principles](../principles.md) forbids this for yfinance and treats Finnhub as forbidden
  until its license is verified. Is the target to move all of it onto the platform's own market data,
  or is a named temporary exception allowed while that happens?
- **Unused legacy records.** Older records for transactions, cost basis and a simulated cash balance
  are still kept but nothing uses them. Remove them, or is transaction-level tracking planned?

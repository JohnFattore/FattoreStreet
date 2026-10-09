# FattoreStreet

This tree is the source of truth for why FattoreStreet exists and what each part of it must do. The
code is derived from it. Each folder is a branch, and its README explains why that area exists in
terms of this page. To understand any requirement, read from here down to it. Constraints that apply
everywhere are in [principles](principles.md), and how the product is shaped to meet them is in
the [high-level design](design.md).

## Who it is for

FattoreStreet is a personal platform, run by one person and open to the public. Its core audience is
a long-term, index-minded individual investor who wants to understand their holdings using
primary-source data rather than a broker's summary. (inferred: the Boglehead advisor and the
cap-weighted index proxies suggest a passive-investing outlook)

It is also the author's public workspace: a place to write, keep recommendations, and practise
building and operating a real production system end to end. (inferred)

## The problem

Free investing tools either hide where their numbers come from or depend on data whose license
forbids reuse. A serious individual investor ends up trusting figures they cannot audit.

FattoreStreet answers with data that is:

- **Primary-source.** Fundamentals and corporate actions come from the companies' own SEC filings,
  and prices from an exchange's own historical feed.
- **Free to use and show.** Everything stored or displayed is licensed for commercial use, so the
  platform can stay public. See [principles](principles.md).
- **Auditable.** Derived numbers (adjusted prices, ratios, index weights) can be traced back to the
  filings and trades they came from.

## What success looks like

- A user can look up any US-listed company and see its price history, fundamentals, splits and
  dividends, and trust them without cross-checking a commercial site.
- A user can track their own portfolio and accounts against that data.
- The data stays current without a human starting anything.
- Running it costs a hobby budget, not a business budget. (inferred)

## Branches

| Branch | Role |
|------|------|
| [Market data](market-data/README.md) | The security universe, daily prices, splits, dividends and adjusted prices: the base everything else builds on |
| [Fundamentals](fundamentals/README.md) | Quarterly financials and ratios from SEC filings |
| [Indexes](indexes/README.md) | Self-built cap-weighted index proxies used as benchmarks and as the scope for heavy work |
| [Economic indicators](economic-indicators/README.md) | Macro series for context |
| [Accounts](accounts/README.md) | Registration, sign-in, and keeping each user's data their own |
| [Portfolio](portfolio/README.md) | A user's accounts, holdings and watch list |
| [Advisor](advisor/README.md) | A conversational assistant with a passive-investing outlook |
| [Blog](blog/README.md) | The author's writing and study notes |
| [Restaurants](restaurants/README.md) | A legacy, frozen restaurant log, kept running until it moves out |
| [Entertainment](entertainment/README.md) | The author's media recommendations (inferred: secondary to the finance core) |
| [Feedback](feedback/README.md) | A changelog and a way for users to report problems |

## Non-goals

- **Not a trading platform.** No order placement, ever. Reading holdings from a broker is a
  possible future feature (see [portfolio](portfolio/README.md)); acting on a user's behalf is not.
- **Not real-time.** End-of-day data is enough for long-term investing. Intraday quotes are out of
  scope.
- **Not investment advice.** The advisor informs; it does not recommend specific trades. (inferred)
- **No paid or restricted data**, however convenient. See [principles](principles.md).
- **Not official index products.** The Fattore indexes are proxies and are never presented as the
  indexes they approximate.

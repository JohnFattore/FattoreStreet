# Security Page

## Why

The holdings table, watch list and indexes all point at individual securities. The security page is
where a user lands to understand one of them: its price history, splits and dividends, and what the
company or fund is. If it's missing or wrong, every link in [portfolio](README.md) leads nowhere
trustworthy.

## Requirements

### Anyone can open a page for any listed US security
Users research before they hold, and the platform is public. The page needs no sign-in and works
for any ticker in the [security universe](../market-data/security-universe.md).

### The page shows price history, splits and dividends
These are the facts a long-term investor checks first. They come from
[market data](../market-data/README.md), so the page shows what the platform can audit.
Today they come from yfinance; see *Every number comes from the platform's own free data* in the
[portfolio README](README.md).

### Companies and funds are each described in their own terms
A fund's useful facts (what it holds, what it costs) differ from a company's (what it earns). The
page shows the right kind of summary for each.

### The page links to the security's fundamentals
Price alone doesn't explain value. Companies link to their figures in
[fundamentals](../fundamentals/README.md).

### Comparisons with third-party data exist only in development
The comparison of the platform's prices, splits and dividends with yfinance's is a diagnostic. Per
*Only commercially free data is stored or shown* in [principles](../principles.md), it's reachable
only in development and never on the public site. Today it's public and linked from every security
page, which has to change.

# Indexes

## Why

A long-term, index-minded investor judges everything against a benchmark. The real benchmark
indexes are licensed products whose membership and weights can't be republished freely, so the
platform builds its own proxies, the Fattore indexes, from the free data it already holds: exchange
prices and SEC filings. See *Only commercially free data is stored or shown* in
[principles](../principles.md).

Indexes also do a second job. The full price universe is far too large to scan for corporate
actions within the SEC's rate limit, so the broadest Fattore index defines the set of securities
that gets the heavy work. See *Heavy detection is limited to securities users care about* in
[corporate actions](../market-data/corporate-actions.md).

The Fattore 50, 100 and 1000 are cap-ranked, cap-weighted top-50, top-100 and top-1000 lists of US
companies, modelled on the large-cap indexes investors already know. They are rebuilt by a daily
scheduled one-shot job on the market-data service, after the night's prices have loaded. (inferred)

This branch has three parts:

- [Market cap](market-cap.md): how big each company is, from filings and prices.
- [Construction](construction.md): who gets in and how much each weighs.
- [Benchmark comparison](benchmark-comparison.md): how close a proxy is to what it approximates.

## Non-goals

- **Official index products.** The Fattore indexes are never presented as, or named as, the
  indexes they approximate. Restated from the root non-goals.
- **Investable products.** No fund or tradable product tracks them. (inferred)

## Open questions

- **Where the candidates come from.** The daily refresh computes market caps for the tickers in a
  fund provider's holdings file for the official large-cap index, not for the whole market, so the
  official index's membership decides who can enter the Fattore 1000. Should candidates come from
  the platform's own universe instead, so the proxy is independent?

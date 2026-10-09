# Raw Prices

## Why

Adjusted prices and every check on them are derived from raw prices, so raw prices are the evidence
everything else in market data rests on. They have to cover the whole market, stay current, and
never be altered.

## Requirements

### Daily prices exist for every listed US security
Users look up arbitrary tickers, and the indexes need the whole market to rank. Daily open, high,
low, close and volume are kept for every security the price source reports, including delisted
ones. History is never thrown away.

### Raw prices are never modified after they're stored
Raw prices are the evidence that adjustments are built from and checked against, and changing them
would destroy that evidence. Adjusted values are kept alongside them, never in their place.

### Prices are current by the next market open
Stale prices make every downstream number stale. Each trading day's prices are available before the
next market open with no human action, and a day that fails to load is retried automatically.

### Loading prices is safe to repeat
Retries and catch-up runs happen routinely, so loading a day that's already stored changes nothing.

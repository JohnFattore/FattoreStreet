# Economic Indicators

## Why

A long-term investor doesn't trade on macro data, but rates, inflation and employment explain much
of what the market is doing and what bonds pay. Without that context, a user sees their portfolio
move with no way to tell a market-wide shift from a problem with their holdings.

The Federal Reserve Bank of St. Louis publishes these series through FRED, free and from the
agencies that produce them, which fits *Primary sources are the truth* in
[principles](../principles.md). Like the rest of the platform's public data, the series are stored
and refreshed by a scheduled job, so the page keeps working when FRED doesn't. Today they're fetched
from FRED on each view and held only briefly in memory, so an outage breaks the page; that has to
change.

This branch has one part:

- [Series](series.md): which indicators are shown, and in what form.

## Non-goals

- **Forecasts or a macro outlook.** The page shows published data, not predictions.
- **Non-US economies, for now.** US indicators only today; global context is wanted eventually,
  deferred rather than ruled out.

## Requirements

### The page survives a FRED outage
Context data that vanishes whenever a free source hiccups teaches users not to rely on it. The page
shows the last stored readings with their dates when FRED can't be reached, and the refresh retries
later. Applies *Missing evidence is not evidence of absence* in [principles](../principles.md).

### Any FRED series can be requested, not only the ones the page shows
Keeping the series open lets the page and future features add indicators without a code change.
The FRED key's own rate limit is accepted as the only guard. Stored and shown series still have to
pass the licensing check in [series](series.md).

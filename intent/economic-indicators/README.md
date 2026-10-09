# Economic Indicators

## Why

A long-term investor doesn't trade on macro data, but rates, inflation and employment explain much
of what the market is doing and what bonds pay. Without that context, a user sees their portfolio
move with no way to tell a market-wide shift from a problem with their holdings. (inferred)

The Federal Reserve Bank of St. Louis publishes these series through FRED, free and from the
agencies that produce them, which fits *Primary sources are the truth* in
[principles](../principles.md). Unlike the rest of the platform's public data, the series are
fetched from FRED when a user views them and held briefly in memory, not stored, because FRED is
already the system of record and keeping a copy would add a job without adding trust. (inferred)

This branch has one part:

- [Series](series.md): which indicators are shown, and in what form.

## Non-goals

- **Forecasts or a macro outlook.** The page shows published data, not predictions. (inferred)
- **Non-US economies.** (inferred)

## Open questions

- **Failing gracefully.** When FRED is down, the page fails rather than showing the last good
  readings. Should recent readings be kept so the page survives an outage, which would mean storing
  them?
- **An open relay.** The service fetches whatever series a caller names, using the platform's own
  FRED key. Should it accept only the series the page actually shows?

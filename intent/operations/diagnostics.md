# Diagnostics

## Why

Auditing derived market numbers—like split- and dividend-adjusted prices—sometimes requires checking
them against a third-party aggregator to spot missing filings or bad ratios. Under *Only
commercially free data is stored or shown* in [principles](../principles.md), third-party figures
whose license does not allow redistribution must never be stored or shown to the public, yet the
author still needs a quick way to run a spot check on specific tickers.

## Requirements

### Author-only diagnostic comparisons are gated to staff accounts
A client-only username check in the browser can be bypassed by anyone who edits local browser
storage, exposing non-free diagnostic comparisons to public visitors. The diagnostic comparison page
sits behind the shared staff-only route guard, which verifies staff status with the server before
rendering.

### Diagnostic comparisons are computed on the fly and never persisted
Applies *Only commercially free data is stored or shown* from [principles](../principles.md). Storing
third-party comparison scores would taint the database with non-free derived data. The comparison
is calculated in memory for the tickers the author is inspecting and discarded when the view closes.

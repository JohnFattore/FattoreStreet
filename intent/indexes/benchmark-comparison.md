# Benchmark Comparison

## Why

A proxy is only trustworthy if its builder can see how close it gets to what it approximates.
Without a comparison, a construction bug could drift the Fattore 1000 away from the large-cap market
and nobody would notice. This keeps the [indexes](README.md) honest.

The comparison needs the official index's holdings, which come from a fund provider's file whose
license is unverified. So it is a development diagnostic, like the price comparison in
[security page](../portfolio/security-page.md), not a user feature.

## Requirements

### The comparison exists only in development
Per *Only commercially free data is stored or shown* in [principles](../principles.md), a source with
an unverified license is never shown to users. The comparison is reachable only in development.
Today it's on the public indexes page, which has to change.

### The comparison shows overlap and weight differences
A developer can see which members overlap with the benchmark's holdings, the share of symbols in
common, and how far the weights differ, side by side.

### The reference holdings never feed the indexes
The file is evidence to check construction against, not an input to it. See *Candidates come from
the platform's own security universe* in [indexes](README.md).

## Non-goals

- **A schedule for the reference holdings.** The file is replaced by hand when a construction check
  needs a fresher comparison. A stale snapshot is acceptable for a diagnostic.

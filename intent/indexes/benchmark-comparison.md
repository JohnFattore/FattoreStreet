# Benchmark Comparison

## Why

A proxy is only trustworthy if users can see how close it gets to what it approximates. Without a
comparison, a construction bug could drift the Fattore 1000 away from the large-cap market and
nobody would notice. This keeps the [indexes](README.md) honest.

## Requirements

### The Fattore 1000 is shown beside the benchmark it approximates
Users can see which members overlap with the benchmark's holdings, the share of symbols in common,
and how far the weights differ, side by side. (inferred)

### The comparison says why the numbers differ
The benchmark's weights come from a different date and methodology, and without saying so users
would read every difference as an error. The comparison explains that weights won't match exactly.
(inferred)

### The comparison never presents the proxy as the official index
Restates the root non-goal: the benchmark is named only as the thing being compared against.
(inferred)

## Open questions

- **License of the reference holdings.** The benchmark side comes from a fund provider's published
  holdings file, which is shown to users and also decides the candidate list (see open questions
  in [indexes](README.md)). Its license for storage and display is unverified, which *Only
  commercially free data is stored or shown* in [principles](../principles.md) treats as forbidden
  until verified. Verify it, or drop the comparison?
- **Naming.** Does naming the official index in the comparison sit too close to "never presented as
  the indexes they approximate"?
- **Staleness.** The holdings file is a fixed snapshot. How often must it be refreshed, and by what?

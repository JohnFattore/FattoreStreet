# Construction

## Why

[Indexes](README.md) are only useful as benchmarks if their rules are simple, published and applied
the same way every day. A benchmark that silently changes shape, or shrinks because a data source
hiccuped, is worse than none.

## Requirements

### Only US operating companies' common stock is eligible
The indexes approximate US large-cap equity benchmarks, so funds, partnerships, preferred stock and
non-US companies are excluded. A company incorporated in the US is eligible; a company with unknown
incorporation counts as US when it is headquartered there. A company with no free-float market cap
is not eligible. (inferred)

### Members are the largest eligible companies by free-float market cap
The Fattore 50, 100 and 1000 take the top 50, 100 and 1000 eligible companies ranked by free-float
market cap. (inferred)

### Weights are proportional to free-float market cap and sum to exactly 100%
Cap weighting mirrors the benchmarks being approximated, and weights that don't add up make every
comparison off by a little. (inferred)

### Indexes are rebuilt daily after prices load
Membership and weights reflect the latest prices by the next morning without anyone starting it.
Applies *The data stays current without a human* from [principles](../principles.md). (inferred)

### A bad refresh never shrinks or wipes an index
Rebuilding replaces the whole membership, so rebuilding on top of a mostly failed size refresh
would leave an index with a fraction of its members. When too few companies were sized, the
rebuild is skipped, yesterday's members are kept, and the run fails loudly. (inferred)

### An index with fewer eligible companies than its target is marked partial
If fewer companies qualify than the index's size, users and later runs need to know it isn't
complete rather than mistaking a short list for the real thing. (inferred)

### Membership is public
Anyone can see each index's members, weights, sizes and the inputs behind them, so the construction
can be checked. (inferred)

## Open questions

- **Rebalance cadence.** Official benchmarks reconstitute yearly or quarterly; the Fattore indexes
  rebuild daily. Is daily turnover the intent, or should membership change on a schedule with only
  weights moving daily?
- **History.** Rebuilds replace current membership. Should past membership be kept so index returns
  can be computed over time?

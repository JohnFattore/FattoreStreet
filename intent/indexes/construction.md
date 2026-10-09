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
is not eligible.

### Members are the largest eligible companies by free-float market cap
The Fattore 50, 100 and 1000 take the top 50, 100 and 1000 eligible companies ranked by free-float
market cap.

### Weights are proportional to free-float market cap and sum to exactly 100%
Cap weighting mirrors the benchmarks being approximated, and weights that don't add up make every
comparison off by a little.

### Indexes are rebuilt daily after prices load
Membership and weights reflect the latest prices by the next morning without anyone starting it.
Applies *The data stays current without a human* from [principles](../principles.md). Membership
and weights both move daily; there is no separate reconstitution schedule.

### A bad refresh never shrinks or wipes an index
Rebuilding replaces the whole membership, so rebuilding on top of a mostly failed size refresh
would leave an index with a fraction of its members. When too few companies were sized, the
rebuild is skipped, yesterday's members are kept, and the run fails loudly.

### An index with fewer eligible companies than its target is marked partial
If fewer companies qualify than the index's size, users and later runs need to know it isn't
complete rather than mistaking a short list for the real thing.

### Membership is public
Anyone can see each index's members, weights, sizes and the inputs behind them, so the construction
can be checked.

## Non-goals

- **Scheduled reconstitution.** Official benchmarks change membership yearly or quarterly. The
  Fattore indexes accept daily turnover in exchange for always being current.
- **Past membership, for now.** Each rebuild replaces the current membership, and history isn't
  kept, because the construction isn't yet good enough to be worth a track record. Once it is, past
  membership will be rebuilt from stored prices and filings, and history kept from then on.

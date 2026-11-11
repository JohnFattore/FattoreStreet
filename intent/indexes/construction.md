# Construction

## Why

[Indexes](README.md) are only useful as benchmarks if their rules are simple, published and applied
the same way every day. A benchmark that silently changes shape, or shrinks because a data source
hiccuped, is worse than none.

## Requirements

### Only US operating companies' common stock is eligible
The indexes approximate US large-cap equity benchmarks, so funds, partnerships, preferred stock and
non-US companies are excluded. A company headquartered in the US is eligible wherever it is
incorporated: many US companies incorporate in Ireland, Bermuda or Switzerland for tax reasons while
running the business from here, and excluding them drops names investors think of as US large caps.
Headquarters decides: a company incorporated in the US but run from abroad is not eligible. When the
filings give no headquarters, US incorporation stands in for it.
A company with no free-float market cap is not eligible.

### Members are the largest eligible companies by total market cap
The Fattore 50, 100 and 1000 take the top 50, 100 and 1000 eligible companies ranked by the whole
company's market cap. Ranking by free float would drop a large company with big insider stakes near
the cutoff, though it is plainly one of the largest; such a company stays in at a small weight
instead, since weighting is by free float. Every listed class of a member company is in the index,
so an index holds more listings than companies, and its size and partial flag count companies.

### Weights are proportional to free-float market cap and sum to exactly 100%
Cap weighting mirrors the benchmarks being approximated, and weights that don't add up make every
comparison off by a little.

### Indexes are rebuilt daily after prices load
Membership and weights reflect the latest prices by the next morning without anyone starting it.
Applies *The data stays current without a human* from [principles](../principles.md). Membership
and weights both move daily; there is no separate reconstitution schedule.

### Only currently priced companies are ranked
A company that was delisted, acquired or renamed keeps its last computed size, and ranking it would
leave a dead or duplicate entry in the index. Only companies whose size was computed from a price
within a few trading days of the latest one are ranked. The window is measured against the latest
price, not the calendar, so a failed price load doesn't empty the index, and a few missed refreshes
keep yesterday's size. If the cutoff would remove most of an index's current members, the refresh
behind it was partial, not the market moving, so the rebuild is skipped as below.

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

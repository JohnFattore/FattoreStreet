---
parent: high-level-design
prefix: PRICE-ADJ
---

# Price Adjustment

Facets: `PRICE-ADJ-SCOPE-*` for working-set and detection scheduling, `PRICE-ADJ-APPLY-*` for the
adjustment arithmetic.

## Context and Design Philosophy

This segment owns two questions that share one class because they share one nightly pass:

1. **Which tickers get looked at tonight**, and which of those get a SEC detection run.
2. **Given a ticker's corporate actions, what are its adjusted prices.**

They are separable intents with separate failure modes. Scheduling failures cost freshness: a split is
found a week late. Arithmetic failures cost correctness: every price before an action is wrong, and
nothing downstream can tell. The facets exist to keep those apart in the specs even though one class
implements both.

**The scheduling half exists because the affordable set is much smaller than the interesting set.** The
price universe is roughly 24k IEX symbols. Most have no SEC counterpart, the SEC rate limiter is
per-process, and a full detection pass takes days. So automatic detection is scoped to one index's
membership, refreshed on a rolling cycle, with one event-driven exception for the case that cannot wait.

**The arithmetic half is backward-cumulative and must be idempotent.** Adjusted prices are computed by
walking from newest to oldest, multiplying a running factor at each action. Running it twice must produce
the same numbers, because it runs every night over history that mostly has not changed. That requirement
is why the pass writes only rows whose values actually differ, and why detection and adjustment must
agree on what an effective date means.

## Working Set

Three sources contribute, unioned:

| Source | Why |
|---|---|
| Tickers with any null adjusted column | The `hist-ingest` signal: these rows are new or were never adjusted |
| Tickers scheduled for stale re-detection | Their actions may change tonight, so their prices may change |
| Every ticker with prices, when `force` is set | The deliberate escape hatch |

A ticker in the working set with no matching `Asset` gets `adjusted = raw` written directly and is counted
as skipped. That is the honest answer for a symbol with no SEC counterpart: it has no discoverable
corporate actions, so its raw prices *are* its adjusted prices, and leaving the columns null would make it
reappear in the working set every night forever.

`etfOnly` and `equityOnly` are mutually exclusive and rejected together as invalid arguments rather than
being silently resolved.

## Detection Scope

Automatic SEC detection is restricted to members of a configured index
(`app.price-adjustment.detection-index-code`, defaulting to `FAT1000`) that also have price data.

**The empty-index case fails closed.** If the index is configured but has no members, the scope is empty
and no automatic detection runs that night, logged at error level. The alternative, falling back to the
full price universe, is precisely the multi-day SEC-hammering run the scope exists to prevent, so a night
of missed detection is the cheaper failure. A blank configuration disables scoping entirely and is the
explicit way to ask for the full universe.

## Rolling Re-Detection

In-scope tickers whose last detection is missing or older than 7 days are candidates. They are sorted
oldest first with never-detected tickers at the head, and the run takes at most `ceil(scope / 7)` of them.

The effect is a rolling weekly refresh: every in-scope ticker is re-detected about once a week, with
bounded runtime and bounded SEC load per night.

**Never-detected tickers drain through the same cap rather than bypassing it.** Because the sort puts nulls
first, a new ticker is already at the head of the queue. Adding a separate "never detected" trigger would
un-defer exactly the tickers the cap just deferred, and the cap would then bound nothing: the first run
after an index rebuild would try to detect the entire in-scope backlog. `force` remains the deliberate
escape hatch for that.

The cap is computed from the detection scope, not from the working set or the price universe. Sizing it
against a wider set would oversubscribe the nightly budget.

## Jump-Triggered Detection

A ticker not scheduled tonight is still detected when all of these hold: it is in the detection scope, it
has newly loaded prices, and any of its last six rows shows an overnight raw close move exceeding 25%.

This is the one event-driven trigger, and it exists because the XBRL path is structurally late. A split's
share-count evidence does not appear until the next quarterly cover page, so waiting for the rolling
refresh means serving wrong prices for up to a week and wrong *unadjusted* prices for months. A 25%
overnight move is the signature of a just-effective split.

Six rows rather than one pair: a multi-day catch-up load can bury the split day in the middle of a batch,
and checking only the newest pair would miss it.

The gate on newly loaded prices matters. Without it, a ticker with a historical 25% move would re-detect
every single night forever.

## Detection Stamping

A successful detection stamps `listings.last_sec_detection_at`, which is what advances the rolling queue.

**A failed equity detection deliberately does not stamp.** If the SEC facts fetch failed, the ticker stays
stale and is retried on the next run, instead of waiting out a full week having detected nothing. Fund
detection stamps unconditionally, which is an asymmetry: an ETF run that skipped every filing for identity
reasons still counts as a detection.

## Adjustment Arithmetic

### The no-actions case

A ticker with prices and no corporate actions gets `adjusted = raw`. This is the same write as the
no-asset case and for the same reason: it is the correct answer and it clears the null signal.

### Effective-date snapping

An action dated on a non-trading day cannot match a price row. Rather than dropping it, the action is
snapped forward to the next trade date at or after its effective date, and the snap is counted.

This matters because ex-dates are frequently *derived*, not observed: a synthesized ex-date or a
settlement-shifted record date can easily land on a weekend or a holiday. Dropping those actions would
silently lose a dividend from the adjustment while leaving the row in the database, which is the worst
combination available (the action looks present and has no effect).

An action effective after the newest price row is skipped rather than snapped, since there is nothing yet
to adjust.

### The backward walk

Prices are walked newest to oldest with a running factor, applied to every row *before* the action's
apply date:

```
for each price row, newest first:
    write adjusted OHLC = raw OHLC * cumulativeFactor
    for each action applying on this date, splits before dividends:
        SPLIT:    cumulativeFactor *= ratio
        DIVIDEND: cumulativeFactor *= (1 - dividendCash / priorTradingClose)
```

The row on the action's apply date is written with the factor as it stands *before* that action is
applied, which is exactly the effective-date convention: the apply date is the first trade date at the
new basis, so it needs no adjustment for that action, while everything before it does.

The factor is accumulated in `BigDecimal` at `DECIMAL64`. A ticker's history can carry dozens of
multiplications, and double-precision drift compounds through all of them. Written values are rounded to
four decimal places.

### The dividend factor's scale requirement

The dividend factor is `1 - cash / priorTradingClose`, where `priorTradingClose` is the **raw** close of
the previous trading day and `cash` must be on that same scale.

This is why the raw dividend amount is preferred over the adjusted one. The adjusted amount is on the
current share basis after forward-split scaling, while `priorTradingClose` is a historical raw price.
Mixing them produces a factor wrong by the product of intervening split ratios. Fallbacks to the adjusted
amount and then to the ratio field exist for ETF rows and legacy rows where the raw amount was never
persisted, and those fallbacks are safe precisely because neither source restates for splits.

Two guards skip a dividend rather than produce nonsense: a missing or non-positive prior close (the first
row of a series has no prior close), and cash that is not positive or is greater than or equal to the
prior close (which would drive the factor to zero or negative).

### Same-day split and dividend

Splits are applied before dividends on a shared date, and the combination is logged as a warning because
it is known to be mis-scaled: the dividend factor divides by the pre-split prior close while the dividend
cash may already be on the post-split scale, so the combined factor can be off by the split ratio. The
warning is an admission, not a fix.

### Write minimization

A row is written only when at least one adjusted value actually changes. Without this, every nightly run
would rewrite the entire price history of every processed ticker, which is tens of millions of rows for no
information gain. With it, a normal night writes the new rows plus any row whose factor genuinely moved.

## Failure Isolation

A ticker that throws during detection or adjustment is counted and skipped, leaving its adjusted columns
untouched. Null rows stay null, so the ticker reappears in tomorrow's working set. The failure mode this
avoids is worse than a retry: writing `raw` into `adjusted` for a ticker whose actions failed to load
would freeze wrong values in place and clear the signal that says they need recomputing.

An interrupt breaks the loop and re-sets the interrupt flag, so a task shutdown stops promptly.

A 100ms sleep follows each SEC detection, on top of the client's own rate limiting.

## yfinance Validation

`validateWithYfinance` attaches diagnostic comparison reports and is never enabled by any scheduled path.
It exists for development. Nothing it returns is persisted; see the licensing constraint in the HLD.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Working-set signal | Null adjusted columns | A dirty flag; a last-adjusted timestamp | The null is written by the ingest segment as a side effect of inserting, so it needs no extra bookkeeping and cannot drift out of sync with the rows it describes. |
| Tickers with no SEC asset | Write `adjusted = raw` | Leave the columns null | A symbol with no SEC counterpart has no discoverable actions, so raw *is* adjusted. Leaving nulls would put it in the working set every night forever. |
| Detection scope | Members of one configured index with prices | Full price universe; a hand-maintained list | 24k symbols against a per-process rate limit is a multi-day pass, and most of those symbols have no SEC counterpart. An index is already maintained for other reasons. |
| Empty configured index | Fail closed, detect nothing, log at error | Fall back to the full universe | The fallback is exactly the run the scope exists to prevent. A missed night is recoverable; a multi-day SEC-hammering run during a rebuild is not. |
| Re-detection cadence | Rolling, oldest first, `ceil(scope / 7)` per night | All in-scope tickers nightly; a fixed count | Bounds nightly runtime and SEC load while guaranteeing every in-scope ticker is refreshed about weekly. A fixed count would not scale with the index. |
| Never-detected tickers | Drain through the same cap, nulls sorted first | A separate trigger that bypasses the cap | A bypass un-defers what the cap just deferred, so the cap would bound nothing and the first run after an index rebuild would attempt the whole backlog. |
| Cap denominator | The detection scope | The working set; the price universe | The candidates are drawn from the scope, so sizing against anything wider oversubscribes the budget. |
| Event-driven trigger | Overnight raw move above 25% on newly loaded prices | Wait for the rolling refresh; a lower threshold | Split evidence in XBRL is months late, so same-day detection needs a price signal. Requiring newly loaded prices stops a historical move from re-triggering nightly. |
| Jump lookback | Last six rows | The newest pair only | A multi-day catch-up load can bury the split day mid-batch, where a newest-pair check would never see it. |
| Failed equity detection | Do not stamp the detection timestamp | Stamp regardless | A failed fetch that stamps would wait out a full week having detected nothing. Not stamping retries tomorrow. |
| Action on a non-trading day | Snap forward to the next trade date | Drop it; snap backward | Derived ex-dates land on non-trading days routinely. Dropping leaves the row present and inert, which is undetectable. Snapping backward would place the action before its own effective date. |
| Factor accumulation | `BigDecimal` at `DECIMAL64` | `double` | Dozens of multiplications per ticker compound double-precision drift, and the result is compared against a reference series to four decimal places. |
| Dividend cash source | Raw amount preferred, adjusted then ratio as fallbacks | Always the adjusted amount | The factor divides by a raw historical close, so the cash must be raw. The adjusted amount is on the current share basis and would be wrong by the intervening split ratios. |
| Same-day split and dividend | Splits first, warn about mis-scaling | Silently proceed; skip one of them | The interaction is genuinely wrong and unfixed. A warning at least makes the affected tickers findable; silence would not. |
| Write policy | Only rows whose adjusted values changed | Rewrite every row every run | Rewriting whole histories nightly is tens of millions of writes for no new information. |
| Per-ticker failure | Count, skip, leave columns null | Abort the run; write raw as adjusted | Leaving nulls preserves the retry signal. Writing raw as adjusted would freeze wrong values and clear the signal that says they are wrong. |
| yfinance validation | Off in every scheduled path, opt-in for development | Always on; removed | Accuracy work needs a reference, and the licensing constraint forbids persisting or serving it. Keeping it opt-in keeps it out of production paths structurally. |

## Open Questions & Future Decisions

### Resolved

1. ✅ Whether an empty detection index should fall back to the full universe: no, fail closed.
2. ✅ Whether never-detected tickers should bypass the nightly cap: no, they sort to the head of it.
3. ✅ Whether a failed detection should stamp the timestamp: no, so the ticker is retried tomorrow.
4. ✅ Whether an action on a non-trading day should be dropped: no, snapped forward.

### Deferred

1. A split and a dividend sharing an effective date produce a mis-scaled dividend factor, off by the
   split ratio. Currently only warned about. Fixing it means either restating the dividend cash to the
   pre-split basis before dividing, or ordering the factor updates so the dividend divides by a
   post-split prior close. Neither is chosen, and no test asserts the current behavior either way.
2. Fund detection stamps `last_sec_detection_at` unconditionally, while equity detection stamps only on
   success. An ETF run that skipped every filing for identity reasons therefore looks like a successful
   detection and will not be retried for a week.
3. The jump trigger reads raw closes with no positivity filter beyond skipping non-positive rows, so a
   bad print can trigger a full SEC detection for a ticker with no corporate action. Cost is wasted
   fetches rather than wrong data, so it is unaddressed.
4. `snappedActions` is counted and reported but never attributed. A rising snap count means more derived
   ex-dates are landing off-market, which is a quality signal about the upstream assigner, and nothing
   currently reads it that way.
5. The 100ms sleep after each detection is additive to the SEC client's own rate limiting, and the total
   effective rate is not stated anywhere. Whether the sleep is still needed is untested.
6. Rounding to four decimal places is applied to written values but not to the accumulated factor, so a
   long action history produces adjusted prices whose last digit depends on the whole chain. This is
   probably correct (rounding the factor would compound error) but it is not stated as intent, and the
   validation segment's tolerance has to absorb it.
7. Nothing detects a ticker whose adjusted series was computed from actions that have since been deleted.
   The working set is driven by null columns and scheduled detections, so a pruned action leaves stale
   adjusted values until the ticker is next detected and its factors happen to change.

## References

- [High-Level Design](../../high-level-design.md): the effective-date convention and the
  commercially-free-data constraint on yfinance.
- [`hist-ingest`](../hist-ingest/hist-ingest-design.md): writes the null adjusted columns that select
  this segment's working set.
- [`equity-actions`](../equity-actions/equity-actions-design.md): shares the effective-date convention and
  supplies raw and adjusted dividend amounts.
- [`etf-distributions`](../etf-distributions/etf-distributions-design.md): supplies fund rows whose raw
  and adjusted amounts are equal.
- [`price-validation`](../price-validation/price-validation-design.md): measures whether this segment's
  output is right.
- `index/`: maintains the membership this segment scopes detection to.

---
parent: high-level-design
prefix: HIST
---

# HIST Ingest

## Context and Design Philosophy

This segment turns IEX HIST daily capture files into raw OHLCV rows in `daily_prices`. It is the
head of the pipeline: every downstream segment (split corroboration, price adjustment, validation)
reads what this one writes, and nothing here depends on SEC data.

Three properties shape the design.

**The unit of work is a trading day, not a ticker.** IEX publishes one TOPS capture per trading day
covering every symbol it traded. A day's ingest is therefore one download and one pass, producing
roughly 24k rows. There is no per-ticker fetch path and no reason to want one.

**The files are large and the parse is CPU-bound, so the two overlap.** A TOPS capture runs to
hundreds of megabytes compressed. Downloading day `n+1` while parsing day `n` keeps both the network
and the CPU busy, which is the difference between a task that finishes inside its nightly window and
one that does not.

**Idempotency is the error-handling strategy.** A day already present in `daily_prices` is skipped
outright, so a partial run is repaired by running again rather than by tracking progress. This is
why per-day failures do not fail the task: the next night retries them for free.

Written rows carry raw OHLCV only. The adjusted columns are deliberately left NULL, because NULL is
what the adjustment segment uses to select its working set. Filling them here would break that
signal.

## Day Selection

Candidate days come from the HIST index (`https://iextrading.com/api/1.0/hist`), a JSON map from
`yyyyMMdd` to the list of feeds published for that date. Only the entry whose `feed` is `TOPS` is
used; a date present in the index with no TOPS entry counts as *not available* rather than as an
error.

Selection walks backwards from yesterday in market time, taking days that are both a trading day by
the local calendar and present in the index, until `app.hist-load.days` (default 20) are collected or
`days * 3` calendar days have been examined. Starting at yesterday rather than today reflects that a
session's capture is not published until after the close.

**The index is the authority on whether a day traded; the local calendar is only a filter.** The
calendar covers weekends, four fixed-date holidays (January 1, June 19, July 4, December 25) and five
floating ones (MLK, Presidents, Memorial, Labor, Thanksgiving). It does not model Good Friday, and it
does not shift a holiday that falls on a weekend to its observed weekday. Neither gap causes a wrong
ingest, because a day the market did not trade does not appear in the index and is dropped by the
second half of the conjunction. The calendar exists to keep the candidate list short, not to be
correct on its own.

The 20-day default window is a re-ingest tolerance, not a backlog: 19 of those days are normally
already present and skipped, so a task that fails for a fortnight still self-heals on its next
success.

## Download and Parse Pipeline

```
index fetch ──▶ filter to unstored TOPS days ──▶ [day 0 download]
                                                       │
     ┌─────────────────────────────────────────────────┘
     ▼
  join download(n) ──▶ start download(n+1) ──▶ parse(n) ──▶ aggregate(n) ──▶ saveAll(n)
     ▲                                                                          │
     └──────────────────────────────────────────────────────────────────────────┘
```

Downloads run on a single-threaded daemon executor, so at most one is in flight and the prefetch
cannot outrun the parser by more than one day. Each file lands in a temp file that is deleted in a
`finally` block whether the day succeeded or failed, because a failed day must not leave hundreds of
megabytes behind on an ephemeral task's disk.

The first download is started before the loop, under the same non-empty check that gates the loop.
The pairing is asserted rather than assumed, so an edit that separates them fails loudly instead of
dereferencing null part way through an ingest.

When a day throws, the prefetch for the following day is restarted before continuing, so one bad day
costs its own work and not the pipelining of everything after it.

## Trade Aggregation

Trade Report messages are streamed out of the capture (see `util/PcapParser`, which owns the binary
format) and folded into one accumulator per symbol:

| Field | Rule |
|---|---|
| `openPrice` | price of the trade with the lowest timestamp |
| `closePrice` | price of the trade with the highest timestamp |
| `highPrice` | maximum trade price |
| `lowPrice` | minimum trade price |
| `volume` | sum of trade sizes |

Open and close are decided by timestamp order rather than arrival order, so the result does not
depend on how the capture happens to be laid out. The comparison is strict, so when several of a
symbol's trades share the extreme timestamp the first one encountered wins: a symbol whose trades all
carry one timestamp gets an open price equal to its close price. That degenerate case falls back to
capture order, and it is the only case that does. A symbol with no trades produces no row at all
rather than a row of zeroes.

**Prices are assumed positive, and that assumption is not currently enforced.** Every Trade Report
contributes to OHLC and volume with no domain check, so a zero or negative print is stored.

The consequences are uneven downstream. Split corroboration is defended: it drops rows without a
positive close, so a non-positive close becomes a gap in its series rather than a poisoned ratio. What
is not defended is a *wrong but positive* close, which manufactures an overnight break that looks like
a split, and non-positive values in the other three OHLC fields, which the adjustment pass rescales
without any positivity filter. Rejecting non-positive prices at aggregation is the intended behavior
and is an open gap.

This is an IEX-only view of the tape. IEX is one venue with single-digit market share, so these
prices are that venue's prints, not a consolidated national best bid and offer. The series is
internally consistent, which is what corporate-action detection needs from it (an overnight ratio
between two closes on the same venue), and it is what is available for free.

## Day Completeness

A day's rows are written in one atomic batch, and that atomicity is what makes the skip check sound.

The completeness marker is existence: `existsByTradeDate` returning true means the date is finished.
That is a strong claim from a weak signal, and it holds only because the day's ~24k rows commit or
roll back together. A write path that committed a day in chunks would leave a date that is present
but incomplete, and because the skip check runs before the download, no later run would ever notice.
The date would stay permanently short by however many symbols were missing, silently, forever.

So the single-batch write and the existence-based skip are one decision in two places, not two
independent ones. Changing either without the other breaks the segment. If the batch is ever split
for memory or throughput reasons, the completeness marker has to become something the partial state
cannot forge: an expected-symbol-count check, or a per-day status row written last.

## Job Semantics

The runner is active only when `app.run-mode=hist-load`, so the same image serves the API in its
default mode. It is an `ApplicationRunner`: the context boots as a normal web application, the work
runs once, and the process then terminates itself through `SpringApplication.exit` so shutdown hooks
run before the container stops.

After a successful load the runner invokes price adjustment in the same process, with `force=false`
so only rows with NULL adjusted columns are recomputed. `app.hist-load.adjust-enabled=false` skips
that phase. `app.hist-load.equity-only=true` restricts it to non-fund tickers, which the scheduled
task sets because ETF detection fetches hundreds of filings per fund against a per-process SEC rate
limit and would otherwise dominate the run. yfinance validation is never enabled from this path.

The exit code is the task's success signal to EventBridge:

| Condition | Exit |
|---|---|
| Load and adjustment completed | 0 |
| Some days failed, at least one processed | 0 |
| Individual tickers failed during adjustment | 0 |
| The load threw | 1 |
| Every attempted day failed | 1 |
| The adjustment phase threw | 1 |

The zero-exit rows are all cases the next run repairs on its own: skipped days are detected by
`existsByTradeDate`, and unadjusted tickers are detected by their NULL adjusted columns. The
non-zero rows are cases where nothing progressed, which is worth waking someone for.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Ingest granularity | One whole trading day per unit of work | Per-ticker fetch; per-symbol backfill | IEX publishes per-day captures covering all symbols. A per-ticker path would mean ~24k requests to reconstruct what one file already holds. |
| Overlapping download and parse | Prefetch day `n+1` on a single-thread executor while parsing day `n` | Fully sequential; parallel multi-day downloads | Sequential wastes the parse window on I/O. Unbounded parallelism buys little (the parse is the bottleneck) and multiplies temp-file disk on a small task. |
| Duplicate protection | `existsByTradeDate` skip before download | Upsert on conflict; a processed-days ledger | The skip avoids the download entirely, which is where the cost is. A ledger would be a second thing to keep true. |
| Day completeness marker | Existence of any row for the date, paired with a single atomic batch write | Expected-symbol-count check; a per-day status row written last | Existence is free and correct as long as the day commits atomically. The stronger markers become necessary only if the batch is ever split, which is why the two decisions are coupled explicitly rather than left to be rediscovered. |
| Price domain checks at aggregation | None today; rejecting non-positive prints is the intended behavior | Trust the feed permanently; validate every field against a plausibility band | A non-positive close does not just store a wrong number, it fabricates an overnight ratio for split detection to find. The downstream median-persistence check catches glitches after the fact, which is weaker than refusing the print. |
| Per-day failure handling | Count, continue, exit 0 if anything succeeded | Abort the run on first failure | Days are independent and the load is idempotent, so a transient failure on one day should not discard the others or mark the task failed. |
| Adjusted columns on insert | Left NULL | Copy raw into adjusted as a placeholder | NULL is the working-set signal for the adjustment segment. A placeholder would make an unadjusted row indistinguishable from an adjusted one. |
| Trading-day calendar | Local approximation, with the index as the authority | Full exchange calendar library; index only | The index already excludes non-trading days, so the calendar only has to shrink the candidate list. A dependency for exact holiday rules would buy no correctness here. |
| Open and close selection | Earliest and latest trade by message timestamp | First and last message in file order | Capture order is an implementation detail of the feed. Timestamp order is the property actually intended. |
| Adjustment invoked in-process after the load | Same task, sequential | A separate scheduled adjustment task | Detection needs the day's prices to corroborate splits, so an independent schedule would race the load. One task makes the ordering structural. |

## Open Questions & Future Decisions

### Resolved

1. ✅ Whether to fail the task when some days fail: no, as long as one day was processed. The load is
   idempotent and the next run retries.
2. ✅ Whether the holiday calendar needs to be exact: no. The HIST index is authoritative for whether
   a day traded, and the calendar is only a candidate filter. The complete NYSE calendar in the
   filing-evidence segment has no counterpart here for the same reason, and the adjustment segment
   needs neither because it snaps to observed trade dates.
3. ✅ Where the non-positive-price guard belongs: here, at aggregation, so such a print never reaches
   `daily_prices`. Split corroboration keeps its existing filter as defense in depth; the adjustment
   pass adds none, since it would be unreachable. The accepted cost is that stored prices no longer
   mirror the feed byte for byte.

### Deferred

1. `util/PcapParser` owns the binary decoding this segment depends on, and it sits outside the
   declared LID scope. Specs about trade extraction therefore have no in-scope annotation site.
   Either widen the scope to include it or accept that the segment's specs stop at the parser
   boundary.
2. The IEX-only nature of the price series is not asserted anywhere. A downstream consumer could
   reasonably assume consolidated prices. Worth stating as a spec rather than leaving as an
   understanding.
3. Trade conditions are not consulted: every Trade Report contributes to OHLC and volume. Whether
   odd-lot or non-last-sale-eligible prints should be excluded is unresolved, and it affects the
   overnight ratios that split detection reads.
4. A configured window of fewer than one day produces a silent no-op: no candidates, all counters
   zero, exit 0. That is indistinguishable from a night on which every candidate was already stored.
   Whether a nonsensical window should fail the task is undecided.
5. Two captures can occupy the temp directory at once, one being parsed and one being prefetched, so
   the task's ephemeral storage ceiling is a real constraint that no code or config asserts.

## References

- [High-Level Design](../../high-level-design.md): tenets and the pipeline's overall approach.
- `util/PcapParser`: IEX TOPS pcap and pcapng decoding, Trade Report extraction.
- `util/MarketTime`: `MARKET` zone for trading-day questions, `STORAGE` for audit timestamps.
- [`price-adjustment`](../price-adjustment/price-adjustment-design.md): consumes the NULL adjusted
  columns this segment leaves behind.

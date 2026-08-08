---
parent: high-level-design
prefix: EQUITY
---

# Equity Corporate Actions

Facets: `EQUITY-SPLIT-*` for split detection, `EQUITY-DIV-*` for dividend detection, bare
`EQUITY-*` for orchestration shared by both.

## Context and Design Philosophy

This segment answers "what corporate actions has this equity had" from SEC XBRL company facts, filing
text, and the raw price series. It is the largest segment in the pipeline and the one where a wrong
answer is most expensive: its output is the input to price adjustment, which rewrites history.

**Splits and dividends live together because they are not independent.** They come from one XBRL fetch.
They are detected in a fixed order, because a dividend's stored amount depends on which splits are
known: a pre-split dividend has to be restated onto the current share basis, and that restatement needs
the split rows to exist first. Separating them would make that ordering a cross-segment contract instead
of a local one.

**The two halves guard against opposite errors.** A false split rescales the whole prior series by a
multiple and compounds with every other action, so split detection refuses to persist on thin evidence
and treats a covered price window with no break as proof of absence. A missing dividend leaves a
permanent hole in yield and total-return history while a misdated one costs a fraction of a percent, so
dividend detection persists everything it finds and accepts a synthesized date rather than dropping the
event. Both follow from the HLD tenet on multiplicative versus additive corrections; neither is a
general rule about confidence thresholds.

**Absence of evidence is only evidence of absence when the evidence source was healthy.** A transient
SEC outage makes filings unfetchable, which makes declarations look absent, which would otherwise
license overwriting a well-anchored stored date with a guess and pruning rows as orphans. The scan
therefore reports its own reliability, and a degraded scan loses the right to delete or to insert
guesses.

## Orchestration

`detectAndPersistWithDiagnostics(ticker, cik)` is the segment's entry point and the only caller of both
halves.

```
beginSecTickerScopedCache()
  ├── fetchFinancials(cik) ──▶ parse XBRL facts        [failure ⇒ report "sec_fetch_failed", stop]
  ├── detectSplits(ticker, cik, root)                  [must precede dividends]
  └── detectDividends(ticker, cik, root)
endSecTickerScopedCache()                              [always, including on failure]
```

The whole detection runs inside a **ticker-scoped SEC document cache** opened and closed here. This is
what makes the filing-evidence segment's repeated document walks affordable: the record-date scan, the
ex-date scan, and the declaration-tuple scan all traverse the same filings, and only the first traversal
costs HTTP. The cache is opened and closed in a `try`/`finally` around the entire detection, so a
failure cannot leak a ticker's documents into the next ticker's scan.

A failed XBRL fetch is not an error to propagate. It returns a report whose failure reason is
`sec_fetch_failed` and whose statistics are empty, so the caller can decide (and, in the adjustment
segment, it decides not to stamp the ticker as freshly detected).

The report is the segment's second output and is not decoration. Split statistics separate the ways a
candidate can end (`priceCorroborated`, `priceSnapped`, `priceRejected`, `priceOnlyDetected`,
`priceOnlyUnconfirmed`), and dividend statistics separate assignment paths
(`tupleMatchedAssignments`, `directExAssignments`, `dpAssignments`, `syntheticAssignments`,
`promotedTupleEvents`). Because detection has no per-event failure mode, these counters are the only way
to see quality changing: a rise in `syntheticAssignments` or `priceOnlyUnconfirmed` is how a regression
in the filing-evidence layer becomes visible here.

## Split Detection

### Ratio candidates from share counts

Shares-outstanding facts come from `dei:EntityCommonStockSharesOutstanding`, restricted to the forms
that carry a reliable cover-page count. Consecutive entries in date order give a ratio of
`newShares / oldShares`, which is snapped to a known split ratio within 2%.

| Ratio class | Values | Persistence requirement |
|---|---|---|
| Primary | 2, 3, 4, 5, 7, 10, 20, 50 and their reciprocals | No filing proof required |
| Extended | 1.5 (3:2), 4/3 | Requires a filing split-date match or a price break |

Extended ratios are separated because a 3:2 move is only a 33% overnight change, which overlaps
ordinary large gaps and buyback-driven share-count drift. A 2:1 move does not.

Share-fact pairs more than 400 days apart are not treated as adjacent, because a gap that long can hide
more than one event and the ratio between its endpoints is not attributable to a single split.

### Dating, in precedence order

The effective date is **the first trade date at the new price basis**, which is the same date the
adjustment pass assigns the pre-action factor to. Holding detection and adjustment to one convention is
what drives the residual level error at a break to zero.

| Source | `exDateSource` | Confidence | How |
|---|---|---|---|
| Price break | `PRICE_BREAK` | 100 | The overnight `prevClose/close` move matching the multiplier, inside the share-fact bracket padded 10 days and widened to include a matched filing candidate |
| Filing text | `FILING_TEXT` | 70 | A split-date candidate from the filing-evidence scan, within 260 days before to 60 days after |
| Share fact | `SHARE_FACT` | 40 | The date of the later share-count fact |

A price break is preferred over a company's own stated effective date because the break *is* the
convention: it is by construction the first trade at the new basis, whereas filing prose may state a
board effective date, a distribution date, or a payable date. When a found break and a filing candidate
disagree by more than seven days, the disagreement is logged rather than silently resolved.

Corroboration tolerances are computed in log space so forward and reverse splits are symmetric: ±15%
for multipliers at or beyond 2x (or at or below 0.5x), ±6% for extended ratios.

### The false-positive veto

If the price series **fully covers** the search window and no overnight move matches the multiplier, the
candidate is rejected and counted as `priceRejected`. The share-count jump was a buyback, an issuance,
or a reporting artifact.

Full coverage is the load-bearing condition. Absence of a break only means "no split" when prices for
the whole window are present; a window that predates the price history proves nothing, so the veto does
not apply there. This is the segment's cleanest example of a guard that has to know its own blind spot.

### Reconcile rather than insert

An existing split with the same ratio (±1%) within ±90 days is **re-dated in place** when the new
resolution is at least as well grounded. Inserting at the new date instead would leave both rows live,
and the adjustment pass would apply both, halving or doubling the whole prior series. Correcting a date
must never be able to produce two splits where the company had one.

### Price-first detection

After the XBRL pass, the raw series is scanned for overnight moves that snap within 10% to a plausible
multiplier ({2,3,4,5,7,10,20,50} and reciprocals) and whose five-day median levels either side confirm
the move persisted within 25%. The persistence check is what separates a split from a one-day glitch or
a crash that recovered.

Breaks already explained by a persisted split (±7 days) are skipped. The rest persist as:

- `SEC_PRICE_CORROBORATED`, confidence 90, when a SEC filing split-date candidate lies within ±14 days.
- `SEC_PRICE_CORROBORATED`, confidence 60, on price evidence alone, but only for multipliers at or
  beyond 5x (or at or below 0.2x), where no ordinary market move is a plausible explanation.
- Nothing at all otherwise: a mid-size unexplained break only increments `priceOnlyUnconfirmed`.

This path exists because the XBRL path is structurally late. A share-count change appears in the next
quarterly cover page, so an XBRL-only pipeline discovers an August split in November. Price-first
detection makes the adjustment segment's large-overnight-move trigger actionable on the day the split
happens.

## Dividend Detection

### Facts to events

Per-share dividend concepts are read from `us-gaap` facts, preferring
`CommonStockDividendsPerShareDeclared`, `CommonStockDividendsPerShareCashPaid`,
`CommonStockDividendsPerShareDeclaredAndPaid`, and `DividendsPaidPerShare`, restricted to USD per-share
units. Facts are grouped by fiscal period end, and one event is selected per period, preferring
quarter-length periods (up to 120 days), then form priority, then the earliest filing.

Classification into regular and special matters because they are matched to dates differently: regular
events carry a quarterly cadence prior that the DP path uses, special events do not. An annual-length
period (250 days or more) is not a quarterly event, and a Q4 amount jumping more than 2.5x the running
regular amount is treated as containing something other than the regular dividend.

If normalization yields no events, the segment stops before scanning any filings. There is nothing to
assign dates to, and the filing scan is by far the most expensive step.

### Ex-date assignment, in precedence order

All four paths are tried in order, and each candidate or tuple is consumed at most once.

**Path 0, amount-anchored tuple match (`TUPLE_MATCHED`, confidence 95).** A declaration tuple whose
amount matches the event's raw XBRL amount, as stated or after undoing later split restatement
(`declared = raw / product of later split ratios`), and whose record date falls 0 to 95 days after the
fiscal period end. Amount tolerance is the greater of 0.0005 absolute and 0.5% relative. Ties prefer an
offset near 45 days, then higher tuple confidence. The ex-date is the tuple's stated ex-date when
present, otherwise derived from its record date; record and payable dates are carried onto the row.

This path is the point of the whole tuple machinery: it replaces cadence inference with the dates the
company published, tied to the specific 8-K by dollar amount.

**Path A, direct ex-date text (`DIRECT_EX_TEXT`, confidence 90).** For regular events, an extracted
ex-date candidate 5 to 130 days after the fiscal period end, scored by `|gap - 45| - confidence/25`.

**Path B, record-date dynamic programming (`RECORD_DP`, confidence 60).** For events still unmatched, a
globally optimal assignment of record-date candidates to events, rather than greedy per-event matching.
Eligibility: record date 10 to 80 days after the fiscal period end, and a filing date not earlier than
five days before the period end. Cost: `|offset - 42| + |cadenceGap - 91| / 2 - confidence / 12`, with a
140-point penalty for skipping an event.

Global optimization is the point. Quarterly dividends are a sequence, and a greedy match that takes the
wrong candidate for Q1 forces wrong candidates for Q2 and Q3 behind it. The skip penalty is high enough
that leaving an event unassigned is a last resort, which is the same asymmetry the segment applies
everywhere: a dated event beats an undated one.

**Fallback, synthetic (`SYNTHETIC`, confidence 10).** A remaining event gets a record date inferred from
the last matched one plus 91 days, or the fiscal period end plus 42 days, and an ex-date derived from
that. The event is persisted because it is real and disclosed; the low confidence and the source tag are
what keep a better-grounded later run free to correct it.

Special dividends are matched separately against unused candidates with a simpler best-match rule, since
they have no cadence to exploit.

### Declaration promotion

A dividend is declared in an 8-K weeks before the 10-Q reports it as an XBRL fact, so between those two
filings the event exists publicly and has no fact to attach to. A high-confidence declaration tuple
whose record date is *after* the newest XBRL fiscal period end is therefore promoted to a provisional
event, counted as `promotedTupleEvents`.

Three guards keep promotion from inventing dividends:

- Tuple confidence at least 85, so only well-formed declarations qualify.
- Record date within 200 days, which scopes promotion to the current blind window instead of
  reconstructing history from press releases.
- Amount within a 3x band of the newest regular amount, which catches a misparsed figure (an annual
  total, a share price, a per-unit distribution) before it becomes a dividend row.

### Split restatement of amounts

Every detected event's amount is restated onto the current share basis by dividing by the product of
split ratios effective after it. Both figures are kept: `rawDividend` as the company declared it and
`adjustedDividend` on today's basis. Keeping both is what lets a newly detected split trigger a
recomputation instead of a lossy in-place rewrite, and it is why the adjustment pass can use the raw
amount against the raw prior close.

### Persistence

Matching is exact first (same ticker, date, and amount), then year-scoped with an approximate amount,
which is what allows a re-detection to correct a date rather than duplicate the event.

**Provenance priority is enforced on every write.** Ranks are `TUPLE_MATCHED` 4, `DIRECT_EX_TEXT` 3,
`RECORD_DP` 2, `SYNTHETIC` 1, unknown 0. A re-detection may move a stored date only when its rank is at
least the stored rank. Amounts still update either way, because an amount correction is unambiguous
while a date move is a claim about better evidence. Without this rule a single night of failed filing
fetches would walk every anchored date back to a synthetic guess, permanently.

Pruning removes rows that detection no longer produces, but only within the detected year range, only
for rows this segment created (`SEC_EQUITY_XBRL`), and never for rows whose rank is 3 or higher. The
last exclusion closes a bypass: deleting a declaration-anchored row and inserting a synthetic one would
achieve the date move the rank guard forbids. Anchored orphans are logged for manual review instead.

A unique-constraint violation on insert is treated as a concurrent writer: the same date is re-read, and
if the event is now present the insert is abandoned quietly.

### Degraded-scan suppression

When the record-date scan reports itself degraded, two behaviors are withheld for that run:

- Inserting an event whose ex-date source rank is 1 or lower (synthetic or unknown).
- Pruning orphans at all.

Updates to existing rows continue, since those are matched to events rather than inferred from absence.
The rule follows from what a degraded scan actually means: candidate lists are incomplete, so "no
declaration for this event" and "no event for this row" have both stopped being informative.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Splits and dividends in one segment | One orchestrator, one XBRL fetch, fixed order | Separate segments per action type | Dividend amounts depend on known splits, so the ordering is a real dependency. Splitting them would turn it into a cross-segment contract and duplicate the fetch. |
| Split effective-date convention | First trade date at the new price basis | Filing-stated effective date; distribution date; record date | It is what the adjustment pass keys on and what a price break identifies. One convention across detection and adjustment is what makes the residual error at the break zero. |
| Price break outranks the company's stated date | Price break at confidence 100 | Trust filing text first | Filing prose states board, distribution, or payable dates that are not the convention. The break is the convention by construction. |
| No break in a covered window | Reject the candidate | Persist at low confidence | Share-count jumps also come from buybacks and issuance. A false split is multiplicative and corrupts everything before it, so a covered window with no break is treated as proof of absence. |
| Coverage requirement on the veto | Veto only applies when prices span the whole window | Always veto on a missing break | Outside the price history, absence of a break is absence of data. A veto that cannot tell those apart would delete real old splits. |
| Existing split at a corrected date | Re-date in place when at least as well grounded | Insert the new date and leave the old row | Two live rows for one event double-adjust the entire prior series. A date correction must never be able to produce that. |
| Extended ratios (3:2, 4:3) | Require filing or price confirmation | Treat like primary ratios | A 33% move is inside the range of ordinary gaps and share-count drift, so the ratio alone is not evidence. |
| Price-first split detection | Scan for unexplained breaks, persist with graded confidence | Rely on XBRL share counts only | Share counts arrive with the next quarterly cover page, so an XBRL-only pipeline is months late. This makes same-day detection possible. |
| Price-only split without filing support | Persist only at 5x or beyond | Persist any snapped break; persist none | At 5x no ordinary market move is a plausible alternative explanation. Below it, the risk of adjusting on a crash outweighs the missed event. |
| Undatable dividend | Persist with a synthetic date at confidence 10 | Skip the event | A missing dividend is a permanent hole in yield and total-return history. A bounded date error is recoverable, and the low rank invites correction. |
| Ex-date assignment strategy | Tuple, then direct text, then global DP, then synthetic | Cadence inference only; greedy matching only | Amount-anchored declarations are read rather than inferred. DP exists because quarterly events are a sequence where one greedy mistake cascades. |
| Amount storage | Persist raw and split-adjusted amounts side by side | Store only the adjusted amount | The raw amount is what pairs with the raw prior close during adjustment, and keeping it makes a newly detected split a recomputation rather than a lossy rewrite. |
| Date overwrite policy | A weaker-ranked re-detection may not move a stored date | Latest detection always wins | Self-healing on recency means one night of SEC failures degrades every anchored date to a guess, irreversibly. |
| Pruning scope | Detected year range, own source type, rank below 3 | Prune every unmatched row | Narrow scope keeps detection from deleting other sources' rows or history it did not examine. The rank exclusion closes the delete-and-reinsert bypass around the date-move guard. |
| Degraded scan | Suppress synthetic inserts and all pruning | Proceed normally; abort the ticker | Both suppressed behaviors infer from absence, and a degraded scan is exactly when absence is uninformative. Updates are still safe because they are matched, not inferred. |
| Declaration promotion | Promote high-confidence tuples past the newest fact | Wait for the 10-Q; promote all unmatched tuples | Between the 8-K and the 10-Q a real dividend has no fact to attach to. Confidence, recency, and amount-band guards keep a misparse from becoming a dividend row. |
| SEC document cache scope | Per ticker, opened and closed around detection | Global cache; no cache | Three scans traverse the same filings, so a per-ticker cache removes two thirds of the HTTP. A global cache would grow unbounded across ~24k tickers. |

## Open Questions & Future Decisions

### Resolved

1. ✅ Whether splits must be detected before dividends: yes. Amount restatement needs the split rows.
2. ✅ Whether a corrected split date may be inserted rather than re-dated: no. Two live rows
   double-adjust the series.
3. ✅ Whether a dividend with no establishable date should be persisted: yes, tagged synthetic and
   low-confidence.
4. ✅ Whether a later run may overwrite a better-grounded date: no, and the pruning path is closed
   against the same move by deletion.

### Deferred

1. `priceOnlyUnconfirmed` breaks are counted and discarded. A mid-size unexplained overnight move is
   either a missed split or a real price move, and nothing records which it was, so the counter cannot
   currently be acted on.
2. Anchored orphans (rank 3 or higher, no longer detected) are logged for manual review, but there is no
   review mechanism, no queue, and no report. In practice they accumulate unseen.
3. The DP cost function's constants (42-day target offset, 91-day cadence, the 140 skip penalty, the
   confidence divisors) have no recorded derivation or evaluation set, so it is not possible to tell
   whether a change to them improves or degrades assignment.
4. Split detection reads `dei:EntityCommonStockSharesOutstanding`, a cover-page count as of the filing
   date rather than the period end. Its timing relative to a split is approximate, which is part of why
   the price break outranks it, but the residual effect on the search bracket is unquantified.
5. Promotion creates provisional events that no later step demotes or reconciles when the 10-Q arrives
   with a different amount. The year-scoped match is expected to absorb them, but nothing asserts that
   a promoted event and its eventual XBRL fact converge to one row.
6. Reverse splits are handled by the same reciprocal ratios and log-space tolerances as forward splits,
   and no test data distinguishes them. Whether reverse splits carry different filing language or
   different price-break characteristics is unexamined.

## References

- [High-Level Design](../../high-level-design.md): the multiplicative-versus-additive tenet these two
  halves implement in opposite directions.
- [`filing-evidence`](../filing-evidence/filing-evidence-design.md): supplies record dates, ex-date
  candidates, declaration tuples, split date candidates, and the degraded-scan signal.
- [`hist-ingest`](../hist-ingest/hist-ingest-design.md): supplies the raw close series split
  corroboration reads.
- [`price-adjustment`](../price-adjustment/price-adjustment-design.md): consumes the persisted actions
  and shares the effective-date convention.
- [`equity-corporate-action-process.md`](../../equity-corporate-action-process.md): step-level
  walkthrough with worked AAPL examples.

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

Re-dating alone cannot retract a split that should never have existed, so weakly evidenced splits are
also prunable: a stored split that is no longer detected and carries confidence below 70 is deleted.
The threshold is set so that a share-fact date (40) and a price-only detection (60) can be retracted
while filing text (70), price corroboration (90) and a price break (100) cannot. The asymmetry is the
same one that governs the rest of the segment: a weakly grounded split is more likely to be a false
positive than a real event a run failed to re-derive, and a strongly grounded one is the reverse.

Pruning infers from absence, so it is suppressed while the split scan reports itself degraded, on the
same terms as dividend pruning. The XBRL and price paths do not depend on filing fetches, which limits
the exposure, but a split dated from filing text during a healthy run must not be retracted by a run
that could not read filings at all.

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
units.

**The core difficulty is that XBRL dividend facts are not one-per-quarter.** A filer reports the same
cash under several period shapes: the quarter itself, a year-to-date cumulative figure, the full fiscal
year, and sometimes an 8-K fact with no period at all. Turning that into one event per quarter is the
normalizer's entire job, and every rule below exists to stop the same dollar being counted twice or the
wrong shape being read as a quarter.

Facts are classified by the length of their reporting period:

| Class | Period length | Role |
|---|---|---|
| Quarter-length | 1 to 120 days | The event, when present |
| Intermediate | 121 to 249 days | Year-to-date cumulative; never an event |
| Annual | 250 days or more | Used only to derive a missing Q4 |
| No start date | (unclassifiable) | Usable, but only under the guard below |

Within one fiscal period end, a quarter-length fact wins, chosen by shortest period, then form priority
(10-Q, then 8-K, then 10-K, then anything else), then earliest filing.

When no quarter-length fact exists, the fallback considers only facts that are neither annual nor
intermediate, and applies one further guard: **a fact with no start date whose amount equals an
intermediate fact's amount is discarded.** An 8-K carrying no period that reports the same number as the
year-to-date cumulative figure *is* that cumulative figure, and admitting it would book three quarters of
dividends as one. Remaining candidates are ordered by having a start date at all, then preferred concept,
then form, then filing date.

### Derived Q4

A filer that reports Q1 to Q3 quarterly and then only an annual total leaves Q4 with no fact of its own.
Rather than lose the quarter, it is derived: `annual total - sum of the quarters already known inside
that fiscal year`.

Two guards keep the subtraction from inventing a dividend. A non-positive result is discarded, which is
what happens when the quarters already sum to the annual total. And the result must not exceed 2.5 times
the median of the known prior quarters, which catches the case where the "annual" fact was not actually
annual, or where a quarter is missing from the sum and the remainder absorbs two quarters of cash.

A derived Q4 fills an empty fiscal-year end outright. When a value is already present there, it is
replaced only when the stored value exceeds the derived one by more than 0.02, because that is the
signature of a cumulative figure having been mistaken for a quarter: the year-end "quarter" reads far
larger than the derived remainder. Otherwise the existing value stands.

### Regular and special

Classification matters because the two are matched to dates differently: regular events carry the
quarterly cadence prior the DP path uses, special events do not.

Against the selected regular amount for a period, another fact at the same period end is flagged special
when it is at least 2.8 times the regular amount *and* at least 0.75 higher in absolute terms, or when it
comes from an 8-K and exceeds the regular amount by more than 0.25. The absolute floor on the first rule
stops a penny-dividend issuer from generating specials out of rounding, and the 8-K rule is looser
because a special dividend is normally announced in exactly that form.

Specials are deduplicated by period end and amount, and any that collide with a regular event on both are
dropped as the same cash seen twice.

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

Every detected event's amount is restated onto the current share basis by the product of the ratios of
splits effective after it. Both figures are kept: `rawDividend` on the price scale that applied when the
dividend was paid, and `adjustedDividend` on today's basis. Keeping both is what lets a newly detected
split trigger a recomputation instead of a lossy in-place rewrite, and it is why the adjustment pass can
divide the raw amount by the raw prior close.

The split an event is measured against is anchored on its ex-date when one has been assigned, and on its
fiscal period end otherwise. Split ratios are snapped to the same common-ratio set the detector uses
before being applied, so a slightly-off stored ratio does not leak a rounding error into every historical
dividend.

**Filers restate their own history, and that has to be undone.** After a split, a company commonly
refiles prior per-share dividends on the post-split basis, so the "raw" XBRL amount for an old quarter is
already scaled and is *not* on the price scale of its own ex-date. Feeding that to the adjustment pass
would divide a post-split cash amount by a pre-split close and produce a dividend factor wrong by the
split ratio.

There is no flag in the data saying which facts were restated, so it is inferred from the amounts. For
each split, the first event at or after it sets a scale anchor, and pre-split events are walked backwards
while they stay within 30% of that anchor. That contiguous run is the set the issuer already restated,
and its earliest member becomes the cutoff. Events at or after the cutoff have the split's ratio removed
from their raw amount rather than applied to it; events before the cutoff are treated as genuinely
pre-split and are scaled normally.

The inference is a heuristic over reported amounts, which is a real weakness: a company that changed its
dividend by more than 30% in the quarter spanning a split will have its cutoff placed wrongly.

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
| Retracting a wrong split | Prune when no longer detected and confidence is below 70, suppressed on a degraded scan | Never delete, correct only by re-dating; tombstone rows instead of deleting | Re-dating cannot retract a split that should not exist, so a false price-only detection would adjust the series permanently. A confidence floor keeps well-grounded splits safe, and a tombstone would require every reader (adjustment, both validators) to learn to filter it. |
| Extended ratios (3:2, 4:3) | Require filing or price confirmation | Treat like primary ratios | A 33% move is inside the range of ordinary gaps and share-count drift, so the ratio alone is not evidence. |
| Price-first split detection | Scan for unexplained breaks, persist with graded confidence | Rely on XBRL share counts only | Share counts arrive with the next quarterly cover page, so an XBRL-only pipeline is months late. This makes same-day detection possible. |
| Price-only split without filing support | Persist only at 5x or beyond | Persist any snapped break; persist none | At 5x no ordinary market move is a plausible alternative explanation. Below it, the risk of adjusting on a crash outweighs the missed event. |
| Undatable dividend | Persist with a synthetic date at confidence 10 | Skip the event | A missing dividend is a permanent hole in yield and total-return history. A bounded date error is recoverable, and the low rank invites correction. |
| Ex-date assignment strategy | Tuple, then direct text, then global DP, then synthetic | Cadence inference only; greedy matching only | Amount-anchored declarations are read rather than inferred. DP exists because quarterly events are a sequence where one greedy mistake cascades. |
| Amount storage | Persist raw and split-adjusted amounts side by side | Store only the adjusted amount | The raw amount is what pairs with the raw prior close during adjustment, and keeping it makes a newly detected split a recomputation rather than a lossy rewrite. |
| Missing Q4 when only an annual total is filed | Derive it by subtraction, guarded by positivity and a 2.5x median ceiling | Skip the quarter; treat the annual fact as an event | Skipping loses a real dividend, and treating a year as a quarter overstates it fourfold. Subtraction recovers the quarter, and the guards catch the cases where the inputs were not what they appeared. |
| Filer-restated historical amounts | Infer the restated run from amount continuity and un-restate it | Trust the raw fact; add a manual override list | Nothing in XBRL marks a restated fact, so there is no exact signal to read. Trusting the raw fact yields a dividend factor wrong by the split ratio for every pre-split quarter the filer refiled. |
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
5. ✅ Whether a wrongly persisted split can be retracted: yes, below a confidence floor of 70, and not
   while the split scan is degraded. Deletion was chosen over a tombstone because a tombstone obliges
   every downstream reader to filter it.
6. ✅ Where the effective-date convention is defined: in the adjustment segment
   (`PRICE-ADJ-APPLY-006`), because the apply loop is what gives the date its meaning. Detection
   conforms to it.
7. ✅ Whether split corroboration should keep its own non-positive-close filter once the ingest segment
   rejects such prints: yes, as defense in depth. It is cheap and it keeps the corroborator correct
   against any price history loaded before that gate existed.

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
6. Detecting which pre-split dividends a filer already restated is inferred from amount continuity (a
   contiguous backwards run within 30% of the first post-split amount) because nothing in XBRL marks a
   restated fact. An issuer that changed its dividend by more than 30% across the split will have its
   cutoff placed wrongly, and the resulting raw amount will be off by the split ratio for the events on
   the wrong side of it. No signal currently detects that case.
7. The derived-Q4 plausibility ceiling (2.5 times the median prior quarter) and the cumulative-detection
   margin (0.02) are unexplained constants, like the DP costs above.
8. Reverse splits are handled by the same reciprocal ratios and log-space tolerances as forward splits,
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

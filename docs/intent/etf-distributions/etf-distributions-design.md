---
parent: high-level-design
prefix: ETF
---

# ETF Distributions

## Context and Design Philosophy

This segment detects fund distributions for tickers whose `Asset.isFund` is true. It shares almost
nothing with the equity path, because funds do not report per-share distributions as XBRL facts. There
is no structured number to parse and no share count to differentiate. Everything comes from filing
prose.

**The hard problem here is identity, not extraction.** An equity CIK is a company, and a filing under it
is about that company. A fund CIK is a *trust*, and one trust files for dozens of series (individual
funds), each with several classes (share classes with distinct tickers). A single 497 can announce
distributions for twenty funds in one table. So the question "what did this ticker distribute" cannot be
answered by reading the trust's filings; it requires first proving that a particular document, or a
particular part of it, is about this ticker's series and class. Extraction is downstream of that proof
and is worthless without it.

**Identity resolution depends on data this segment does not own.** A ticker is only detectable when its
`Listing` carries a SEC series ID or a class contract ID, populated by the ETF identity enrichment in the
monthly asset load. A fund whose identity was never resolved is skipped outright, and no amount of
filing text can rescue it. The upstream enrichment is therefore a hard precondition, not a nicety.

**Confidence gates persistence rather than annotating it.** The equity path persists nearly everything it
finds and relies on provenance ranking to let better evidence correct worse. This segment does the
opposite: a distribution whose date resolution scores below 70 is discarded, not stored at low
confidence. The reason is that there is no independent check available here. Equity dividends can be
sanity-checked against XBRL amounts and a quarterly cadence; a fund distribution extracted from a table
row has nothing to be checked against, so a wrong one would be indistinguishable from a right one
forever.

## Preconditions

Two gates run before any filing is fetched.

1. **The ticker must have a `Listing`.** Missing means skip, counted as `listing_missing`.
2. **The listing must carry a SEC series ID or a class contract ID.** Missing means skip, counted as
   `identity_missing`.

The second gate is what makes the identity scoring below meaningful: the two highest-weighted signals
are exactly these two identifiers, so a listing without either can never score well enough to matter.

## Filing Selection

Filings come from the shared discovery service, newest first, and are scored by form.

| Form | Score | Why |
|---|---|---|
| `497*` | 140 | Prospectus supplements, where distribution announcements live |
| `485*`, `N-1A`, `N-1A/A` | 130 | Registration statements and amendments |
| `N-CSR`, `N-CSRS`, and their `/A` variants | 115 | Annual and semi-annual shareholder reports |
| anything else | 0 | Not read |

Filings older than eight years are skipped as stale. Unlike the equity path there is no fetch budget:
every eligible in-window filing is processed on every run. That is affordable only because the segment
is not run nightly, which is itself a consequence discussed under *Trigger*.

## Document Resolution

A fund filing's distribution table is usually not in the primary document, so candidate documents are
assembled per filing, capped at eight:

1. The primary document, when named.
2. Items from the filing index whose names look distribution-related: extension `.htm`, `.html`, `.txt`,
   or `.xml`, and a name containing any of `dividend`, `distribution`, `dist`, `income`, `capgain`,
   `capitalgain`, `supplement`, `class`, `ex99`, `ex-99`, `ex101`, `ex-101`, or `497`.

Each candidate is fetched, truncated to 1,250,000 characters, and scored for identity. The
highest-scoring document wins the filing. The full submission text is then fetched and scored on the same
footing, so it can displace every individual document if it happens to contain stronger identity
evidence.

Truncation is a memory bound on documents that can run to tens of megabytes. It is also a silent
correctness risk: a distribution table past the cut is invisible, and nothing reports that a document was
truncated.

## Identity Scoring

Signals are additive, and a document must reach **2** to be considered about this ticker.

| Signal | Points |
|---|---|
| SEC series ID appears in the text | 4 |
| SEC class contract ID appears in the text | 4 |
| SEC class ticker appears as a token | 3 |
| The ticker itself appears as a token | 2 |
| SEC series name matches | 2 |
| SEC class name matches | 2 |
| The document name contains the ticker | 1 |
| The form type is a 497 variant | 1 |

Name matching requires a normalized name of at least six characters, and is satisfied either by a full
substring match or by at least two words of four or more characters appearing as tokens. The word-level
fallback exists because fund names are rendered inconsistently across documents ("iShares Core S&P 500
ETF" versus "iShares Core S&P 500 Exchange Traded Fund"), so an exact match is too brittle.

The identifiers outweigh the names deliberately: a series ID is unambiguous, while a fund name is often a
prefix of its siblings' names.

**The threshold of 2 is the weakest link in the segment.** It is cleared by the ticker string alone, and
a multi-fund prospectus supplement mentions every ticker it covers. A document about twenty funds
therefore passes identity for all twenty, and the amount and date extractors then run over the whole
document without knowing which row belongs to which fund.

## Amount Extraction

Three patterns, each requiring a dollar amount, all rejecting values at or above 50 as implausible for a
per-share fund distribution:

| Pattern | Score |
|---|---|
| A distribution or dividend keyword within 80 characters before the amount | 90 |
| An amount followed by `per share` | 85 |
| A dividend or distribution keyword and an amount on the same line | 75 |

The highest-scoring candidate wins. Selection is by pattern strength only: proximity to the matched
identity evidence is not considered, and neither is agreement between patterns. In a document holding
several funds' rows, the winner is whichever row the strongest pattern happened to match first.

## Date Resolution

Four paths, in order, with the confidence each yields:

| Path | Effective date | Confidence |
|---|---|---|
| `ex_date` | The extracted ex-dividend date | 95 |
| `record_date` | Derived from the record date using the shared settlement rules | 86 |
| `pay_date_fallback` | The business day before the payable date | 62 |
| `filing_date_fallback` | The business day before the filing date | 55 |

A found payable date adds 5, capped at 100.

Record-to-ex derivation is not reimplemented here: it calls the same routine the equity path uses, so
both asset classes share one settlement history (T+3, then T+2, then T+1) and one NYSE holiday calendar.

Date candidates are gathered by three passes over the normalized text, then the best per role is chosen
by score, then by proximity to the filing date, then by earliest date:

- **Sentence pass.** A role keyword plus up to ~220 following characters; every date in the snippet
  becomes a candidate. Scored 90 for an ISO date, 88 for a month-name date, 82 otherwise, on the
  reasoning that an unambiguous format is more likely to be the intended date than a bare numeric one.
- **Labeled pass.** One pattern over the whole document pairing a role label with a nearby date, scored
  92. This is the only pattern here matched against an entire filing rather than a line or window, so it
  is the one that runs against the regex-budget guard. On timeout the candidates already collected are
  kept and the other two passes still contribute, so a slow document degrades this signal instead of
  losing the extraction.
- **Table pass.** Line-oriented, examining each line with the two following lines as context, which is
  how a date lands next to its label across a table row split over several lines. A window must carry a
  distribution signal (a dividend, distribution, or per-share keyword, or a dollar amount) or it is
  ignored, and a window that looks like annual-report boilerplate ("year ended", "fiscal year", "for the
  year", "annual") is dropped unless it carries an explicit date label. Scored 80 for ex and record, 78
  for pay.

### The confidence gate makes two paths unreachable

Persistence requires confidence of at least 70. Working the arithmetic through:

| Path | Best achievable confidence | Persists? |
|---|---|---|
| `ex_date` | 100 (95 + 5) | yes |
| `record_date` | 91 (86 + 5) | yes |
| `pay_date_fallback` | 67 (62 + 5, and the payable date is present by definition on this path) | **never** |
| `filing_date_fallback` | 55 (no payable date exists on this path, so the bonus cannot apply) | **never** |

Both fallback paths compute a date, record a resolution path in the diagnostics, and are then always
rejected by the gate. Their only remaining function is diagnostic: the counters show how often a filing
yielded a payable date or nothing but a filing date. Whether that is the intended design or a threshold
that drifted past them is unresolved.

## Persistence

A row is written when confidence clears the gate and no row already exists with the same ticker, action
type, effective date, amount, and `SEC_ETF_FILING` source type. An existing match is counted as
`duplicate` and skipped.

Stored fields include the amount as both raw and adjusted (identical, since fund distributions are not
restated for splits here), the form type and accession number that produced it, the record and payable
dates, the confidence score, and the series and class identifiers that matched.

**Persistence is insert-or-skip only.** There is no reconciliation of a changed date, no provenance
ranking, and no pruning of rows that later runs no longer produce. The equity path has all three. The
asymmetry follows from the confidence gate: because only well-evidenced rows are ever written, there is
no low-quality tier for a later run to correct, and a re-detection that resolves a *different* date
inserts a second row rather than moving the first. That is the segment's most likely source of duplicate
distributions.

## Diagnostics

The detection report is the only visibility into a path with no structured input to validate against. It
counts filings considered, fetched, and identity-matched; amounts and dates extracted; below-confidence
and duplicate outcomes; skips by reason; identity scores bucketed; amount sources; and date resolution
paths. It also keeps up to ten sample created rows with their accession, form, document, date, amount,
and identity score.

The skip reasons form a funnel (`form_not_relevant`, `filing_stale`, `no_candidate_documents`,
`document_fetch_failed_or_empty`, `identity_mismatch`, `amount_missing`, `date_missing`,
`below_confidence`, `duplicate`), which is what makes "this fund's distributions are missing" a
diagnosable question here in a way it is not in the equity path.

## Trigger

ETF detection fetches up to eight documents plus a full submission text per eligible filing, for every
in-window filing, against a per-process SEC rate limiter. For a trust with a long filing history that is
hundreds of fetches for one ticker, which is why the nightly `hist-load` task sets `equity-only`.

**The consequence is that this segment currently has no automated trigger.** The admin endpoint that used
to invoke it was retired with the rest of the Spring Boot admin routes, and no scheduled run mode covers
it. It runs only when someone executes `hist-load` manually with `equity-only=false`. Fund distributions
therefore go stale by default, and the staleness is invisible because nothing reports the last time a
fund was scanned.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Detection source | Filing text only | XBRL facts, as for equities | Funds do not report per-share distributions as XBRL facts. There is no structured alternative to parse. |
| Identity precondition | Require a series ID or class contract ID on the listing | Attempt detection by ticker alone | A trust files for many funds, so a ticker string in a filing proves nothing about which fund a table row belongs to. Without an identifier there is nothing to anchor on. |
| Identity model | Additive weighted signals with a minimum score | Require an exact series-ID match | Documents vary in what they disclose; some name the fund without repeating its identifier. Additive scoring accepts partial evidence, at the cost of a threshold that admits weak matches. |
| Name matching | Substring or two words of four or more characters | Exact name match only | Fund names are rendered inconsistently across documents, so exact matching is too brittle to be useful. |
| Document selection | Primary document plus index items with distribution-like names, capped at eight | Primary document only; every document in the filing | The distribution table is usually in an attachment, so primary-only misses most of them. Reading every document would multiply the fetch cost against a shared rate limit. |
| Full submission text | Fetched and scored on equal footing with individual documents | Fallback only, as in the equity path | For funds it is often the only rendering that contains the whole distribution table, so restricting it to a fallback would lose the best source. |
| Document truncation | Hard cap at 1,250,000 characters | Stream and scan without a cap | An unbounded fund document can exhaust heap. The cap trades a bounded memory footprint for silently invisible content past the cut. |
| Amount plausibility | Reject at or above 50 per share | No bound; a per-fund bound | Per-share fund distributions are cents to low dollars. The bound rejects NAVs, totals, and share prices caught by the same patterns. |
| Amount selection | Highest pattern score wins | Nearest to the identity evidence; require agreement between patterns | Pattern strength is the only signal implemented. Proximity to identity evidence would be a better discriminator in a multi-fund document and is not currently used. |
| Date precedence | Stated ex-date, then record date, then payable date, then filing date | Record date first; ex-date only | A stated ex-date needs no derivation. The two weaker paths exist as diagnostics because the confidence gate rejects both. |
| Record-to-ex derivation | Reuse the equity path's routine | Reimplement for funds | One settlement history and one holiday calendar for both asset classes. Two copies would drift. |
| Confidence gate | Discard below 70 rather than store at low confidence | Store everything, as the equity path does | Fund distributions have no independent check (no XBRL amount, no cadence), so a wrong row would be indistinguishable from a right one indefinitely. |
| Persistence model | Insert or skip on exact duplicate | Reconcile dates, rank provenance, prune orphans, as for equities | Follows from the confidence gate: no low-quality tier exists to correct. The cost is that a re-detection resolving a different date inserts a second row. |
| Split restatement | None; raw and adjusted amounts are equal | Restate as for equity dividends | Fund share splits are rare and not detected by this path, so there is nothing to restate against. |
| Scan scope | Every eligible filing in an eight-year window, no fetch budget | A per-run budget as in the equity path | Only viable because the segment is not run nightly. If it gains a schedule, it needs a budget. |

## Open Questions & Future Decisions

### Resolved

1. ✅ Whether a fund without a resolved SEC identity can be detected: no. It is skipped, and the monthly
   identity enrichment is a hard precondition.
2. ✅ Whether to store low-confidence fund distributions: no. Nothing downstream could ever correct them.
3. ✅ Whether to reimplement record-to-ex derivation for funds: no. Both asset classes share one routine.

### Deferred

1. The identity threshold of 2 is cleared by a bare ticker token, so a multi-fund prospectus supplement
   passes identity for every ticker it mentions. Amount and date extraction then run over the whole
   document with no way to tell which row belongs to which fund. Raising the threshold to require an
   identifier, or scoping extraction to the text region where identity matched, would close this. Both
   are behavior changes and neither is decided.
2. `pay_date_fallback` and `filing_date_fallback` cannot produce a persisted row at any input, because
   their maximum confidence falls below the gate. Either the gate should be lowered to admit one of them,
   or their confidences should be raised, or they should be documented as diagnostic-only and stop
   pretending to resolve an effective date.
3. Amount selection ignores proximity to the identity match. In a multi-fund table the strongest pattern
   match anywhere in the document wins, which is the same failure mode as item 1 seen from the amount
   side.
4. Document truncation at 1,250,000 characters is silent. Nothing counts truncated documents, so a fund
   whose table sits past the cut looks identical to a fund with no table.
5. A re-detection that resolves a different date for the same distribution inserts a second row rather
   than correcting the first, and nothing detects the pair. The equity path's year-scoped match exists
   for exactly this and has no counterpart here.
6. The segment has no automated trigger since the admin routes were retired. It needs either its own
   scheduled run mode (with a fetch budget, per item 7) or an explicit decision that fund distributions
   are maintained manually.
7. There is no per-run fetch budget. That is currently safe only because nothing schedules this segment;
   the moment it gains a schedule, an eight-year window over every eligible filing is not affordable.
8. Nothing records when a fund was last scanned, so staleness is invisible. The equity path has
   `listings.last_sec_detection_at` driving its rolling refresh; funds have no equivalent.

## References

- [High-Level Design](../../high-level-design.md): the two-path approach and the `isFund` seam.
- [`filing-evidence`](../filing-evidence/filing-evidence-design.md): supplies filing discovery, the
  regex budget, and the record-to-ex settlement rules this segment calls.
- [`equity-actions`](../equity-actions/equity-actions-design.md): the contrasting path, with XBRL facts,
  provenance ranking, reconciliation, and pruning.
- [`price-adjustment`](../price-adjustment/price-adjustment-design.md): routes on `Asset.isFund` and
  consumes the rows this segment writes.
- `listing/`: ETF identity enrichment populating the series and class identifiers this segment requires.

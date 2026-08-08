---
parent: high-level-design
prefix: FILING
---

# Filing Evidence

## Context and Design Philosophy

This segment turns SEC filings into dated, scored candidates. It is the only place in the pipeline
that reads filing *documents*, and it is the evidence machinery both asset-class paths share: the
equity path asks it for record dates, ex-dividend dates, and split effective dates; the ETF path
reuses its extraction cache, its regex budget, and its date grammar.

**This segment does not decide which date is correct.** It reports what each filing appears to say
and how well evidenced each reading is. Choosing an ex-date for a given quarterly event is the
equity segment's job, and it needs a *ranked field of candidates* to choose from, not a single
answer. Every output therefore carries a confidence score, a pattern label, and its provenance
(which accession, which document, primary or exhibit).

Three constraints shape everything here.

**The SEC rate limiter is per-process and shared with every other consumer in the run.** A single
ticker's dividend history can span hundreds of filings. Fetching them all, every night, for every
ticker in scope, is not affordable, so the segment is built around not fetching the same document
twice in its lifetime.

**Filings are immutable, which makes extraction cacheable forever.** An accession's text never
changes, so an extraction result is valid until the *extractor* changes. This is what converts an
unaffordable full-history scan into an affordable incremental one.

**Filing prose is hostile to regex.** The dates that matter sit at an unpredictable distance from the
words that label them, which forces lazy bounded quantifiers next to a large month-name alternation.
That shape backtracks catastrophically on some real documents, so matching runs against a guarded
input rather than an unguarded string.

## Filing Discovery

`EdgarFilingDiscoveryService` is the canonical source of filing *metadata*: accession number, form
type, primary document name, and filing date. It downloads no filing bodies.

Coverage comes from the CIK's submissions JSON plus up to 48 archive shards linked from it, which is
what reaches back beyond the recent window SEC includes inline. Rows are keyed by accession and the
first occurrence wins, so a filing appearing in both the recent block and an archive shard is not
double-counted. A row without an accession number or without a primary document name is dropped,
because neither can be fetched later. Results come back newest first, with unknown filing dates last.

Discovery is resilient by design: a malformed submissions payload or an unavailable archive shard is
logged and skipped rather than thrown, and a total discovery failure yields an empty candidate list
so the run continues to the next ticker. Losing an archive shard costs coverage of old filings, which
the next run retries; throwing would cost the whole ticker.

## Form Relevance and Ordering

Relevance is a per-form score, and it differs by what is being looked for. A form scoring zero is not
read at all, and the count is kept per form so a scan can report what it rejected.

| Form | Dividend scan | Split scan |
|---|---|---|
| 8-K, 8-K/A | 140 | 145 |
| 10-Q, 10-Q/A | 100 | 105 |
| 10-K, 10-K/A | 95 | 100 |
| DEF 14A, DEFA14A | 110 | 95 |
| 6-K, 20-F, 40-F | 90 | 90 |
| anything else | 0 (rejected) | 0 (rejected) |

The two orderings differ where the mode changes what a form is worth. A proxy statement is a good
source of dividend record dates and a poor source of split effective dates, so `DEF 14A` outranks
`10-Q` for dividends and falls below it for splits.

Candidates are ordered newest first, then by form score, then by accession number for stability.
There is no cap on how many are *selected*: the cap is on how many are *fetched*, which is a
different mechanism.

## The Per-Run Fetch Budget

Each scan may perform at most 250 fresh dividend document fetches, or 400 fresh split fetches, per
run. A filing whose extraction is already cached does not consume budget.

This is a spend limit, not a horizon. Combined with newest-first ordering it produces a specific
behavior: the first run on a ticker extracts its most recent 250 relevant filings and defers the
rest; the next run finds those 250 cached and free, so its budget reaches 250 filings further back.
Over successive nights the budget walks backward through unextracted history until the whole filing
record is covered, after which every run is nearly free and only genuinely new filings cost anything.

The deferred count is logged per scan, so a ticker still working through its backlog is visible
rather than looking like a ticker with no old filings.

## Extraction Cache

`FilingExtractionStore` holds one row per (CIK, accession) with two independently versioned sections,
one for dividend extraction and one for split extraction. A section is reusable only when its stored
extractor version equals the current constant; a stale version, a missing section, or JSON that fails
to parse all read as absent and trigger a fresh extraction.

**The version constants are the cache-invalidation contract.** Any change to what extraction produces
(patterns, scores, the exhibit walk, the sentence pass) must bump the affected section's version, or
the run will keep serving results computed by logic that no longer exists. Nothing enforces this
mechanically, which makes it the most easily broken invariant in the segment.

Cache writes are best effort. A `DataIntegrityViolationException`, which is what a concurrent writer
on the same accession looks like, is logged and swallowed: the extraction result is still returned to
the caller and the only cost is re-extracting that accession on a later run. A failed cache write must
never fail a scan.

**The cache is deliberately lossy for record dates.** Only the single best record-date candidate per
filing is persisted, while ex-date candidates are kept as a list deduplicated by date holding the
highest score each. A filing that mentions three plausible record dates contributes exactly one to
any later run. That is the intended trade (a filing's best reading is what downstream selection
consumes) but it means widening record-date selection later cannot be done from cache alone: it needs
a version bump and a re-extraction.

Extraction timestamps use the storage zone (UTC), since they are only ever compared to other stored
timestamps.

## Document Walk

For each selected filing, in order:

1. **Primary document.** Fetched by accession and document name, normalized to searchable text.
2. **Exhibits**, up to six. Discovered by scanning the primary document's `href` values for names
   ending `.htm`, `.html`, or `.txt` whose path looks like an exhibit or press release (`ex99`,
   `99-`, `exhibit99`, `exhibit`, `ex-`, `exh`, `press`). Query strings are stripped, paths reduced to
   their basename, duplicates removed, and the first six kept in document order.
3. **Full submission text**, but only as a fallback when the steps above produced nothing.

Exhibit matches are penalized five points relative to primary-document matches. An exhibit is usually
the press release holding the real dates, so it is worth reading, but when the primary document and an
exhibit disagree the primary document is the filing of record.

The three fallbacks are independent: record dates, ex-date candidates, and declaration tuples each
trigger a full-submission fetch on their own emptiness. A filing that yields record dates but no
tuples still fetches the full submission text looking for tuples.

Declaration-tuple extraction walks the exhibits a second time rather than reusing the text from step
2. This is only affordable because `WebService` caches fetched documents per ticker for the duration
of the run, so the second walk is served from memory. That coupling lives outside this segment, in
`client/WebService`, and it is load-bearing: without it, tuple extraction would double the segment's
HTTP volume.

## Candidate Extraction and Scoring

Two extractors run over every document and their results merge.

**Labeled-pattern extraction** applies a scored pattern list and keeps the best candidate per date.

| Purpose | Pattern | Score |
|---|---|---|
| Record date | `RECORD_DATE_NEAR_DIVIDEND` | 130 |
| Record date | `SHAREHOLDER_OF_RECORD` | 120 |
| Record date | `HOLDERS_OF_RECORD` | 115 |
| Record date | `RECORD_AT_CLOSE_OF_BUSINESS` | 110 |
| Record date | `RECORD_DATE_OF` | 95 |
| Record date | `RECORD_DATE_WILL_BE` | 90 |
| Record date | `GENERIC_RECORD_DATE` | 70 |
| Ex-dividend date | `EX_DIVIDEND_DATE_LINE` | 135 |
| Ex-dividend date | `EX_DIVIDEND_TRADING_START` | 115 |
| Split date | `SPLIT_ADJUSTED_TRADING` | 130 |
| Split date | `SPLIT_EFFECTIVE_DATE` | 125 |
| Split date | `SPLIT_DISTRIBUTION_DATE` | 105 |
| Split date | `SPLIT_GENERIC_DATE` | 80 |

Split extraction short-circuits: when `SPLIT_ADJUSTED_TRADING` matches, the weaker split patterns are
not consulted at all. A document that states when trading begins on a split-adjusted basis has stated
the effective date directly, in the exact sense the adjustment code needs (first trade date at the new
basis), so a lower-scoring "effective" phrase elsewhere in the same document can only muddy it.

**Sentence-level extraction** splits text on terminal punctuation, keeps sentences containing a
dividend or split trigger word, classifies each sentence's intent, and discards sentences whose intent
is generic. Within a qualifying sentence, every date is scored from the intent's base score plus a
proximity boost that decays with distance from the sentence's anchor phrase, minus two points per
additional date already found in that sentence. The last rule encodes that the first date after
"record date" in a sentence is the likely referent and later ones are increasingly incidental.

Merging keeps one candidate per date: labeled-pattern candidates are taken first and a sentence
candidate replaces one only when it compares strictly better. Output is ordered best first.

## Regex Budget

`BoundedRegexInput` wraps the text being matched and throws once a match exceeds a wall-clock budget,
which callers treat as "no further matches."

It guards the **input**, not the patterns, and that placement is the whole point. The lazy `?` in
`.{0,900}?` is deliberate: it finds the *nearest* date after a keyword. Making the quantifier
possessive would bound the runtime and silently change which date a filing resolves to, turning a
performance fix into a correctness regression. Truncating the input would also bound runtime, at the
cost of dropping a legitimate date near the end of a long filing. Guarding the input leaves every
pattern byte-identical, so behavior is unchanged for every document that completes within budget and
only pathological ones are cut short.

- Default budget 2000ms, generous next to a normal match (low single-digit milliseconds) and small
  next to a nightly run.
- The deadline is checked every 4096 characters read. Checking on every character would cost more
  than the backtracking it guards against.
- A fresh budget per pattern and per window, so one pathological match cannot starve the rest.
- On timeout, the best candidate found so far is kept. The outcome is identical to a document holding
  no further matches, so one slow document never aborts a scan.
- Instances are stateful, single-use (the deadline starts at construction) and not thread-safe.

Because FindSecBugs reports this pattern shape as ReDoS, the suppression in the SpotBugs exclude file
is package-wide. A newly added pattern of the same shape is therefore suppressed automatically and
will not fail the build, which means the guard has to be applied by discipline rather than by the
build catching its absence.

## Ex-Dividend Date from Record Date

Settlement rules changed twice, so the mapping has three regimes. The record date is first normalized
forward to the next business day, which absorbs record dates that fall on weekends or holidays.

| Normalized record date | Ex-dividend date |
|---|---|
| before 2017-09-05 (T+3) | two business days before |
| 2017-09-05 to 2024-05-27 (T+2) | one business day before |
| 2024-05-28 onward (T+1) | the record date itself |

Business-day arithmetic uses a full NYSE calendar: weekends, New Year's Day, MLK, Presidents' Day,
Good Friday (computed by the anonymous Gregorian algorithm), Memorial Day, Juneteenth from 2022,
Independence Day, Labor Day, Thanksgiving, and Christmas, with weekend-falling fixed holidays shifted
to the observed Friday or Monday.

This calendar is complete in a way the ingest segment's is not, because here it changes an answer: a
holiday missed by one day puts a dividend's ex-date on a day the market was closed, which then
misplaces the price adjustment. In the ingest segment the calendar only filters a candidate list that
the IEX index already validates.

## Date Grammar

`FilingTextDates` owns the grammar so the date service and the tuple extractor cannot drift apart.

Recognized forms are month-name dates (abbreviated or full, optional trailing period, optional comma)
and numeric `M/D/YYYY`. Parsing normalizes `Sept` to `Sep`, strips periods, collapses whitespace, then
tries five formatters in order, returning empty rather than throwing when none match.

Text normalization removes `<script>` and `<style>` blocks with their content, strips remaining tags,
unescapes the handful of entities that appear in filing prose (`&nbsp;`, `&amp;`, `&#160;`, `&#8217;`,
`&#8211;`), and collapses whitespace. Removing script and style content *before* stripping tags
matters: stripping tags first would leave JavaScript source in the searchable text, where a date
literal could be extracted as if it were prose.

## Declaration Tuples

`DividendDeclarationTupleExtractor` produces the segment's highest-value output: an amount anchored to
the dates stated with it. Matching a quarterly XBRL event to a tuple by dollar amount ties it to the
8-K that actually declared it, which replaces cadence guessing with dates the company published.

Anchors are per-share dollar amounts found three ways: `$X per share` (including "per common share",
"per ordinary share", "per class A share", and `$X/share`), `dividend of $X` or `distribution of $X`,
and the label-first table form `Dividend per share: $X`. Amounts outside `(0, 100)` are rejected as
implausible for a per-share cash dividend.

Around each anchor, a 600-character window on each side is searched for labeled record, payable,
declaration, and ex-dividend dates, taking the nearest occurrence of each label to the anchor.
**A record date is mandatory**: an anchor without one yields no tuple, because an amount with no
record date cannot be matched to an event any better than cadence already can.

Confidence starts at 60 and adds 15 for explicit per-share phrasing, 10 for a payable date, and 10 for
a declaration or ex-date, capped at 100. Tuples are keyed by amount (to four decimal places) and
record date; on collision the higher confidence wins, and on equal confidence the earlier filing wins,
which prefers the original declaration over a later restatement of it.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Extraction caching | Persist per-accession results, versioned by extractor | Re-extract every run; cache raw documents instead | Filings are immutable, so the result is valid until the logic changes. Caching results rather than documents also avoids storing filing bodies. |
| Cache invalidation | A version constant per section, bumped by hand | Hash the pattern set; timestamp expiry | A hash would invalidate on cosmetic edits and is hard to make stable across refactors. Expiry would re-fetch immutable documents for no reason. The cost is that the bump is a discipline, not a mechanism. |
| Scan limit | Per-run fresh-fetch budget, cached reads free | Fixed cap on filings considered; unlimited fetching | A fixed cap would permanently hide old filings. Unlimited fetching cannot coexist with a shared per-process SEC rate limit. The budget converges on full coverage without ever spending more than one night's allowance. |
| Candidate ordering | Newest filings first | Oldest first; highest form score first | Recent filings answer recent events, which is what a nightly run is for. It also makes the budget walk backward, which terminates. |
| Record-date caching granularity | Persist only the best candidate per filing | Persist all candidates | Downstream selection consumes one reading per filing. Storing all of them would grow the cache for information nothing reads, at the cost that widening selection later needs a re-extraction. |
| ReDoS mitigation | Bound the input with a deadline | Possessive quantifiers; truncate the input; rewrite the patterns | The lazy quantifier is semantically load-bearing (nearest date wins), so possessive matching changes results. Truncation drops late dates. Bounding the input leaves matching behavior identical for every document that completes. |
| Timeout handling | Keep the best match found so far | Discard the document; fail the scan | A timeout is indistinguishable from "no more matches here," and a single pathological document is not a reason to lose a ticker's whole scan. |
| Exhibit handling | Read up to six, penalize matches by 5 | Ignore exhibits; treat them equally | The press release in an exhibit usually holds the real dates, so ignoring exhibits loses the best source. The penalty keeps the filing of record authoritative on disagreement. |
| Full submission text | Fallback only, per output type | Always fetch; never fetch | It is a large fetch that usually duplicates the primary document. Fetching it only on emptiness pays for it exactly when nothing cheaper worked. |
| Split pattern short-circuit | `SPLIT_ADJUSTED_TRADING` suppresses weaker split patterns | Always run all patterns and rank | A statement of when split-adjusted trading begins *is* the effective date under the pipeline's convention. Admitting weaker candidates from the same document can only compete with a direct statement. |
| Two form-score tables | Separate dividend and split scores | One shared relevance table | The same form is not equally informative for both. A proxy statement is a strong dividend source and a weak split source, and one table cannot express that. |
| Segment ownership of the shared helpers | Cache, regex guard, and date grammar live here | Duplicate them in the equity and ETF segments | The ETF path uses the cache and the guard. Duplicating them would let two copies of the ReDoS defense and the cache-versioning rule drift apart. |

## Open Questions & Future Decisions

### Resolved

1. ✅ Whether to cap filings considered or fetches performed: fetches. A cap on consideration would
   permanently hide old filings; a fetch budget converges on full coverage.
2. ✅ Whether to fix the ReDoS shape in the patterns: no. The lazy quantifier decides which date is
   returned, so changing it is a correctness change disguised as a performance fix.
3. ✅ Whether an exhibit may outrank the primary document: not at equal evidence. The five-point
   penalty resolves ties toward the filing of record.

### Deferred

1. Nothing enforces the cache-version bump. A change to patterns or scoring that forgets it leaves
   the run serving results from logic that no longer exists, silently and indefinitely. A checksum
   over the pattern set, or a test asserting the version changes with the pattern list, would close
   this. Both have costs; neither is decided.
2. The declaration-tuple exhibit walk depends on `WebService` caching documents for the duration of a
   ticker's detection. The cache's lifecycle is owned by the *caller*: the equity segment opens and
   closes it around detection. This segment neither opens it nor checks that it is open, so any caller
   that scans without opening one silently pays the full HTTP cost of every repeated document walk,
   and no test would show it.
3. The regex-shape SpotBugs suppression is package-wide, so a new unguarded pattern of the same shape
   is silently accepted by the build. Narrowing the suppression to the sites that actually use the
   guard would make the build enforce what the convention currently only asks for.
4. Sentence-level intent classification and its proximity boost are tuned by constants with no stated
   derivation. They work, but nothing records what evidence set they were tuned against, so it is not
   currently possible to tell whether a change improves or degrades them.
5. Extraction is per-CIK, and a scan reports form-level diagnostics but no per-filing record of why a
   filing produced nothing. When a known dividend is missed, the scan cannot say whether the filing
   was never selected, never fetched, fetched and unmatched, or timed out.

## References

- [High-Level Design](../../high-level-design.md): the evidence-ranking approach this segment implements.
- [`equity-actions`](../equity-actions/equity-actions-design.md): consumes record dates, ex-date candidates, declaration tuples, and split date candidates.
- [`etf-distributions`](../etf-distributions/etf-distributions-design.md): reuses the extraction cache, the regex guard, and the date grammar.
- `client/WebService`: SEC HTTP client, per-ticker document cache, rate limiting, retries.
- `.claude/rules/springboot-java.md`: the convention requiring the regex guard on this pattern shape.
- [`equity-corporate-action-process.md`](../../equity-corporate-action-process.md): step-level walkthrough of how the equity path consumes these outputs.

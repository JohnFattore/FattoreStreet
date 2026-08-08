# Filing Evidence Specs

EARS requirements for SEC filing discovery, document extraction, and dated-candidate scoring.
Design: [`filing-evidence-design.md`](filing-evidence-design.md).

Markers: `[x]` implemented, `[ ]` active gap, `[D]` deferred.

## Filing Discovery

- [x] **FILING-001**: When discovering a CIK's filings, the system shall collect accession number, form type, primary document name, and filing date from the SEC submissions payload, and shall not download filing bodies during discovery.
- [x] **FILING-002**: When discovering a CIK's filings, the system shall additionally scan up to 48 archive bundles linked from the recent submissions payload.
- [x] **FILING-003**: When the same accession number appears more than once during filing discovery, the system shall keep the first occurrence.
- [x] **FILING-004**: When a discovered filing row has no accession number or no primary document name, the system shall exclude it from the discovery result.
- [x] **FILING-005**: When returning discovered filings, the system shall order them by filing date descending, placing filings with an unknown filing date last.
- [x] **FILING-006**: If an archive bundle fetch fails or a submissions payload cannot be parsed, then the system shall log the failure and continue discovery with the remaining sources.
- [x] **FILING-007**: If filing discovery fails entirely for a CIK, then the system shall treat the CIK as having no candidate filings rather than propagating the failure.

## Form Relevance and Candidate Ordering

- [x] **FILING-008**: When selecting candidate filings for a dividend scan, the system shall score forms 8-K and 8-K/A at 140, DEF 14A and DEFA14A at 110, 10-Q and 10-Q/A at 100, 10-K and 10-K/A at 95, and 6-K, 20-F and 40-F at 90.
- [x] **FILING-009**: When selecting candidate filings for a split scan, the system shall score forms 8-K and 8-K/A at 145, 10-Q and 10-Q/A at 105, 10-K and 10-K/A at 100, DEF 14A and DEFA14A at 95, and 6-K, 20-F and 40-F at 90.
- [x] **FILING-010**: When a filing's form scores zero in the scan's form table, the system shall exclude the filing from the scan and count it as rejected under its form.
- [x] **FILING-011**: When a filing scan selects candidates, the system shall record discovered, selected, and rejected counts per form type as scan diagnostics.
- [x] **FILING-012**: When ordering selected candidate filings, the system shall order by filing date descending, then by form score descending, then by accession number ascending.
- [x] **FILING-013**: The system shall place no limit on how many candidate filings a scan selects, limiting instead the number of fresh document fetches per run (see FILING-014).

## Per-Run Fetch Budget

- [x] **FILING-014**: While performing a dividend record-date scan, the system shall perform at most 250 fresh filing document extractions per run; while performing a split effective-date scan, at most 400.
- [x] **FILING-015**: When a candidate filing has a reusable cached extraction for the scan's section, the system shall reuse it without consuming the run's fresh-fetch budget.
- [x] **FILING-016**: When a candidate filing has no reusable cached extraction and the run's fresh-fetch budget is exhausted, the system shall skip the filing and count it as deferred rather than failing the scan.
- [x] **FILING-017**: When a filing scan completes with deferred filings, the system shall log the deferred count so a ticker still working through unextracted history is distinguishable from one with no further filings.

## Scan Reliability Signal

- [x] **FILING-066**: When a dividend record-date scan completes, the system shall report the scan as degraded if more than three filings failed and the failed count exceeds one quarter of the processed count, so that consumers can distinguish an incomplete candidate list from a genuinely empty one.
- [x] **FILING-067**: When a dividend record-date scan completes, the system shall report its processed and failed filing counts alongside the candidates, so a consumer can judge the result's completeness without re-running the scan.
- [ ] **FILING-068**: When a split effective-date scan completes, the system shall report its processed and failed filing counts and whether it was degraded, on the same terms as FILING-066, so that split persistence decisions which infer from absence can be suppressed during an unhealthy scan.

## Extraction Cache

- [x] **FILING-018**: The system shall persist filing extraction results per CIK and accession number, with independently versioned dividend and split sections.
- [x] **FILING-019**: When reading a cached extraction section whose stored extractor version differs from the current version for that section, the system shall treat the section as absent and re-extract.
- [x] **FILING-020**: If a cached extraction section cannot be parsed, then the system shall treat the section as absent, log the condition, and re-extract.
- [x] **FILING-021**: If persisting a filing extraction violates a data integrity constraint, then the system shall log the condition, return the freshly computed extraction to the caller, and continue the scan.
- [x] **FILING-022**: When persisting a filing's dividend extraction, the system shall store one best record-date candidate with its score, the ex-date candidates deduplicated by date keeping the highest score per date, and the declaration tuples.
- [x] **FILING-023**: When persisting a filing extraction, the system shall stamp the extraction time in the storage zone (UTC).
- [ ] **FILING-024**: When the extraction logic for a section changes (its patterns, scores, document walk, or sentence pass), the system shall require that section's extractor version to change, enforced by a check rather than by convention.

## Document Walk

- [x] **FILING-025**: When extracting from a filing, the system shall scan the primary document first, then up to six exhibit documents.
- [x] **FILING-026**: When discovering a filing's exhibit documents, the system shall accept only hyperlink targets ending in `.htm`, `.html`, or `.txt` whose path contains one of `ex99`, `99-`, `exhibit99`, `exhibit`, `ex-`, `exh`, or `press`, shall strip any query string, shall reduce each to its basename, shall remove duplicates, and shall keep the first six in document order.
- [x] **FILING-027**: When a candidate date is extracted from an exhibit document rather than the primary document, the system shall reduce its score by 5.
- [x] **FILING-028**: When a filing's primary document and exhibits yield no candidates for a given output type (record dates, ex-dividend dates, or declaration tuples), the system shall fetch the full submission text and extract that output type from it.
- [x] **FILING-029**: If fetching a filing's full submission text fails, then the system shall treat it as yielding no candidates rather than failing the filing.
- [x] **FILING-030**: If fetching an exhibit document fails, then the system shall continue with the filing's remaining exhibits.
- [x] **FILING-031**: If extracting a filing fails, then the system shall count the filing as failed, log it, and continue the scan with the remaining filings.

## Candidate Extraction and Scoring

- [x] **FILING-032**: When extracting record-date candidates from filing text, the system shall score matches by pattern: `RECORD_DATE_NEAR_DIVIDEND` 130, `SHAREHOLDER_OF_RECORD` 120, `HOLDERS_OF_RECORD` 115, `RECORD_AT_CLOSE_OF_BUSINESS` 110, `RECORD_DATE_OF` 95, `RECORD_DATE_WILL_BE` 90, `GENERIC_RECORD_DATE` 70.
- [x] **FILING-033**: When extracting ex-dividend-date candidates from filing text, the system shall score `EX_DIVIDEND_DATE_LINE` at 135 and `EX_DIVIDEND_TRADING_START` at 115.
- [x] **FILING-034**: When extracting split-date candidates from filing text and `SPLIT_ADJUSTED_TRADING` matches, the system shall score that match at 130 and shall not consult the weaker split patterns for that document.
- [x] **FILING-035**: When extracting split-date candidates from filing text and `SPLIT_ADJUSTED_TRADING` does not match, the system shall score `SPLIT_EFFECTIVE_DATE` at 125, `SPLIT_DISTRIBUTION_DATE` at 105, and `SPLIT_GENERIC_DATE` at 80.
- [x] **FILING-036**: When extracting dated candidates from a document with a scored pattern list, the system shall keep at most one candidate per distinct date, preferring the higher-scoring match.
- [x] **FILING-037**: When performing sentence-level candidate extraction, the system shall split text on terminal punctuation, keep only sentences containing a trigger term for the scan mode, and discard sentences whose classified intent is generic.
- [x] **FILING-038**: When scoring a date found by sentence-level extraction, the system shall use the sentence intent's base score, plus a proximity boost that decays with the distance from the sentence's anchor phrase, minus two points for each date already extracted from that sentence, floored at 1.
- [x] **FILING-039**: When merging labeled-pattern candidates with sentence-level candidates, the system shall keep one candidate per date, replacing a labeled-pattern candidate only when the sentence candidate compares strictly better.
- [x] **FILING-040**: When a filing scan returns candidates, the system shall order them best first and shall carry each candidate's score, pattern label, source document, and originating accession number.
- [x] **FILING-041**: When more than one filing yields the same candidate date, the system shall keep the candidate with the higher confidence score, breaking ties toward the earlier filing date and then the lower accession number.

## Regex Budget

- [x] **FILING-042**: When matching a filing-text pattern that pairs a lazy bounded quantifier with a month-name alternation, the system shall match against a deadline-guarded input rather than an unguarded character sequence.
- [x] **FILING-043**: The guarded input shall abort a match that exceeds a wall-clock budget of 2000 milliseconds by default, checking the deadline at most once per 4096 characters read.
- [x] **FILING-044**: The system shall use a fresh regex budget for each pattern and each text window, so that one pathological match cannot exhaust the budget of another.
- [x] **FILING-045**: If a filing-text pattern match exceeds its regex budget, then the system shall keep the best candidate found so far and continue the scan, treating the timeout as the absence of further matches.
- [x] **FILING-046**: The system shall leave the guarded patterns textually unchanged, so that a document completing within budget yields the same matches it would without the guard.

## Ex-Dividend Date Derivation

- [x] **FILING-047**: When deriving an ex-dividend date from a record date, the system shall first normalize the record date forward to the next business day.
- [x] **FILING-048**: When the normalized record date falls before 2017-09-05 (T+3 settlement), the system shall set the ex-dividend date two business days before it.
- [x] **FILING-049**: When the normalized record date falls on or after 2017-09-05 and before 2024-05-28 (T+2 settlement), the system shall set the ex-dividend date one business day before it.
- [x] **FILING-050**: When the normalized record date falls on or after 2024-05-28 (T+1 settlement), the system shall set the ex-dividend date equal to the normalized record date.
- [x] **FILING-051**: When performing business-day arithmetic for ex-dividend derivation, the system shall treat as non-business days weekends, New Year's Day, Martin Luther King Jr. Day, Presidents' Day, Good Friday, Memorial Day, Juneteenth in years from 2022, Independence Day, Labor Day, Thanksgiving, and Christmas Day.
- [x] **FILING-052**: When a fixed-date holiday used for ex-dividend business-day arithmetic falls on a Saturday, the system shall observe it on the preceding Friday; when it falls on a Sunday, on the following Monday.
- [x] **FILING-053**: When given no record date, the system shall derive no ex-dividend date.

## Date Grammar and Text Normalization

- [x] **FILING-054**: The system shall recognize month-name dates (abbreviated or full, with optional trailing period and optional comma) and numeric `M/D/YYYY` dates in filing text, using one shared grammar for every filing-text extractor.
- [x] **FILING-055**: When parsing a date from filing text, the system shall normalize `Sept` to `Sep`, strip periods, and collapse whitespace before parsing.
- [x] **FILING-056**: If a date string in filing text matches no recognized format, then the system shall yield no date rather than raising an error.
- [x] **FILING-057**: When normalizing filing text for searching, the system shall remove `<script>` and `<style>` elements together with their content before stripping remaining markup, so that script and style source cannot contribute extracted dates.
- [x] **FILING-058**: When normalizing filing text for searching, the system shall unescape `&nbsp;`, `&amp;`, `&#160;`, `&#8217;`, and `&#8211;`, and collapse runs of whitespace to single spaces.

## Declaration Tuples

- [x] **FILING-059**: When extracting declaration tuples from filing text, the system shall anchor on per-share dollar amounts phrased as an amount followed by a per-share qualifier, as `dividend of` or `distribution of` an amount, or as a `dividend per share` label followed by an amount.
- [x] **FILING-060**: When a declaration amount anchor is not greater than zero or is not less than 100, the system shall reject the anchor as implausible for a per-share cash dividend.
- [x] **FILING-061**: When building a declaration tuple, the system shall search 600 characters on each side of the amount anchor for labeled record, payable, declaration, and ex-dividend dates, taking for each label the occurrence nearest the anchor.
- [x] **FILING-062**: When an amount anchor has no record date within its window, the system shall produce no declaration tuple for that anchor.
- [x] **FILING-063**: When scoring a declaration tuple, the system shall start at 60, add 15 for explicit per-share phrasing, add 10 when a payable date was found, add 10 when a declaration date or an ex-dividend date was found, and cap the result at 100.
- [x] **FILING-064**: When two declaration tuples share the same amount rounded to four decimal places and the same record date, the system shall keep the higher-scoring tuple, and on equal scores the one from the earlier filing date.
- [x] **FILING-065**: When a filing states an ex-dividend date alongside a declaration amount, the system shall carry that stated ex-dividend date on the tuple rather than deriving one from the record date.

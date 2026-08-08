# ETF Distributions Specs

EARS requirements for fund distribution detection from SEC filing text.
Design: [`etf-distributions-design.md`](etf-distributions-design.md).

Markers: `[x]` implemented, `[ ]` active gap, `[D]` deferred.

## Preconditions

- [x] **ETF-001**: When detecting fund distributions for a ticker with no `Listing` row, the system shall skip the ticker and count the skip reason as `listing_missing`.
- [x] **ETF-002**: When detecting fund distributions for a ticker whose listing carries neither a SEC series ID nor a SEC class contract ID, the system shall skip the ticker and count the skip reason as `identity_missing`.
- [x] **ETF-003**: The system shall detect fund distributions from SEC filing text only, without relying on XBRL per-share facts, because funds do not report distributions as XBRL facts.

## Filing Selection

- [x] **ETF-004**: When selecting filings for fund distribution detection, the system shall score forms beginning `497` at 140, forms beginning `485` and forms `N-1A` and `N-1A/A` at 130, and forms `N-CSR`, `N-CSRS`, `N-CSR/A` and `N-CSRS/A` at 115.
- [x] **ETF-005**: When a filing's form scores zero for fund distribution detection, the system shall skip it, count the skip reason as `form_not_relevant`, and count the rejection under its form type.
- [x] **ETF-006**: When a filing's filing date is more than 8 years before the current market date, the system shall skip it and count the skip reason as `filing_stale`.
- [x] **ETF-007**: When selecting filings for fund distribution detection, the system shall process them in filing-date order, newest first.
- [ ] **ETF-008**: While performing fund distribution detection, the system shall limit the number of fresh document fetches per run, so that a scheduled run over a trust's full filing history stays within the SEC request budget.

## Document Resolution

- [x] **ETF-009**: When resolving candidate documents for a fund filing, the system shall include the primary document when named, then filing-index items whose name ends in `.htm`, `.html`, `.txt`, or `.xml` and contains any of `dividend`, `distribution`, `dist`, `income`, `capgain`, `capitalgain`, `supplement`, `class`, `ex99`, `ex-99`, `ex101`, `ex-101`, or `497`.
- [x] **ETF-010**: When resolving candidate documents for a fund filing, the system shall consider at most 8 documents.
- [x] **ETF-011**: If fetching a fund filing's index fails, then the system shall fall back to the primary document alone.
- [x] **ETF-012**: When a fund filing yields no candidate documents, the system shall skip the filing and count the skip reason as `no_candidate_documents`.
- [x] **ETF-013**: When a fund filing document exceeds 1,250,000 characters, the system shall truncate it to that length before scoring or extraction.
- [x] **ETF-014**: When scoring a fund filing's candidate documents, the system shall also fetch the full submission text and score it on the same footing, allowing it to be selected over any individual document.
- [x] **ETF-015**: When every candidate document for a fund filing fails to fetch or is blank, the system shall skip the filing and count the skip reason as `document_fetch_failed_or_empty`.
- [x] **ETF-016**: If fetching an individual fund filing document fails, then the system shall continue with the filing's remaining candidate documents.
- [ ] **ETF-017**: When a fund filing document is truncated, the system shall record that it was truncated, so that a distribution table beyond the truncation point is distinguishable from a document containing none.

## Identity Scoring

- [x] **ETF-018**: When evaluating whether a fund filing document concerns a given ticker, the system shall add 4 points for the listing's SEC series ID appearing in the text, 4 for its SEC class contract ID, 3 for its SEC class ticker as a token, 2 for the ticker itself as a token, 2 for a series name match, 2 for a class name match, 1 for the ticker appearing in the document name, and 1 for a form type containing `497`.
- [x] **ETF-019**: When matching a fund series or class name against filing text, the system shall require the normalized name to be at least 6 characters, and shall accept either a full substring match or at least two constituent words of 4 or more characters appearing as tokens.
- [x] **ETF-020**: When scoring a fund filing's candidate documents for identity, the system shall select the highest-scoring document as the filing's text.
- [x] **ETF-021**: When the best identity score for a fund filing is below 2, the system shall skip the filing and count the skip reason as `identity_mismatch`.
- [ ] **ETF-022**: When extracting a distribution amount and dates from a fund filing document that concerns more than one fund, the system shall restrict extraction to the region of text where this ticker's identity matched, so that another fund's table row cannot be attributed to this ticker.

## Amount Extraction

- [x] **ETF-023**: When extracting a fund distribution amount, the system shall score a dollar amount preceded within 80 characters by a distribution or dividend keyword at 90, a dollar amount followed by `per share` at 85, and a dividend or distribution keyword with a dollar amount on the same line at 75.
- [x] **ETF-024**: When extracting a fund distribution amount, the system shall reject any candidate that is not positive or is 50 or greater, as implausible for a per-share fund distribution.
- [x] **ETF-025**: When more than one fund distribution amount candidate is found in a document, the system shall select the highest-scoring candidate.
- [x] **ETF-026**: When no fund distribution amount candidate is found in a filing, the system shall skip the filing and count the skip reason as `amount_missing`.

## Date Resolution

- [x] **ETF-027**: When resolving a fund distribution's effective date, the system shall prefer an extracted ex-dividend date at confidence 95, then a date derived from an extracted record date at confidence 86, then the business day before an extracted payable date at confidence 62, then the business day before the filing date at confidence 55.
- [x] **ETF-028**: When a fund distribution's payable date was extracted, the system shall add 5 to the resolution confidence, capped at 100.
- [x] **ETF-029**: When deriving a fund distribution's ex-dividend date from its record date, the system shall use the same settlement-era rules and NYSE calendar the equity path uses, rather than a fund-specific derivation.
- [x] **ETF-030**: When gathering fund distribution date candidates from a role-keyword sentence, the system shall score an ISO-formatted date at 90, a month-name date at 88, and any other recognized date at 82.
- [x] **ETF-031**: When gathering fund distribution date candidates with the whole-document labeled-date pattern, the system shall score them at 92 and shall match against a deadline-guarded input.
- [x] **ETF-032**: If the whole-document labeled-date pattern exceeds its regex budget, then the system shall keep the candidates already collected and continue with the sentence and table passes.
- [x] **ETF-033**: When gathering fund distribution date candidates from table lines, the system shall examine each line together with the two following lines, and shall score ex and record candidates at 80 and payable candidates at 78.
- [x] **ETF-034**: When a fund filing's table window contains no distribution signal (no dividend, distribution or per-share keyword and no dollar amount), the system shall extract no date candidates from that window.
- [x] **ETF-035**: When a fund filing's table window matches annual-report boilerplate (`year ended`, `fiscal year`, `for the year`, or `annual`) and carries no explicit date label, the system shall extract no date candidates from that window.
- [x] **ETF-036**: When selecting the best fund distribution date candidate for a role, the system shall prefer the highest score, then the candidate closest to the filing date, then the earliest date.
- [x] **ETF-037**: When no effective date can be resolved for a fund distribution, the system shall skip the filing and count the skip reason as `date_missing`.
- [x] **ETF-038**: When a fund distribution's date resolution confidence is below 70, the system shall not persist the distribution, and shall count the skip reason as `below_confidence`.
- [ ] **ETF-039**: The system shall not offer a fund distribution date resolution path whose maximum achievable confidence falls below the persistence threshold, so that every resolution path can produce a persisted row.

## Persistence

- [x] **ETF-040**: When persisting a fund distribution, the system shall set the action type to DIVIDEND, the source type to `SEC_ETF_FILING`, and both the raw and adjusted amounts to the extracted amount.
- [x] **ETF-041**: When persisting a fund distribution, the system shall store the originating form type and accession number, the extracted record and payable dates, the resolution confidence, and the listing's SEC series ID and class contract ID.
- [x] **ETF-042**: When a fund distribution row already exists with the same ticker, action type, effective date, amount, and `SEC_ETF_FILING` source type, the system shall skip the insert and count the skip reason as `duplicate`.
- [x] **ETF-043**: The system shall not restate fund distribution amounts for splits, storing raw and adjusted amounts as equal.
- [ ] **ETF-044**: When a fund distribution re-detection resolves a different effective date for a distribution already stored from the same accession, the system shall reconcile the stored row rather than inserting a second row.

## Diagnostics

- [x] **ETF-045**: When fund distribution detection completes for a ticker, the system shall report counts of filings considered, filings fetched, identity matches, amounts extracted, dates extracted, below-confidence outcomes, and duplicates.
- [x] **ETF-046**: When fund distribution detection completes for a ticker, the system shall report skip counts by reason, identity scores in buckets, amount source counts, date resolution path counts, and per-form discovered, eligible and rejected counts.
- [x] **ETF-047**: When fund distribution detection persists rows for a ticker, the system shall retain up to 10 sample rows carrying accession number, form type, source document, effective date, amount, and identity score.
- [ ] **ETF-048**: When fund distribution detection completes for a ticker, the system shall record when that ticker was last scanned, so that stale fund coverage is detectable.

## Trigger

- [ ] **ETF-049**: The system shall provide an automated trigger for fund distribution detection, so that fund distributions do not go stale by default; the nightly price load excludes funds deliberately (its `equity-only` setting) because an unbudgeted fund scan would dominate that task's runtime.

# Equity Corporate Actions Specs

EARS requirements for equity split and dividend detection from SEC XBRL facts, filing text, and the raw
price series. Design: [`equity-actions-design.md`](equity-actions-design.md).

Facets: `EQUITY-*` orchestration, `EQUITY-SPLIT-*` split detection, `EQUITY-DIV-*` dividend detection.

Markers: `[x]` implemented, `[ ]` active gap, `[D]` deferred.

## Orchestration

- [x] **EQUITY-001**: When detecting corporate actions for an equity, the system shall fetch the CIK's SEC company facts once and use that single payload for both split and dividend detection.
- [x] **EQUITY-002**: When detecting corporate actions for an equity, the system shall detect and persist splits before detecting dividends, so that dividend amount restatement (EQUITY-DIV-024) sees the current split rows.
- [x] **EQUITY-003**: When detecting corporate actions for an equity, the system shall open a ticker-scoped SEC document cache before detection and close it after detection completes or fails.
- [x] **EQUITY-004**: If fetching or parsing a CIK's SEC company facts fails, then the system shall return a detection report carrying the failure reason `sec_fetch_failed` with empty statistics, rather than propagating the failure.
- [x] **EQUITY-005**: When equity detection completes, the system shall report split outcome counts separating price-corroborated, price-snapped, price-rejected, price-only-detected, and price-only-unconfirmed candidates.
- [x] **EQUITY-006**: When equity detection completes, the system shall report dividend assignment counts separating tuple-matched, direct-ex-text, dynamic-programming, synthetic, and promoted assignments.
- [x] **EQUITY-007**: When equity detection completes, the system shall report the record-date scan's failed filing count and whether the scan was degraded.

## Split Detection: Ratio Candidates

- [x] **EQUITY-SPLIT-001**: When detecting equity splits, the system shall read shares-outstanding facts from the SEC XBRL `dei` cover-page concept, restricted to the reporting forms that carry a reliable cover-page share count.
- [x] **EQUITY-SPLIT-002**: When comparing consecutive shares-outstanding facts, the system shall compute the ratio of the later count to the earlier count and snap it to a known split ratio within a 2% tolerance.
- [x] **EQUITY-SPLIT-003**: The system shall treat 2, 3, 4, 5, 7, 10, 20, 50 and their reciprocals as primary split ratios requiring no filing confirmation.
- [x] **EQUITY-SPLIT-004**: The system shall treat 1.5 and 4/3 as extended split ratios, and shall persist such a candidate only when a filing split-date candidate matches or a corroborating price break is found.
- [x] **EQUITY-SPLIT-005**: When two consecutive shares-outstanding facts are more than 400 days apart, the system shall not treat their ratio as a single split candidate.

## Split Detection: Dating

- [x] **EQUITY-SPLIT-006**: When dating an equity split, the system shall produce a date conforming to the effective-date convention defined by PRICE-ADJ-APPLY-006 (the first trade date at the new price basis, which the adjustment pass writes with the pre-action factor).
- [x] **EQUITY-SPLIT-007**: When dating an equity split candidate, the system shall prefer a corroborating overnight price break, then a filing split-date candidate, then the date of the later shares-outstanding fact.
- [x] **EQUITY-SPLIT-008**: When an equity split is dated from a corroborating price break, the system shall record its ex-date source as `PRICE_BREAK` with confidence 100; from filing text, `FILING_TEXT` with confidence 70; from the shares-outstanding fact date, `SHARE_FACT` with confidence 40.
- [x] **EQUITY-SPLIT-009**: When searching for an equity split's corroborating price break, the system shall search the bracket between the two shares-outstanding facts padded by 10 days, widened to include a matched filing split-date candidate.
- [x] **EQUITY-SPLIT-010**: When matching a filing split-date candidate to an equity split candidate, the system shall accept candidates from 260 days before to 60 days after the share-fact date.
- [x] **EQUITY-SPLIT-011**: When corroborating an equity split multiplier against the raw close series, the system shall compare overnight moves in log space, accepting a deviation up to 15% for multipliers at or above 2 (or at or below 0.5), and up to 6% for extended ratios.
- [x] **EQUITY-SPLIT-012**: When corroborating an equity split multiplier, the system shall exclude price rows without a positive close from the series.
- [x] **EQUITY-SPLIT-013**: When a corroborating price break and a filing split-date candidate for the same equity split disagree by more than 7 days, the system shall log the disagreement.
- [x] **EQUITY-SPLIT-027**: When more than one overnight move inside an equity split candidate's search window falls within tolerance of its multiplier, the system shall select the move closest to the multiplier in log space.
- [x] **EQUITY-SPLIT-028**: When two shares-outstanding facts report the same date, the system shall keep the larger share count, so that a superseded cover-page figure cannot manufacture a ratio change.

## Split Detection: False-Positive Veto

- [x] **EQUITY-SPLIT-014**: When an equity split candidate's raw close series spans the entire search window and no overnight move matches the candidate's multiplier, the system shall reject the candidate and count it as price-rejected.
- [x] **EQUITY-SPLIT-015**: When an equity split candidate's raw close series does not span the entire search window, the system shall not apply the price-rejection veto, because absence of a break outside the price history is absence of data rather than evidence of absence.

## Split Detection: Persistence

- [x] **EQUITY-SPLIT-016**: When an equity split candidate matches a stored split within 90 days whose ratio agrees within 1%, and the new resolution is at least as well grounded as the stored one, the system shall re-date the stored row in place rather than inserting a new row.
- [x] **EQUITY-SPLIT-017**: When persisting a newly detected equity split from XBRL share counts, the system shall set the action type to SPLIT, the ratio to the reciprocal of the snapped share multiplier, and the source type to `SEC_EQUITY_XBRL`.
- [ ] **EQUITY-SPLIT-024**: When a stored equity split is no longer detected, its confidence is below 70, and its source type is one this segment writes, the system shall delete it, so that a split persisted on weak evidence (a share-count fact date at 40, or price evidence alone at 60) does not adjust the series permanently.
- [ ] **EQUITY-SPLIT-025**: When a stored equity split is no longer detected and its confidence is 70 or above, the system shall retain it, so that a price-break, filing-text, or price-corroborated split is never removed by a run that merely failed to re-derive it.
- [ ] **EQUITY-SPLIT-026**: While the split effective-date scan for a ticker is reported degraded (see FILING-068), the system shall not delete any stored equity split, because absence of a re-detection is uninformative when the evidence source was unhealthy.

## Split Detection: Price-First Detection

- [x] **EQUITY-SPLIT-018**: When scanning an equity's raw close series for unexplained split-like breaks, the system shall accept an overnight move that snaps within 10% in log space to one of 2, 3, 4, 5, 7, 10, 20, 50 or their reciprocals.
- [x] **EQUITY-SPLIT-019**: When evaluating an unexplained split-like break, the system shall require the median close over the 5 trading days after the break to differ from the median over the 5 trading days before it by the break's multiplier within 25% in log space, so that one-day glitches and recovered crashes are excluded.
- [x] **EQUITY-SPLIT-020**: When an unexplained split-like break falls within 7 days of an already persisted split, the system shall skip it as already explained.
- [x] **EQUITY-SPLIT-021**: When an unexplained split-like break has a SEC filing split-date candidate within 14 days, the system shall persist it with source type `SEC_PRICE_CORROBORATED` and confidence 90.
- [x] **EQUITY-SPLIT-022**: When an unexplained split-like break has no filing support and its multiplier is at or above 5 (or at or below 0.2), the system shall persist it with source type `SEC_PRICE_CORROBORATED` and confidence 60.
- [x] **EQUITY-SPLIT-023**: When an unexplained split-like break has no filing support and its multiplier is between 0.2 and 5, the system shall not persist it, and shall count it as price-only-unconfirmed.

## Dividend Detection: Facts to Events

- [x] **EQUITY-DIV-001**: When parsing equity dividend facts, the system shall read per-share dividend concepts from SEC XBRL `us-gaap` facts, restricted to USD per-share units.
- [x] **EQUITY-DIV-002**: When parsing equity dividend facts, the system shall prefer the concepts `CommonStockDividendsPerShareDeclared`, `CommonStockDividendsPerShareCashPaid`, `CommonStockDividendsPerShareDeclaredAndPaid`, and `DividendsPaidPerShare`.
- [x] **EQUITY-DIV-003**: When normalizing equity dividend facts, the system shall group them by fiscal period end and produce at most one regular event per period end.
- [x] **EQUITY-DIV-004**: When normalizing equity dividend facts, the system shall classify a fact's reporting period as quarter-length at 120 days or fewer, intermediate above 120 and below 250 days, and annual at 250 days or more, and shall treat a fact with no start date as belonging to none of these classes.
- [x] **EQUITY-DIV-005**: When a fiscal period end has one or more quarter-length equity dividend facts, the system shall select among them by shortest reporting period, then by form priority (10-Q, then 8-K, then 10-K, then any other form), then by earliest filing date.
- [x] **EQUITY-DIV-006**: When equity dividend normalization yields no events, the system shall skip the filing record-date scan entirely.
- [x] **EQUITY-DIV-040**: When a fiscal period end has no quarter-length equity dividend fact, the system shall select among that period's facts excluding annual and intermediate ones, ordering by whether the fact has a start date, then by preferred concept, then by form priority, then by earliest filing date.
- [x] **EQUITY-DIV-041**: When selecting a fallback equity dividend fact for a period end, the system shall discard a fact that has no start date and whose amount equals that of an intermediate-length fact at the same period end, because such a fact is the year-to-date cumulative figure rather than a quarterly dividend.
- [x] **EQUITY-DIV-042**: When normalizing equity dividend facts, the system shall never emit an intermediate-length or annual-length fact as a dividend event.

## Dividend Detection: Derived Fourth Quarter

- [x] **EQUITY-DIV-043**: When an annual equity dividend fact covers a fiscal year, the system shall derive the missing fourth-quarter amount as the annual total less the sum of the quarterly amounts already known for period ends inside that fiscal year.
- [x] **EQUITY-DIV-044**: When a derived fourth-quarter equity dividend amount is not positive, the system shall discard it.
- [x] **EQUITY-DIV-045**: When a derived fourth-quarter equity dividend amount exceeds 2.5 times the median of the known prior quarterly amounts in that fiscal year, the system shall discard it as implausible.
- [x] **EQUITY-DIV-046**: When a derived fourth-quarter equity dividend amount is accepted and no amount is yet recorded at the fiscal year end, the system shall record the derived amount there.
- [x] **EQUITY-DIV-047**: When a derived fourth-quarter equity dividend amount is accepted and an amount is already recorded at the fiscal year end, the system shall replace it only when the recorded amount exceeds the derived amount by more than 0.02, which is the signature of a cumulative figure having been read as a quarter.
- [x] **EQUITY-DIV-048**: When more than one annual equity dividend fact shares a fiscal year end, the system shall derive a fourth quarter from at most one of them.

## Dividend Detection: Special Classification

- [x] **EQUITY-DIV-049**: When an equity dividend fact at a period end differs from that period's selected regular amount, the system shall classify it as a special event when it is at least 2.8 times the regular amount and at least 0.75 greater in absolute terms, or when it comes from an 8-K and exceeds the regular amount by more than 0.25.
- [x] **EQUITY-DIV-050**: When classifying special equity dividends, the system shall consider neither annual nor intermediate-length facts.
- [x] **EQUITY-DIV-051**: When a special equity dividend candidate shares both period end and amount with a regular event, the system shall discard the candidate as the same cash counted twice, and shall deduplicate remaining candidates by period end and amount.
- [x] **EQUITY-DIV-052**: When emitting normalized equity dividend amounts, the system shall round them to four decimal places.
- [x] **EQUITY-DIV-037**: When parsing equity dividend facts, the system shall accept facts only from the reporting forms that carry reliable per-share dividend disclosure, and shall skip facts from other forms.
- [x] **EQUITY-DIV-038**: When parsing equity dividend facts, the system shall skip any fact whose per-share value is not positive.
- [x] **EQUITY-DIV-039**: When parsing equity dividend facts, the system shall deduplicate facts identical in period, value, form, and filing date.

## Dividend Detection: Ex-Date Assignment

- [x] **EQUITY-DIV-007**: When assigning equity dividend ex-dates, the system shall try, in order: an amount-anchored declaration tuple, a directly extracted ex-date, a dynamic-programming assignment over record-date candidates, and finally a synthesized date.
- [x] **EQUITY-DIV-008**: When assigning equity dividend ex-dates, the system shall consume each declaration tuple and each date candidate at most once across all events.
- [x] **EQUITY-DIV-009**: When matching a declaration tuple to an equity dividend event, the system shall accept the tuple's amount as matching either the event's raw XBRL amount or that amount with later split restatement undone (the raw amount divided by the product of subsequent split ratios).
- [x] **EQUITY-DIV-010**: When matching a declaration tuple amount to an equity dividend event amount, the system shall accept a difference up to the greater of 0.0005 absolute and 0.5% relative.
- [x] **EQUITY-DIV-011**: When matching a declaration tuple to an equity dividend event, the system shall require the tuple's record date to fall from 0 to 95 days after the event's fiscal period end, preferring offsets near 45 days and then higher tuple confidence.
- [x] **EQUITY-DIV-012**: When an equity dividend event is matched to a declaration tuple, the system shall set its ex-date source to `TUPLE_MATCHED` with confidence 95, use the tuple's stated ex-date when present and otherwise derive one from the tuple's record date, and carry the tuple's record and payable dates onto the event.
- [x] **EQUITY-DIV-013**: When matching a directly extracted ex-date candidate to a regular equity dividend event, the system shall require the candidate to fall from 5 to 130 days after the fiscal period end, shall rank candidates by the absolute deviation of the gap from 45 days less the candidate confidence divided by 25, and shall set the ex-date source to `DIRECT_EX_TEXT` with confidence 90.
- [x] **EQUITY-DIV-014**: When assigning record dates to remaining regular equity dividend events, the system shall compute a globally optimal assignment over all such events rather than matching each event greedily, and shall set the ex-date source to `RECORD_DP` with confidence 60.
- [x] **EQUITY-DIV-015**: When considering a record-date candidate for an equity dividend event in the dynamic-programming assignment, the system shall require the record date to fall from 10 to 80 days after the fiscal period end and the candidate's filing date to be no earlier than 5 days before the fiscal period end.
- [x] **EQUITY-DIV-016**: When computing the dynamic-programming assignment cost for an equity dividend event, the system shall use the absolute deviation of the record-date offset from 42 days, plus half the absolute deviation of the cadence gap from 91 days, less the candidate confidence divided by 12, and shall charge a penalty of 140 for leaving an event unassigned.
- [x] **EQUITY-DIV-017**: When an equity dividend event remains unassigned after all evidence-based paths, the system shall synthesize a record date from the last matched record date plus 91 days, or from the fiscal period end plus 42 days when no record date was matched, derive an ex-date from it, and set the ex-date source to `SYNTHETIC` with confidence 10.
- [x] **EQUITY-DIV-018**: The system shall persist an equity dividend event whose ex-date could only be synthesized, rather than discarding the event.
- [x] **EQUITY-DIV-019**: When assigning ex-dates to special equity dividend events, the system shall match them separately from regular events against unused candidates, without applying a quarterly cadence expectation.

## Dividend Detection: Declaration Promotion

- [x] **EQUITY-DIV-020**: When a declaration tuple's record date falls after the newest XBRL fiscal period end, the system shall promote the tuple to a provisional equity dividend event, so that a dividend declared between the 8-K and the next periodic report is not missing from the history.
- [x] **EQUITY-DIV-021**: When promoting a declaration tuple to a provisional equity dividend event, the system shall require the tuple's confidence to be at least 85.
- [x] **EQUITY-DIV-022**: When promoting a declaration tuple to a provisional equity dividend event, the system shall require the tuple's record date to be no more than 200 days old, so that promotion covers only the current reporting blind window.
- [x] **EQUITY-DIV-023**: When promoting a declaration tuple to a provisional equity dividend event, the system shall reject a tuple whose amount is more than 3 times or less than one third of the newest regular dividend amount.

## Dividend Detection: Amount Restatement

- [x] **EQUITY-DIV-024**: When persisting an equity dividend event, the system shall restate its per-share amount onto the current share basis by the product of the ratios of splits effective after the event.
- [x] **EQUITY-DIV-025**: When persisting an equity dividend event, the system shall store both the amount on the price scale that applied at its own ex-date and the amount restated onto the current share basis.
- [x] **EQUITY-DIV-053**: When determining which splits fall after an equity dividend event, the system shall anchor on the event's assigned ex-date when it has one, and on its fiscal period end otherwise.
- [x] **EQUITY-DIV-054**: When applying split ratios to equity dividend amounts, the system shall first snap each split's ratio to the nearest common split ratio within 2%, so that a slightly imprecise stored ratio does not propagate into every restated dividend.
- [x] **EQUITY-DIV-055**: When a filer has already restated pre-split equity dividend amounts onto the post-split basis, the system shall remove that split's ratio from the stored raw amount rather than applying it, so that the raw amount stays on the price scale of its own ex-date (the scale PRICE-ADJ-APPLY-009 requires).
- [x] **EQUITY-DIV-056**: When identifying which pre-split equity dividend events a filer already restated, the system shall take the first event at or after the split as a scale anchor, walk earlier events backwards while each stays within 30% of that anchor, and treat the earliest event of that contiguous run as the cutoff at or after which amounts are already restated.

## Dividend Detection: Persistence and Provenance

- [x] **EQUITY-DIV-026**: When persisting an equity dividend event, the system shall match it against stored dividends first by exact ticker, date, and amount, and then within the same year by approximate amount, so that a re-detection corrects a stored date rather than inserting a duplicate.
- [x] **EQUITY-DIV-027**: The system shall rank equity dividend ex-date sources as `TUPLE_MATCHED` above `DIRECT_EX_TEXT`, above `RECORD_DP`, above `SYNTHETIC`, above unknown.
- [x] **EQUITY-DIV-028**: When a re-detected equity dividend event matches a stored row whose ex-date source ranks higher than the new event's, the system shall leave the stored effective date and provenance unchanged.
- [x] **EQUITY-DIV-029**: When a re-detected equity dividend event matches a stored row, the system shall update the stored amounts regardless of ex-date source rank.
- [x] **EQUITY-DIV-030**: When persisting a newly detected equity dividend, the system shall set the action type to DIVIDEND, the effective date to the assigned ex-date, and the source type to `SEC_EQUITY_XBRL`.
- [x] **EQUITY-DIV-031**: If inserting an equity dividend violates a unique constraint, then the system shall re-read the stored dividends for that date and abandon the insert when the event is already present.
- [x] **EQUITY-DIV-032**: When pruning stored equity dividends that detection no longer produces, the system shall prune only rows whose effective date falls within the detected year range, only rows whose source type is `SEC_EQUITY_XBRL`, and only rows whose ex-date source ranks below `DIRECT_EX_TEXT`.
- [x] **EQUITY-DIV-033**: When a stored equity dividend is no longer detected but its ex-date source ranks at `DIRECT_EX_TEXT` or above, the system shall log it for review and shall not delete it, so that deleting and re-inserting cannot bypass the date-move guard in EQUITY-DIV-028.

## Dividend Detection: Degraded-Scan Suppression

- [x] **EQUITY-DIV-034**: While the record-date scan for a ticker is reported degraded, the system shall not insert an equity dividend event whose ex-date source rank is at or below `SYNTHETIC`.
- [x] **EQUITY-DIV-035**: While the record-date scan for a ticker is reported degraded, the system shall not prune any stored equity dividend.
- [x] **EQUITY-DIV-036**: While the record-date scan for a ticker is reported degraded, the system shall continue updating stored equity dividends that match detected events, because those updates are matched rather than inferred from absence.

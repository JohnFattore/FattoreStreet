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

- [x] **EQUITY-SPLIT-006**: The system shall define an equity split's effective date as the first trade date at the new price basis, matching the date the price-adjustment pass assigns the pre-action factor to.
- [x] **EQUITY-SPLIT-007**: When dating an equity split candidate, the system shall prefer a corroborating overnight price break, then a filing split-date candidate, then the date of the later shares-outstanding fact.
- [x] **EQUITY-SPLIT-008**: When an equity split is dated from a corroborating price break, the system shall record its ex-date source as `PRICE_BREAK` with confidence 100; from filing text, `FILING_TEXT` with confidence 70; from the shares-outstanding fact date, `SHARE_FACT` with confidence 40.
- [x] **EQUITY-SPLIT-009**: When searching for an equity split's corroborating price break, the system shall search the bracket between the two shares-outstanding facts padded by 10 days, widened to include a matched filing split-date candidate.
- [x] **EQUITY-SPLIT-010**: When matching a filing split-date candidate to an equity split candidate, the system shall accept candidates from 260 days before to 60 days after the share-fact date.
- [x] **EQUITY-SPLIT-011**: When corroborating an equity split multiplier against the raw close series, the system shall compare overnight moves in log space, accepting a deviation up to 15% for multipliers at or above 2 (or at or below 0.5), and up to 6% for extended ratios.
- [x] **EQUITY-SPLIT-012**: When corroborating an equity split multiplier, the system shall exclude price rows without a positive close from the series.
- [x] **EQUITY-SPLIT-013**: When a corroborating price break and a filing split-date candidate for the same equity split disagree by more than 7 days, the system shall log the disagreement.

## Split Detection: False-Positive Veto

- [x] **EQUITY-SPLIT-014**: When an equity split candidate's raw close series spans the entire search window and no overnight move matches the candidate's multiplier, the system shall reject the candidate and count it as price-rejected.
- [x] **EQUITY-SPLIT-015**: When an equity split candidate's raw close series does not span the entire search window, the system shall not apply the price-rejection veto, because absence of a break outside the price history is absence of data rather than evidence of absence.

## Split Detection: Persistence

- [x] **EQUITY-SPLIT-016**: When an equity split candidate matches a stored split within 90 days whose ratio agrees within 1%, and the new resolution is at least as well grounded as the stored one, the system shall re-date the stored row in place rather than inserting a new row.
- [x] **EQUITY-SPLIT-017**: When persisting a newly detected equity split from XBRL share counts, the system shall set the action type to SPLIT, the ratio to the reciprocal of the snapped share multiplier, and the source type to `SEC_EQUITY_XBRL`.

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
- [x] **EQUITY-DIV-003**: When normalizing equity dividend facts, the system shall group them by fiscal period end and select one event per period, preferring quarter-length periods of at most 120 days, then form priority, then the earliest filing date.
- [x] **EQUITY-DIV-004**: When a normalized equity dividend event covers a period of 250 days or more, the system shall classify it as a special event rather than a regular quarterly one.
- [x] **EQUITY-DIV-005**: When a fourth-quarter equity dividend amount exceeds 2.5 times the running regular amount, the system shall classify the event as special.
- [x] **EQUITY-DIV-006**: When equity dividend normalization yields no events, the system shall skip the filing record-date scan entirely.

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

- [x] **EQUITY-DIV-024**: When persisting an equity dividend event, the system shall restate its per-share amount onto the current share basis by dividing by the product of the ratios of splits effective after the event.
- [x] **EQUITY-DIV-025**: When persisting an equity dividend event, the system shall store both the amount as declared and the amount restated onto the current share basis.

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

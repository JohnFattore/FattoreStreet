# Price Validation Specs

EARS requirements for the read-only adjusted-price and corporate-action accuracy checks and the weekly
report. Design: [`price-validation-design.md`](price-validation-design.md).

Markers: `[x]` implemented, `[ ]` active gap, `[D]` deferred.

## Licensing Boundary

- [x] **PRICE-VAL-001**: The price validation services shall never write to the database.
- [x] **PRICE-VAL-002**: The system shall emit only derived comparison statistics from price validation, and shall never persist, return from an API, or render any value sourced from the external reference series.
- [x] **PRICE-VAL-003**: The system shall obtain reference adjusted closes and reference corporate actions through the Django portfolio service rather than fetching them directly, so the reference crosses one process boundary only.
- [x] **PRICE-VAL-004**: The read-only property of the validation run mode shall be asserted by a test rather than left to convention, because it is what keeps the run mode inside the data-licensing rule.

## Adjusted-Price Comparison

- [x] **PRICE-VAL-005**: When validating a ticker's adjusted prices, the system shall compute, for each date present in both the stored and reference series, the ratio of the stored adjusted close to the reference adjusted close.
- [x] **PRICE-VAL-006**: When computing adjusted-price ratios for a ticker, the system shall exclude any date where either series has a non-positive value.
- [x] **PRICE-VAL-007**: When measuring a ticker's adjusted-price deviation, the system shall normalize the ratio series at the latest date common to both series, so that a constant level offset between the two series reads as zero deviation and only event differences remain.
- [x] **PRICE-VAL-008**: When measuring a ticker's adjusted-price deviation, the system shall count a date as out of tolerance when its normalized ratio deviates from 1 by more than 0.005 in absolute value.
- [x] **PRICE-VAL-009**: When validating a ticker's adjusted prices, the system shall report the compared date count, the first and last compared dates, the mean absolute deviation, the maximum absolute deviation, and the out-of-tolerance date count.
- [ ] **PRICE-VAL-010**: When normalizing a ticker's adjusted-price ratio series, the system shall anchor on a value robust to a single bad print (such as a median over the most recent common dates), so that one corrupted date cannot make an entire ticker's history read as out of tolerance.

## Break Detection

- [x] **PRICE-VAL-011**: When validating a ticker's adjusted prices, the system shall report a break at any date whose day-over-day ratio step exceeds 0.01 in absolute value, because such a step localizes a missing, extra, or misdated corporate action.
- [x] **PRICE-VAL-012**: When reporting an adjusted-price break, the system shall include its date, its signed step size, and the stored corporate action nearest to it within 7 days, or none when no action falls in that window.
- [x] **PRICE-VAL-013**: When validating one ticker's adjusted prices, the system shall report at most 20 breaks.
- [ ] **PRICE-VAL-014**: When reporting an adjusted-price break alongside a nearby stored action, the system shall state whether the break's magnitude is consistent with that action's size, so that a mis-scaled action is distinguishable from a missing one.

## Per-Ticker Statuses

- [x] **PRICE-VAL-015**: When a ticker has no stored adjusted prices in the validation window, the system shall report the status `no_adjusted_prices` and compare nothing.
- [x] **PRICE-VAL-016**: When a ticker has fewer than two stored price rows, or fewer than two dates common to both series, the system shall report the status `insufficient_overlap`.
- [x] **PRICE-VAL-017**: When the reference series for a ticker is empty, the system shall report the status `no_reference_data`.

## Batch Validation

- [x] **PRICE-VAL-018**: If validating one ticker throws during a batch, then the system shall count that ticker as skipped and continue with the remaining tickers, because a partial report over a large index is more useful than none.
- [x] **PRICE-VAL-019**: When summarizing a validation batch, the system shall report tickers checked, tickers skipped, tickers out of tolerance, and the total break count.
- [x] **PRICE-VAL-020**: When summarizing a validation batch, the system shall include at most 10 worst tickers and at most 20 breaks, because the summary is delivered as an email body.
- [ ] **PRICE-VAL-021**: When a validation batch completes having checked no tickers because every ticker lacked reference data, the system shall fail rather than reporting a healthy run, for the same reason an empty scope fails (PRICE-VAL-025).
- [ ] **PRICE-VAL-022**: When a validation batch completes, the system shall persist its derived summary statistics, so that the share of in-scope tickers within tolerance is comparable across runs and a persistent error is distinguishable from a new regression.

## Corporate-Action Diffing

- [x] **PRICE-VAL-023**: When diffing stored corporate actions against reference events for a ticker, the system shall pair dividends whose dates fall within 60 days and splits whose dates fall within 7 days, and shall report each pair's date difference, because the window's purpose is establishing correspondence rather than judging correctness.
- [x] **PRICE-VAL-024**: When comparing a paired corporate action against its reference event, the system shall treat dividend amounts as agreeing within 2% and split ratios as agreeing within 1%, and shall normalize a reference split's share multiplier to the stored ratio convention before comparing.

## Weekly Task

- [x] **PRICE-VAL-025**: If the configured validation index resolves to zero members, then the system shall log the condition at error level and exit with code 1, rather than reporting a healthy run over an empty scope.
- [x] **PRICE-VAL-026**: If the configured validation minimum date is not an ISO date, then the system shall exit with code 1.
- [x] **PRICE-VAL-027**: When selecting tickers for the weekly validation run, the system shall order the configured index's members by index weight descending.
- [x] **PRICE-VAL-028**: Where a maximum ticker count is configured for the weekly validation run, the system shall validate only the heaviest that many members, and shall log that the run was capped.
- [x] **PRICE-VAL-029**: Where no SNS topic is configured for the weekly validation run, the system shall log the rendered report instead of publishing it, and shall treat that as success.
- [x] **PRICE-VAL-030**: If publishing the weekly validation report fails, then the system shall exit with code 1, because a report nobody receives is indistinguishable from a run that never happened.
- [x] **PRICE-VAL-031**: When the weekly validation run completes and its report is delivered, the system shall exit with code 0 even when individual tickers were skipped.
- [x] **PRICE-VAL-032**: Where `app.run-mode` is not `validate-prices`, the system shall create neither the validation runner nor the report publisher, so that other deployments construct no SNS client and require no AWS credentials.
- [x] **PRICE-VAL-033**: When the weekly validation run finishes, the system shall terminate the JVM with the computed exit code after running shutdown hooks.

## Report Rendering

- [x] **PRICE-VAL-034**: When rendering the validation report, the system shall produce plain text carrying the index code, the comparison window start, the elapsed time, and the batch summary.
- [x] **PRICE-VAL-035**: When a rendered validation report would exceed 250,000 bytes, the system shall truncate it, leaving headroom below the SNS 256 KB message limit for the truncation notice.
- [x] **PRICE-VAL-036**: When a validation report is truncated, the system shall state in the report body that it was truncated, so that a shortened report cannot read as a complete one.
- [x] **PRICE-VAL-037**: When publishing a validation report, the system shall limit the message subject to 100 ASCII characters.

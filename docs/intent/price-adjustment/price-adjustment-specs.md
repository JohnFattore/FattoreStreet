# Price Adjustment Specs

EARS requirements for the nightly working-set selection, SEC detection scheduling, and backward-cumulative
price adjustment. Design: [`price-adjustment-design.md`](price-adjustment-design.md).

Facets: `PRICE-ADJ-SCOPE-*` working set and detection scheduling, `PRICE-ADJ-APPLY-*` adjustment
arithmetic.

Markers: `[x]` implemented, `[ ]` active gap, `[D]` deferred.

## Working Set

- [x] **PRICE-ADJ-SCOPE-001**: When running a full price adjustment pass, the system shall build its working set from the union of tickers having any null adjusted price column and tickers scheduled for stale SEC re-detection.
- [x] **PRICE-ADJ-SCOPE-002**: Where the force option is set, the system shall add every ticker with price data to the price adjustment working set.
- [x] **PRICE-ADJ-SCOPE-003**: When a ticker in the price adjustment working set has no matching `Asset`, the system shall write its adjusted prices equal to its raw prices and count it as skipped, so that a symbol with no SEC counterpart does not re-enter the working set on every run.
- [x] **PRICE-ADJ-SCOPE-004**: If both the ETF-only and equity-only options are set, then the system shall reject the request as invalid arguments rather than resolving the conflict.
- [x] **PRICE-ADJ-SCOPE-005**: Where the ETF-only option is set, the system shall process only tickers whose asset is a fund; where the equity-only option is set, only tickers whose asset is not a fund.

## Detection Scope

- [x] **PRICE-ADJ-SCOPE-006**: When resolving the automatic SEC detection scope, the system shall use the members of the configured detection index that also have price data.
- [x] **PRICE-ADJ-SCOPE-007**: If the configured detection index has no members, then the system shall use an empty detection scope, log the condition at error level, and run no automatic SEC detection that pass, rather than falling back to the full price universe.
- [x] **PRICE-ADJ-SCOPE-008**: Where the detection index code is configured blank, the system shall treat every ticker with price data as in scope.
- [x] **PRICE-ADJ-SCOPE-009**: The system shall gate every automatic SEC detection trigger on the ticker being within the detection scope, allowing only the force option to detect outside it.

## Rolling Re-Detection

- [x] **PRICE-ADJ-SCOPE-010**: When scheduling stale SEC re-detections, the system shall consider an in-scope ticker stale when its listing has no last-detection timestamp or that timestamp is more than 7 days old.
- [x] **PRICE-ADJ-SCOPE-011**: When scheduling stale SEC re-detections, the system shall order candidates by last-detection timestamp ascending with never-detected tickers first, and shall select at most the detection scope size divided by 7, rounded up.
- [x] **PRICE-ADJ-SCOPE-012**: The system shall schedule never-detected in-scope tickers through the same nightly cap as any other stale ticker, and shall not provide a separate trigger that bypasses that cap, so that the cap remains a bound on nightly SEC load.
- [x] **PRICE-ADJ-SCOPE-013**: When computing the nightly re-detection cap, the system shall derive it from the size of the detection scope rather than from the working set or the full price universe.
- [x] **PRICE-ADJ-SCOPE-014**: When an in-scope ticker's listing row is absent, the system shall not schedule it for re-detection.

## Jump-Triggered Detection

- [x] **PRICE-ADJ-SCOPE-015**: When an in-scope ticker is not already scheduled for re-detection, has rows needing adjustment, and shows an overnight raw close move exceeding 25% in absolute value among its most recent six price rows, the system shall run SEC detection for it immediately and count the trigger.
- [x] **PRICE-ADJ-SCOPE-016**: When evaluating the overnight-move trigger, the system shall examine the most recent six price rows rather than only the newest pair, so that a move buried inside a multi-day catch-up load is still detected.
- [x] **PRICE-ADJ-SCOPE-017**: When evaluating the overnight-move trigger, the system shall ignore row pairs where either close is missing or not positive.
- [x] **PRICE-ADJ-SCOPE-018**: The system shall apply the overnight-move trigger only to tickers that have rows needing adjustment, so that a historical large move does not re-trigger SEC detection on every run.

## Detection Stamping

- [x] **PRICE-ADJ-SCOPE-019**: When SEC detection completes for a ticker, the system shall stamp the listing's last-detection timestamp in the storage zone, which is what advances the rolling re-detection queue.
- [x] **PRICE-ADJ-SCOPE-020**: If equity SEC detection for a ticker reports a failure reason, then the system shall not stamp the listing's last-detection timestamp, so that the ticker is retried on the next pass rather than waiting out a full re-detection interval.
- [ ] **PRICE-ADJ-SCOPE-021**: If fund SEC detection for a ticker completed without examining any filing (every filing skipped), then the system shall not stamp the listing's last-detection timestamp, matching the equity behavior in PRICE-ADJ-SCOPE-020.

## Failure Isolation and Pacing

- [x] **PRICE-ADJ-SCOPE-022**: If detection or adjustment throws for one ticker during a full pass, then the system shall count the failure, leave that ticker's adjusted columns untouched, and continue with the remaining tickers.
- [x] **PRICE-ADJ-SCOPE-023**: If a full price adjustment pass is interrupted, then the system shall stop processing further tickers and re-assert the thread's interrupt flag.
- [x] **PRICE-ADJ-SCOPE-024**: When a SEC detection completes during a full pass, the system shall pause 100 milliseconds before continuing to the next ticker.
- [x] **PRICE-ADJ-SCOPE-025**: When a full price adjustment pass completes, the system shall report tickers processed, skipped for having no asset, failed, scheduled detections, jump-triggered detections, prices updated, snapped actions, and aggregated equity and fund detection diagnostics.

## Adjustment: Effective-Date Handling

- [x] **PRICE-ADJ-APPLY-001**: When a ticker has price rows and no corporate actions, the system shall write its adjusted prices equal to its raw prices.
- [x] **PRICE-ADJ-APPLY-002**: When a corporate action's effective date is not a trade date in the ticker's price series, the system shall apply it on the earliest trade date at or after that effective date, and shall count the action as snapped.
- [x] **PRICE-ADJ-APPLY-003**: When a corporate action's effective date is later than the ticker's newest price row, the system shall apply no adjustment for it.
- [x] **PRICE-ADJ-APPLY-004**: When a corporate action has no effective date, the system shall apply no adjustment for it.
- [x] **PRICE-ADJ-APPLY-005**: When several corporate actions apply on the same trade date, the system shall apply splits before dividends.

## Adjustment: Factor Arithmetic

- [x] **PRICE-ADJ-APPLY-006**: When adjusting a ticker's prices, the system shall walk its price rows from newest to oldest, writing each row's adjusted values as its raw values multiplied by the cumulative factor as it stands before that row's own actions are applied.
- [x] **PRICE-ADJ-APPLY-007**: When applying a split on its apply date, the system shall multiply the cumulative factor by the split's stored ratio, and shall ignore a split whose ratio is missing or not positive.
- [x] **PRICE-ADJ-APPLY-008**: When applying a dividend on its apply date, the system shall multiply the cumulative factor by one minus the dividend cash divided by the raw close of the previous trading day.
- [x] **PRICE-ADJ-APPLY-009**: When resolving the cash amount for a dividend adjustment factor, the system shall prefer the raw dividend amount, falling back to the adjusted dividend amount and then to the ratio field, because the factor divides by a raw historical close and the raw amount is the only one on that scale.
- [x] **PRICE-ADJ-APPLY-010**: If a dividend's prior trading close is missing or not positive, then the system shall apply no factor for that dividend.
- [x] **PRICE-ADJ-APPLY-011**: If a dividend's resolved cash amount is not positive, or is greater than or equal to the prior trading close, then the system shall apply no factor for that dividend.
- [x] **PRICE-ADJ-APPLY-012**: When accumulating adjustment factors, the system shall use decimal arithmetic at DECIMAL64 precision rather than binary floating point, so that a long action history does not compound representation drift.
- [x] **PRICE-ADJ-APPLY-013**: When writing an adjusted price value, the system shall round it to four decimal places.
- [x] **PRICE-ADJ-APPLY-014**: When a split and a dividend share an apply date, the system shall log a warning that the dividend factor may be mis-scaled.
- [ ] **PRICE-ADJ-APPLY-015**: When a split and a dividend share an apply date, the system shall compute the dividend factor on a share basis consistent with the prior close it divides by, so that the combined factor is not off by the split ratio.

## Adjustment: Write Policy

- [x] **PRICE-ADJ-APPLY-016**: When adjusting a ticker's prices, the system shall write a price row only when at least one of its adjusted values differs from what is already stored.
- [x] **PRICE-ADJ-APPLY-017**: When a ticker's adjustment fails, the system shall leave its null adjusted columns null, so that the ticker re-enters the working set on the next pass rather than having its raw prices frozen as adjusted.
- [x] **PRICE-ADJ-APPLY-018**: When adjusting a ticker's prices, the system shall persist the changed rows in a single batch per ticker.
- [ ] **PRICE-ADJ-APPLY-019**: When a ticker's corporate actions have changed since its adjusted prices were last computed, the system shall recompute that ticker's adjusted prices even when no price row is null, so that a deleted or re-dated action cannot leave stale adjusted values in place.

## yfinance Validation

- [x] **PRICE-ADJ-SCOPE-026**: Where the yfinance validation option is set, the system shall attach corporate-action and adjusted-price comparison reports to its output and shall persist nothing derived from them.
- [x] **PRICE-ADJ-SCOPE-027**: No scheduled run mode shall enable the yfinance validation option.

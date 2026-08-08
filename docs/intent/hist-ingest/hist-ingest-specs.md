# HIST Ingest Specs

EARS requirements for the IEX HIST raw price ingest. Design: [`hist-ingest-design.md`](hist-ingest-design.md).

Markers: `[x]` implemented, `[ ]` active gap, `[D]` deferred.

## Day Selection

- [x] **HIST-001**: The system shall treat the IEX HIST index (`https://iextrading.com/api/1.0/hist`) as authoritative for whether a date has a published capture.
- [x] **HIST-002**: When selecting candidate days for the IEX HIST load, the system shall include a date only when the date is a trading day by the local calendar and the date appears in the HIST index.
- [x] **HIST-003**: When selecting candidate days for the IEX HIST load, the system shall walk backwards from the previous day in market time (`America/New_York`), and shall examine at most three times the configured day count before stopping.
- [x] **HIST-004**: When a date appears in the HIST index with no entry whose feed is `TOPS`, the system shall count that date as not available and shall not treat it as an error.
- [x] **HIST-005**: Where a date has more than one `TOPS` entry in the HIST index, the system shall use the first such entry.
- [x] **HIST-006**: If the HIST index request returns a non-200 status or a body that cannot be parsed, then the system shall abort the IEX HIST load without ingesting any day.
- [x] **HIST-007**: The local trading-day calendar used for IEX HIST candidate selection shall exclude weekends, January 1, June 19, July 4, December 25, and the third Monday of January, the third Monday of February, the last Monday of May, the first Monday of September, and the fourth Thursday of November.

## Idempotency and Day Completeness

- [x] **HIST-008**: When a candidate date already has at least one `daily_prices` row, the system shall skip that date without downloading its capture.
- [x] **HIST-009**: When ingesting an IEX HIST trading day, the system shall write every symbol's row for that date in a single atomic batch, so that the existence of any row for the date implies the date is fully ingested (the condition HIST-008 relies on).
- [ ] **HIST-010**: Where an IEX HIST trading day's rows cannot be written in a single atomic batch, the system shall record day completeness in a marker that a partially written day cannot satisfy, rather than relying on row existence.

## Trade Aggregation

- [x] **HIST-011**: When aggregating an IEX HIST trading day, the system shall set each symbol's open price from the trade with the lowest message timestamp and its close price from the trade with the highest message timestamp.
- [x] **HIST-012**: Where two or more of a symbol's trades share the extreme timestamp on an IEX HIST trading day, the system shall use the first such trade encountered in the capture, so that a symbol whose trades all carry one timestamp has an open price equal to its close price.
- [x] **HIST-013**: When aggregating an IEX HIST trading day, the system shall set each symbol's high and low prices to the maximum and minimum trade prices, and its volume to the sum of trade sizes.
- [x] **HIST-014**: When a symbol has no Trade Report messages in an IEX HIST trading day's capture, the system shall write no `daily_prices` row for that symbol and date.
- [ ] **HIST-015**: If a Trade Report carries a non-positive price, then the system shall exclude it from that symbol's IEX HIST OHLCV aggregation.
- [x] **HIST-016**: The system shall store IEX HIST OHLCV as single-venue IEX prints, without consolidating them against any other venue.
- [x] **HIST-017**: When writing an IEX HIST trading day's rows, the system shall leave the adjusted price columns null, so that the price-adjustment segment's working-set selection (rows whose adjusted columns are null) identifies them.

## Download Pipeline

- [x] **HIST-018**: While IEX HIST days remain to ingest, the system shall download the next day's capture concurrently with parsing the current day's capture, and shall keep at most one download in flight.
- [x] **HIST-019**: When an IEX HIST day's ingest completes or fails, the system shall delete that day's downloaded temporary file.
- [x] **HIST-020**: If an IEX HIST day's download, parse, or write throws, then the system shall count the failure, start the following day's download, and continue with the remaining days.

## Job Semantics

- [x] **HIST-021**: Where `app.run-mode` is not `hist-load`, the system shall not create the HIST load runner.
- [x] **HIST-022**: When the HIST load runner has finished its work, the system shall terminate the JVM with the computed exit code after running shutdown hooks.
- [x] **HIST-023**: When the IEX HIST load finishes without throwing, and not every attempted day failed, the system shall run corporate-action price adjustment in the same process with force disabled.
- [x] **HIST-024**: When the IEX HIST load processed zero new days because every candidate was already stored, the system shall still run corporate-action price adjustment.
- [x] **HIST-025**: Where `app.hist-load.adjust-enabled` is false, the system shall skip the price-adjustment phase and report success.
- [x] **HIST-026**: Where `app.hist-load.equity-only` is true, the system shall restrict the price-adjustment phase to non-fund tickers.
- [x] **HIST-027**: When invoking price adjustment, the HIST load runner shall never enable yfinance validation.
- [x] **HIST-028**: If the IEX HIST load throws, or every attempted day failed, or the price-adjustment phase throws, then the system shall exit with code 1.
- [x] **HIST-029**: When some IEX HIST days failed but at least one was processed, or when individual tickers failed during the price-adjustment phase, the system shall exit with code 0.
- [x] **HIST-031**: When the IEX HIST load and the price-adjustment phase both complete without throwing, the system shall exit with code 0.
- [ ] **HIST-030**: If the configured IEX HIST day count is less than one, then the system shall fail rather than report success having examined no days.

# Equity Corporate-Action Accuracy: Price-Corroborated Splits + Amount-Anchored Dividend Ex-Dates

## Context

After many accuracy passes, equity corporate actions still miss the accuracy bar. The two dominant, structural error classes (per code review of the pipeline):

1. **Splits.** `EquitySplitDetector` infers splits from XBRL `EntityCommonStockSharesOutstanding` ratio snapping. Share counts are reported quarterly, so the effective date falls back to a share-fact date that can be weeks off; ratios not in the hardcoded set are missed; buybacks/issuance near a ratio cause false positives. One missed or misdated split makes whole stretches of adjusted history ~N times wrong.
2. **Dividend ex-dates.** `EquityExDateAssigner` guesses ex-dates with a DP optimizer (42-day offset / 91-day cadence priors) plus a synthetic `periodEnd+42` fallback, even though the 8-K press-release exhibits literally state amount, declaration, record, and payable dates. Candidates are extracted as bare dates, never anchored to a specific dividend amount, so the assigner must guess which date belongs to which quarterly event.

Fix: use two deterministic signals we already have but don't exploit:
- **Our own IEX raw closes** (commercially free, persistable, already in `daily_prices`): a split of price-multiplier `ratio` shows as overnight `prevClose/close ≈ 1/ratio` on the true ex-date. Use this to snap split dates, reject false positives, and catch missed splits. Verified conventions: `applyAdjustments` multiplies earlier prices by `ratio` (0.25 for a 4:1 forward), and its factor convention means "first trade date at the new price basis" is exactly the right `effectiveDate`, which is exactly what a price break identifies.
- **Amount-anchored 8-K tuples**: extract co-located (amount, record/payable/declared/ex date) tuples from the filing text already fetched by `CorporateActionFilingDateService` (zero extra SEC HTTP), and match them to XBRL quarterly events by amount. This replaces most DP guessing with stated dates.

No LLM in the detection path (nightly Fargate one-shot has no llama.cpp). ETF code (`EtfCorporateActionService`, `support/Etf*`) untouched. yfinance stays dev-only diagnostics.

**Dependency:** builds on the currently uncommitted branch work (`AdjustedPriceValidationService`, V2 migration, `PriceAdjustmentService` changes). That work should land first or together with this.

---

## Phase 0: Baseline + schema

### 0.1 Baseline (manual, dev, before code changes)
With Django running (yfinance proxy), for the split canary (AAPL, NVDA, TSLA, GOOGL, AMZN, SHOP) and the existing 20-ticker `CANARY_EQUITY_TICKERS`:
- `GET /admin/adjust-prices?ticker=<T>&force=true&validateWithYfinance=true`
- Record per ticker: `priceValidationReport.breaks` (count, steps, nearestAction), `maxAbsDeviation`, `validationReport` dateDrift/missing/amount counts, `equityDiagnostics` fallback-path counters. Keep as scratch notes; Phase 5 re-runs identical commands.

### 0.2 Flyway `V3__corporate_action_provenance.sql`
New: `springboot/src/main/resources/db/migration/V3__corporate_action_provenance.sql`
- Drop + recreate `corporate_actions_source_type_check` AND `corporate_actions_aud_source_type_check` (both exist in V1) adding `'SEC_PRICE_CORROBORATED'`.
- `ADD COLUMN ex_date_source varchar(24)` on both `corporate_actions` and `corporate_actions_aud`.

`model/CorporateAction.java`:
- Add `SEC_PRICE_CORROBORATED` to `SourceType`.
- Add `exDateSource` (String, length 24) + accessors. Values (string constants): dividends `TUPLE_MATCHED`, `DIRECT_EX_TEXT`, `RECORD_DP`, `SYNTHETIC`; splits `PRICE_BREAK`, `FILING_TEXT`, `SHARE_FACT`.

Provenance is categorical and drives the KPIs ("what fraction of dates are tuple-anchored"), so it gets its own column; `confidenceScore` stays numeric and is set alongside. H2 test schema comes from the entity, prod validated by `ddl-auto=validate`, so migration and entity must stay in lockstep.

---

## Phase 1: Price-implied split corroboration and dating

### 1.1 New: `corporateaction/support/SplitPriceCorroborator.java`
Plain support class (constructed with `new` in `EquityCorporateActionService`, matching existing pattern); sole dependency `DailyPriceRepository`. Deterministic, IEX-only, Fargate-safe.

API:
```java
PriceSeries load(String ticker)  // one findByTickerOrderByTradeDateAsc call
record Corroboration(LocalDate breakDate, double observedMultiplier, double logDistance)
Optional<Corroboration> corroborate(PriceSeries s, double multiplier, LocalDate windowStart, LocalDate windowEnd)
boolean windowFullyCovered(PriceSeries s, LocalDate windowStart, LocalDate windowEnd)
List<Corroboration> scanForSplitLikeBreaks(PriceSeries s, LocalDate minDate)
```

Algorithm (log space, symmetric for forward/reverse; multiplier `M = 1/ratio` = new/old shares):
- `corroborate`: trading day in window minimizing `|ln(prevClose/close) − ln(M)|`, accepted if distance ≤ tolerance: `ln(1.15)` for `M ≥ 2` or `M ≤ 0.5` (allows ±15% same-day market move); `ln(1.06)` for extended ratios 3:2, 4:3 (natural earnings gaps of −25%/−33% are common, so tight band; this is corroboration of an XBRL-asserted ratio, not sole-source detection).
- `scanForSplitLikeBreaks`: overnight ratios within `ln(1.10)` of any multiplier in `{2,3,4,5,7,10,20,50}` ∪ reciprocals, AND a persistence check: `median(close[t..t+4]) / median(close[t−5..t−1])` within `ln(1.25)` of `1/M`. Persistence kills one-day glitches and V-shaped crashes.

### 1.2 Modify `support/EquitySplitDetector.java`
Constructor gains the corroborator; `EquityCorporateActionService` gains a `DailyPriceRepository` param to build it. Per detected XBRL pair (prev, curr share facts):

1. **Date snapping**: search window `[prev.date − 10d, curr.date + 10d]`. Effective-date priority: **price break > filing-text candidate > share-fact date**; `exDateSource` = `PRICE_BREAK` / `FILING_TEXT` / `SHARE_FACT`, `confidenceScore` 100 / 70 / 40. If break and filing candidate disagree by > 5 trading days, WARN and keep the break.
2. **False-positive guard**: if `windowFullyCovered` and no corroboration → do not persist; increment new `priceRejected` diagnostic. If price history doesn't cover the window (pre-coverage splits), keep current behavior. EXTENDED ratios: filing match OR price break now counts as confirmation (today: filing match only).
3. **Reconcile, don't insert** (critical because of the `(ticker, action_type, effective_date, ratio)` unique constraint): before insert, look for an existing SPLIT with same ratio (±1%) within ±90 days; if found at a different date, update `effectiveDate`/`exDateSource`/`confidenceScore` in place. Otherwise re-dating creates a second row and the series double-adjusts. Add `findAllByTickerAndActionType` to `CorporateActionRepository` if absent.

### 1.3 Price-first detection of missed splits
After the XBRL pass, `scanForSplitLikeBreaks` over the full series; drop breaks explained by a persisted split within ±5 trading days. For each unexplained break with snapped multiplier M:
- `2 ≤ M < 5` (or reciprocal): persist only if a SEC filing split-date candidate (reuse the `fetchSplitEffectiveDates` list already fetched this run, no extra HTTP) lies within ±10 trading days. `sourceType = SEC_PRICE_CORROBORATED`, `exDateSource = PRICE_BREAK`, confidence 90.
- `M ≥ 5` or `M ≤ 0.2`: persist without filing candidate (a persistent overnight 5x move is effectively always a split), confidence 60.
- Otherwise: diagnostic counter `priceOnlyUnconfirmed` only.

This also makes the existing >25% overnight-jump re-detection hook in `PriceAdjustmentService` actually useful: today a jump-triggered re-run finds nothing until the next 10-Q updates share counts; with 1.3 it finds the split from the fresh price break immediately. No further `PriceAdjustmentService` changes beyond diagnostics rollup.

Extend `SplitDetectionStats` with `priceSnapped, priceCorroborated, priceRejected, priceOnlyDetected, priceOnlyUnconfirmed`; roll up in `summarizeEquityDiagnostics`.

---

## Phase 2: Amount-anchored 8-K tuples for dividend ex-dates

### 2.1 New: `corporateaction/support/DividendDeclarationTupleExtractor.java`
Pure text-in/records-out (no HTTP, unit-testable):
```java
record DividendDeclaration(Double amountPerShare, LocalDate declarationDate, LocalDate recordDate,
    LocalDate payableDate, LocalDate exDate, LocalDate filingDate, String accessionNumber, int confidenceScore)
List<DividendDeclaration> extract(String searchableText, LocalDate filingDate, String accessionNumber)
```
Find amount anchors (`$X.XX per share`, `dividend of $X.XX`); scan ±600 chars around each for labeled dates ("payable", "record date / holders of record", "declared", "ex-dividend"), reusing `CorporateActionFilingDateService`'s date grammar via a small shared helper. Handle table layouts ("Record Date: March 10, 2025"). Tuple requires amount + record date minimum; dedupe by (amount, recordDate) keeping highest score. Multiple tuples per document expected (regular + special).

### 2.2 Modify `CorporateActionFilingDateService.scanDividendRecordDates`
Run tuple extraction over the same already-fetched text (primary doc + EX-99 exhibits + full-submission fallback). Add `List<DividendDeclaration> declarations` to `RecordDateScanResult`; thread through `EquityCorporateActionService.detectDividends`.

### 2.3 Modify `support/EquityExDateAssigner.assignExDividendDates`
New first pass before the direct-ex pass:
- For each regular event, find the unused tuple whose amount matches `event.rawAmount()` (abs 0.0005 or rel 0.5%; also try split-adjusted variants using known splits) and whose recordDate ∈ `[fiscalPeriodEnd, fiscalPeriodEnd + 95d]`. Prefer closest-to-45-day offset, then higher tuple confidence.
- Ex-date = tuple's explicit exDate if stated, else `computeExDividendDate(recordDate)`. Provenance `TUPLE_MATCHED`; carry recordDate/payDate through.
- Unmatched events fall through: existing direct-ex pass → DP (`RECORD_DP`) → synthetic fallback.
- **Keep persisting the synthetic fallback**, tagged `SYNTHETIC`, confidence 10 (dropping it trades a 1-weighted date error for a 3-weighted missing error, and misdated-dividend adjustment error is far smaller than missing-event error).

### 2.4 Provenance through `DividendEvent` → upserter
Extend the `EquityCorporateActionService.DividendEvent` record with `LocalDate recordDate, LocalDate payDate, String exDateSource` (mechanical ripple: ~7 construction sites in `EquityExDateAssigner` and `EquityDividendNormalizer`, plus tests). `EquityDividendUpserter` sets `recordDate`, `payDate`, `exDateSource`, `confidenceScore` on insert and update (equity rows currently never populate record/pay dates: free win). Priority rule: a `SYNTHETIC` event must not overwrite the date of an existing row whose source is `TUPLE_MATCHED`/`DIRECT_EX_TEXT` (protects good dates when a filing fetch transiently fails on a weekly re-detect); a `TUPLE_MATCHED` event may move an existing row's date.

Extend `DividendDetectionStats` with `tupleMatchedAssignments, directExAssignments, dpAssignments, syntheticAssignments`; roll up in summary.

---

## Phase 3: Tests (existing patterns: plain JUnit for support classes, Mockito for services)

- New `SplitPriceCorroboratorTest`: 4:1 forward snap; 1:10 reverse; 3:2 inside/outside the 6% band; −33% crash fails persistence check; single-day glitch rejected; uncovered window → empty; two candidates in window picks closest.
- New `DividendDeclarationTupleExtractorTest`: canonical press-release paragraph; table layout; two dividends in one release; amount without record date → no tuple; numeric date formats.
- Update `EquitySplitDetectorTest`: price break overrides filing date; covered-window-no-break rejection; re-dating updates existing row (no second insert); price-only detection thresholds.
- Update `EquityExDateAssignerTest`: tuple-first wins over DP; split-adjusted amount match; fallback ordering + provenance strings.
- Update `EquityDividendUpserterTest`, `EquityCorporateActionServiceTest`, `PriceAdjustmentServiceTest` (new diagnostics), `CorporateActionValidationCanaryTest` (add `CANARY_SPLIT_TICKERS` constant).
- `cd springboot && mvn test`; JaCoCo floor must hold.

## Phase 4: Docs

- `docs/equity-corporate-action-process.md`: add `SplitPriceCorroborator` + `DividendDeclarationTupleExtractor` to the files table/diagram; document the effective-date convention ("first trade date at new price basis") and provenance values.
- `springboot/README.md`: new sourceType/`ex_date_source`, V3 migration, updated detection behavior.
- `docs/API_REFERENCE.md`: only if it enumerates `/admin/adjust-prices` diagnostic fields.

## Phase 5: End-to-end verification

1. `mvn test` clean; boot against dev Postgres → V3 applies, `ddl-auto=validate` passes.
2. Re-run Phase 0.1 commands identically. Success criteria:
   - Splits: zero `breaks` with |step| > 1% on the 6 split-canary tickers; every persisted split's nearest-action offset = 0 days; `maxAbsDeviation` ≤ 0.5% outside dividend-timing noise.
   - Dividends: synthetic-assignment share < 20% on the 20-ticker canary (from baseline); dateDrift ≤ baseline per ticker; weighted error (3·missing + 2·amount + 1·date) not worse anywhere.
   - ETF spot check on 2 to 3 fund tickers: unchanged behavior (no ETF code touched).
3. Fresh-split drill: delete a recent real split row in dev, `adjustTicker(force=true)` recreates it at the price-break date; confirm the jump-trigger path also recreates it without force.

## Execution order

0.1 baseline → 0.2 schema → Phase 1 (splits; independently shippable) → re-measure splits → Phase 2 (dividends) → tests/docs alongside each phase → Phase 5. Phases 1 and 2 are independent of each other.

## Open decisions / risks

1. **Sole-source threshold**: M ≥ 5 without filing confirmation is conservative; lowering to M ≥ 2 raises coverage but a genuine crash-and-stay-down could become a false split.
2. **Raw-close assumption**: corroboration assumes `daily_prices.close_price` is truly unadjusted IEX. Spot-check before enabling the false-positive guard (AAPL 2020-08-31 should show the 4:1 break); guard behind an easily flipped constant if uncertain.
3. **Re-dating prod rows**: on the next weekly re-detect, some existing split dates will move; adjusted series shift for the window between old and new dates (that is the fix, but it shows as a one-time diff).
4. **Tuple window 0–95d** may occasionally cross-match adjacent quarters for flat-dividend payers; amount + offset preference + one-use-per-tuple bounds this; watch canary dateDrift.
5. **Uncommitted branch work** (V2, `AdjustedPriceValidationService`, `PriceAdjustmentService` edits) must land first or together.

# Equity Corporate Action Load Process

The equity corporate action pipeline detects stock splits and dividends for a given ticker by parsing SEC XBRL financial facts and scanning SEC filing documents for key dates. It then persists `CorporateAction` records and applies price adjustments to historical daily prices.

## Files Involved

| # | File | Role |
|---|------|------|
| 1 | `controller/AdminController.java` | HTTP entry point: `GET /admin/adjust-prices?ticker=AAPL` |
| 2 | `corporateaction/PriceAdjustmentService.java` | Top-level orchestrator. Resolves Asset, decides equity vs ETF path, applies price adjustments after detection. |
| 3 | `corporateaction/EquityCorporateActionService.java` | Core equity orchestrator. Coordinates split detection, dividend detection, and returns an `EquityDetectionReport`. |
| 4 | `client/WebService.java` | SEC HTTP client. Fetches XBRL facts, submissions, and filing documents with rate limiting (250ms between requests) and retries (3 attempts, exponential backoff). |
| 5 | `corporateaction/EdgarFilingDiscoveryService.java` | Discovers filing metadata (accession numbers, form types, primary documents, filing dates) from SEC submissions JSON and archive shards. |
| 6 | `corporateaction/CorporateActionFilingDateService.java` | Scans filing text with regex patterns for record dates, ex-dividend dates, and split effective dates. Each match carries a confidence score based on pattern strength, document type, and position. |
| 7 | `corporateaction/support/EquitySplitDetector.java` | Detects splits by finding ratio changes in shares-outstanding XBRL data, then resolves effective dates via price breaks and SEC filings. Also runs price-first detection of splits the XBRL path missed. |
| 8 | `corporateaction/support/SplitPriceCorroborator.java` | Confirms and dates splits against the raw IEX close series in `daily_prices`: finds the overnight break matching the split multiplier, rejects candidates with no break in a fully covered window, and scans for unexplained split-like breaks. |
| 9 | `corporateaction/support/EquityDividendFactParser.java` | Parses raw dividend-per-share facts from SEC XBRL JSON (e.g., `CommonStockDividendsPerShareDeclared`). |
| 10 | `corporateaction/support/EquityDividendNormalizer.java` | Deduplicates and selects the best dividend fact per fiscal period. Classifies regular vs special dividends. Also handles split-adjusting historical dividend amounts. |
| 11 | `corporateaction/support/EquityExDateAssigner.java` | Assigns ex-dividend dates. Tries amount-anchored declaration tuples first, then direct ex-date matches, then dynamic programming over record-date candidates. |
| 12 | `corporateaction/support/DividendDeclarationTupleExtractor.java` | Extracts (amount, record date, payable date, declaration date, ex-date) tuples from 8-K / press-release text, anchored to the per-share dollar amount. |
| 13 | `corporateaction/support/FilingTextDates.java` | Shared date grammar (US date regex + parser) and HTML-to-text normalization used by the filing-date service and the tuple extractor. |
| 14 | `corporateaction/support/EquityDividendUpserter.java` | Upserts dividend `CorporateAction` rows. Matches existing records, updates changed fields, inserts new, prunes stale, and enforces ex-date provenance priority. |
| 15 | `model/CorporateAction.java` | JPA entity with fields: `ticker`, `actionType` (SPLIT/DIVIDEND), `effectiveDate`, `ratio`, `rawDividend`, `adjustedDividend`, `sourceType`, `recordDate`, `payDate`, `confidenceScore`, `exDateSource`, etc. |
| 16 | `repository/CorporateActionRepository.java` | Spring Data JPA interface. Provides find/exists queries by ticker, action type, and effective date. |

Note: The support classes are instantiated directly by `EquityCorporateActionService` in its constructor rather than being Spring-managed beans (the tuple extractor is owned by `CorporateActionFilingDateService`).

## Architecture Diagram

```
AdminController
  GET /admin/adjust-prices?ticker=AAPL
        |
        v
PriceAdjustmentService.adjustTicker()
  - Looks up Asset by ticker (gets CIK)
  - Checks isFund flag -> routes to equity or ETF path
        |
        v
EquityCorporateActionService.detectAndPersistWithDiagnostics(ticker, cik)
        |
        +---> WebService.fetchFinancials(cik)       [SEC XBRL JSON]
        |
        +---> EquitySplitDetector.detectSplits()     [Split detection]
        |         |
        |         +---> CorporateActionFilingDateService.fetchSplitEffectiveDates()
        |         |         |
        |         |         +---> EdgarFilingDiscoveryService.discoverFilings()
        |         |         +---> WebService.fetchFilingDocument()  (per filing)
        |         |
        |         +---> SplitPriceCorroborator.load()/corroborate()   [IEX raw closes]
        |         |         (snap effective date to price break; reject no-break candidates)
        |         +---> SplitPriceCorroborator.scanForSplitLikeBreaks()
        |         |         (price-first detection of missed splits)
        |         +---> CorporateActionRepository.save()  (reconcile-or-insert)
        |
        +---> detectDividends()                      [Dividend detection]
                  |
                  +---> EquityDividendFactParser.parseDividendFacts()
                  +---> EquityDividendNormalizer.normalizeDividendFacts()
                  +---> CorporateActionFilingDateService.scanDividendRecordDates()
                  |         |
                  |         +---> EdgarFilingDiscoveryService.discoverFilings()
                  |         +---> WebService.fetchFilingDocument()  (up to 250 filings)
                  |         +---> DividendDeclarationTupleExtractor.extract()  (same text)
                  |
                  +---> EquityExDateAssigner.assignExDividendDates()
                  |         (tuple match -> direct ex-date -> record-date DP -> synthetic)
                  +---> EquityDividendNormalizer.adjustDividendsForFutureSplits()
                  +---> EquityDividendUpserter.upsertDividendEvents()
```

## Happy Path: Loading AAPL

### Step 1: HTTP Request to PriceAdjustmentService

```
GET /admin/adjust-prices?ticker=AAPL
```

`AdminController` delegates to `PriceAdjustmentService.adjustTicker("AAPL", ...)`. The service looks up the `Asset` by ticker, finds CIK `320193`, sees `isFund=false`, and calls `EquityCorporateActionService.detectAndPersistWithDiagnostics("AAPL", 320193)`.

### Step 2: Fetch SEC XBRL Facts

`WebService.fetchFinancials(320193)` calls:

```
GET https://data.sec.gov/api/xbrl/companyfacts/CIK0000320193.json
```

Returns a large JSON blob with all XBRL-reported financial facts: shares outstanding, dividends per share declared/paid, and more. This single fetch provides data for both split and dividend detection.

### Step 3: Split Detection (EquitySplitDetector)

**Parse shares outstanding from XBRL:**
- Navigates `root.facts.dei.EntityCommonStockSharesOutstanding.units.shares`
- Filters to relevant SEC forms: 10-K, 10-Q, 8-K, 6-K, 20-F, 40-F
- Extracts valid `(date, shareCount)` pairs

**Detect ratio changes:**
- Compares consecutive share entries chronologically
- Calculates `newShares / oldShares`
- Snaps to known split ratios within 2% tolerance:
  - Primary (no filing proof needed): 2, 3, 4, 5, 7, 10, 20, 50 and their reverses
  - Extended (requires filing confirmation): 1.5, 4/3
- For AAPL, finds a ~7:1 jump (June 2014) and a ~4:1 jump (August 2020)

**Resolve effective dates (price break > filing text > share fact):**
- `SplitPriceCorroborator.corroborate()` searches the raw IEX close series inside the share-fact bracket (±10 days, widened to include a matched filing candidate) for the overnight `prevClose/close` move matching the split multiplier, in log space. Tolerance: ±15% for multipliers ≥2x (or ≤0.5x), ±6% for extended ratios (3:2, 4:3). A found break becomes the `effectiveDate` (`exDateSource = PRICE_BREAK`, confidence 100) — it is by construction the first trade date at the new price basis, which is exactly what `applyAdjustments` keys on.
- Otherwise `CorporateActionFilingDateService.fetchSplitEffectiveDates(cik)` candidates are used (`FILING_TEXT`, confidence 70), else the share-fact date (`SHARE_FACT`, confidence 40).
- **False-positive guard**: if the price series fully covers the search window and no matching break exists, the candidate is rejected (`priceRejected` diagnostic) — the share-count jump was a buyback/issuance artifact, not a split. Extended ratios are confirmed by a filing match OR a price break.

**Persist (reconcile-or-insert):**
- An existing SPLIT with the same ratio (±1%) within ±90 days is **re-dated in place** when the new resolution is at least as well grounded (confidence comparison) — inserting at the new date would leave both rows live and double-adjust the series.
- Otherwise saves a new `CorporateAction`:
  - `actionType = SPLIT`
  - `ratio = 1/snappedRatio` (e.g., 0.25 for a 4:1 split, 1/7 for a 7:1 split)
  - `sourceType = SEC_EQUITY_XBRL`
  - `effectiveDate`, `exDateSource`, `confidenceScore` from the resolution above

**Price-first detection of missed splits:**
- After the XBRL pass, `scanForSplitLikeBreaks()` finds overnight moves that snap (±10%) to a plausible multiplier ({2,3,4,5,7,10,20,50} and reciprocals) and whose surrounding 5-day median levels confirm the move persisted (kills one-day glitches and V-shaped crashes).
- Breaks already explained by a persisted split (±7 days) are skipped. Unexplained breaks persist as `sourceType = SEC_PRICE_CORROBORATED` when a SEC filing split-date candidate lies within ±14 days (confidence 90), or on price evidence alone for multipliers ≥5x / ≤0.2x (confidence 60). Mid-size breaks without filing support only increment the `priceOnlyUnconfirmed` diagnostic.
- This makes the nightly >25% overnight-jump re-detection hook effective on split day itself, instead of waiting weeks for the next 10-Q share count.

### Step 4: Dividend Detection - Parse Facts (EquityDividendFactParser)

Navigates `root.facts.us-gaap.CommonStockDividendsPerShareDeclared.units.USD/shares` (and related concepts like `CommonStockDividendsPerShareCashPaid`).

Extracts entries like:
```
{ startDate: 2024-03-30, endDate: 2024-06-29, value: 0.25, form: "10-Q", filed: "2024-08-02" }
```

Filters for USD per-share units, deduplicates, produces `List<DividendFact>`.

### Step 5: Dividend Detection - Normalize (EquityDividendNormalizer)

Groups facts by `endDate` (fiscal period end). For each group, selects the best fact by:
- Preferring quarter-length periods (80-120 days)
- Scoring by form priority (10-Q/10-K preferred)
- Preferring earliest filing date

Classifies each event as **regular** (quarterly cadence) or **special** (anomalous/one-time). Outputs one `DividendEvent` per fiscal period with `fiscalPeriodEnd` and `rawAmount`.

### Step 6: Dividend Detection - Scan Filing Text for Record Dates (CorporateActionFilingDateService)

`scanDividendRecordDates(320193)`:

1. **Discover filings**: `EdgarFilingDiscoveryService.discoverFilings(320193)` fetches `submissions/CIK0000320193.json` plus archive shards. Returns all filing metadata sorted by date.

2. **Filter and score by form type**: 8-K (score 140), DEF 14A (110), 10-Q (100), 10-K (95), 6-K/20-F/40-F (90). Selects up to 250 filings.

3. **Extract dates from filing text**: For each filing, fetches the primary document via `WebService.fetchFilingDocument()` and applies regex patterns:

   | Pattern | Score | Example Match |
   |---------|-------|---------------|
   | `RECORD_DATE_NEAR_DIVIDEND` | 130 | "dividend...record date...July 15, 2024" |
   | `SHAREHOLDER_OF_RECORD` | 120 | "shareholders of record...July 15, 2024" |
   | `HOLDERS_OF_RECORD` | 115 | "holders of record as of..." |
   | `RECORD_AT_CLOSE_OF_BUSINESS` | 110 | "record at close of business..." |
   | `RECORD_DATE_OF` | 95 | "record date of..." |
   | `RECORD_DATE_WILL_BE` | 90 | "record date will be..." |
   | `GENERIC_RECORD_DATE` | 70 | "record date..." |

   Also extracts direct ex-dividend date candidates (`EX_DIVIDEND_DATE_LINE`, `EX_DIVIDEND_TRADING_START`).

4. **Fallbacks**: If the primary document yields nothing, tries the full submission text and up to 6 exhibit documents (exhibit matches penalized by -5 points).

5. **Declaration tuples**: `DividendDeclarationTupleExtractor.extract()` runs over the same fetched text (primary + exhibits, full submission as fallback; exhibit re-fetches hit the ticker-scoped SEC cache so no extra HTTP). It finds per-share dollar amount anchors ("$0.24 per share", "dividend of $0.24", "Dividend per share: $0.24") and pairs each with the record / payable / declaration / ex-dividend dates labeled within ±600 characters. A tuple requires amount + record date; tuples dedupe by (amount, recordDate) keeping the highest-confidence extraction.

6. **Result**: `RecordDateScanResult` with deduplicated `RecordDateCandidate`, `ExDividendDateCandidate`, and `DividendDeclaration` lists, plus form discovery/selection/rejection statistics.

### Step 7: Dividend Detection - Assign Ex-Dates (EquityExDateAssigner)

Receives normalized events, record date candidates, direct ex-date candidates, declaration tuples, and known corporate actions (for split-restatement amount matching).

**Path 0 - Amount-anchored tuple match (preferred, `exDateSource = TUPLE_MATCHED`):**
- For each event (regular and special), find the unused tuple whose amount matches the event's raw XBRL amount — as-is, or after undoing future-split restatement (declared = raw / product of later split ratios) — and whose record date falls 0-95 days after the fiscal period end
- Prefer offsets near 45 days, then higher tuple confidence; each tuple is used at most once
- Ex-date = the tuple's explicit ex-date when stated, else computed from its record date; record and payable dates are carried onto the persisted row
- This replaces cadence guessing with the dates the company actually declared

**Path A - Direct ex-date match (`exDateSource = DIRECT_EX_TEXT`):**
- For each regular event, look for an `ExDividendDateCandidate` where `fiscalPeriodEnd < exDate <= fiscalPeriodEnd + 130 days`
- Score: `|gap - 45 days| - confidenceScore/25` (lower is better)
- Each candidate used at most once

**Path B - Record date assignment via dynamic programming (`exDateSource = RECORD_DP`):**
- For events not matched via Path A, run DP to find the globally optimal assignment of record dates to events
- State: `dp[i][j]` = minimum cost to assign first `i` events using candidates `0..j`
- Eligibility: record date 10-80 days after fiscal period end; filing date not before `fiscalPeriodEnd - 5 days`
- Cost: `|dayOffset - 42| + |cadenceGap - 91|/2 - confidenceScore/12`
- Skip penalty: 140 points
- Computes ex-dividend date from each assigned record date using T+1/T+2 settlement rules:
  - Before 2024-05-28: ex-date = previous business day before record date
  - On/after 2024-05-28: ex-date = next business day after record date

**Fallback (`exDateSource = SYNTHETIC`):**
- Any remaining unmatched events get an inferred record date: last matched record date + 91 days (quarterly cadence), or fiscal period end + 42 days
- Ex-date computed from the inferred record date; still persisted (a missing event is worse than a misdated one) but tagged low-confidence

**Special dividends** assigned separately with a simpler best-match approach against unused candidates.

### Step 8: Split-Adjust Dividends (EquityDividendNormalizer)

`adjustDividendsForFutureSplits(events, existingSplits)`: For each dividend, checks if any splits occurred after its effective date. Adjusts the per-share amount to a current-share basis.

Example: A $0.47/share dividend paid before AAPL's 7:1 split becomes $0.47 / 7 = ~$0.0671 adjusted. Both `rawDividend` (0.47) and `adjustedDividend` (0.0671) are preserved.

### Step 9: Upsert to Database (EquityDividendUpserter)

For each detected event:
- **Exact match** (same ticker + date + amount): skip, or update if fields changed
- **Year-scoped match** (same year, approximate amount): update date/amounts
- **No match**: insert new `CorporateAction`:
  - `actionType = DIVIDEND`
  - `effectiveDate = exDividendDate` (from Step 7)
  - `ratio = adjustedAmount`
  - `rawDividend`, `adjustedDividend`
  - `sourceType = SEC_EQUITY_XBRL`
  - `recordDate`, `payDate`, `exDateSource`, `confidenceScore` (95 tuple / 90 direct / 60 DP / 10 synthetic)

**Provenance priority**: a weaker-grounded re-detection may never move a stored date set by a stronger path (rank: `TUPLE_MATCHED` > `DIRECT_EX_TEXT` > `RECORD_DP` > `SYNTHETIC`). This protects tuple-anchored dates when a later run's filing fetch transiently fails; amounts still update.

Stale entries (in DB but not in detected list) are pruned.

### Step 10: Apply Price Adjustments (PriceAdjustmentService)

Back in the top-level orchestrator, `applyAdjustments()` walks all corporate actions for AAPL:
- **Splits**: Multiply all `DailyPrice` OHLCV values before the split effective date by the ratio (e.g., divide by 4 for a 4:1 split)
- **Dividends**: For each ex-dividend date, multiply the cumulative backward factor by \(1 - d / P_{\text{prior}}\), where \(P_{\text{prior}}\) is the **raw** prior trading day close (`close_price`) and \(d\) is the **raw** cash dividend per share (`rawDividend` on the `CorporateAction`). Using `adjustedDividend` here would be wrong because that amount is on a current-share basis after forward-split scaling, while \(P_{\text{prior}}\) is historical raw close. If `rawDividend` is missing, the code falls back to `adjustedDividend` or `ratio` (ETF and legacy rows).

Returns a summary map with diagnostics from both split and dividend detection.

## Key Design Decisions

- **Confidence scoring throughout**: Every extracted date carries a score based on regex pattern strength, document type, and position in text. Scores propagate through to final assignment decisions and are persisted on the `CorporateAction` entity.

- **DP optimization for record date matching**: Ensures globally optimal assignment across all quarterly dividends rather than greedy per-event matching. This prevents one bad greedy choice from cascading errors across subsequent quarters.

- **T+1 settlement awareness**: The ex-date computation accounts for the US market's switch from T+2 to T+1 settlement on May 28, 2024.

- **Split adjustment of historical dividends**: Raw amounts are preserved alongside adjusted amounts so the full history is auditable and can be recalculated if new splits are detected.

- **Amount-anchored declarations first**: Matching a declaration tuple by dollar amount ties each XBRL event to the exact 8-K that declared it, so its record/ex dates are read rather than inferred. Direct ex-date extraction and the DP record-date path remain as fallbacks when no tuple matches.

- **Price series as ground truth for splits**: The raw IEX closes already in `daily_prices` are commercially free and deterministic, so they can drive persistence (unlike yfinance, which stays diagnostics-only). A split's overnight break both dates it exactly and vetoes false positives; the persistence-median check keeps crashes and glitches out.

- **Effective-date convention**: A split's `effectiveDate` is the first trade date at the new price basis — the same date `applyAdjustments` assigns the pre-action factor to, and the date a raw-price break identifies. Keeping detection and adjustment on one convention drives the residual price-level error to zero at the break.

## Key Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| Max filings to scan | 250 | Dividend record date extraction |
| Max exhibit docs per filing | 6 | Fallback date extraction |
| Record date offset window | 10-80 days | From fiscal period end |
| Fiscal-to-ex gap window | 5-130 days | Direct ex-date matching |
| Quarter cadence | 91 days | Expected gap between regular dividends |
| Fallback skip penalty | 140 points | DP cost for skipping an event |
| T+1 cutoff date | 2024-05-28 | Settlement rule change |
| Ratio snap tolerance | 2% | Split ratio matching |
| SEC rate limit | 250ms | Minimum delay between SEC requests |
| Tuple record window | 0-95 days | Declaration record date after fiscal period end |
| Tuple amount tolerance | max(0.0005, 0.5%) | Declared vs XBRL amount match |
| Price-break tolerance | ±15% (≥2x), ±6% (extended) | Corroborating an XBRL split multiplier |
| Price-scan tolerance | ±10% + 5-day median persistence | Detecting unexplained split-like breaks |
| Sole-source multiplier | ≥5x (or ≤0.2x) | Price-only split persisted without filing proof |
| Reconcile window | ±90 days, ratio ±1% | Re-dating an existing split instead of inserting |

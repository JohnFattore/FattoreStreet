# Multi-signal stock split detection — XBRL share jumps, price-break corroboration, and SEC filing dates

_FattoreStreet @ [`03fb2129`](https://github.com/JohnFattore/FattoreStreet/tree/03fb2129577110ef4488e1577ff06e2a570126e1) — 2026-07-28_

_Source: [#149](https://github.com/JohnFattore/FattoreStreet/issues/149)_

## Overview

FattoreStreet detects equity stock splits (SPLIT `CorporateAction` rows) without ever consuming a vendor "splits" feed — everything is derived from SEC EDGAR company-facts XBRL and IEX daily closes, per `.claude/rules/data-licensing-commercial-free.md`. `EquitySplitDetector` (`springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/support/EquitySplitDetector.java`) is the interesting piece: it doesn't trust any single signal. It looks for jumps in the `EntityCommonStockSharesOutstanding` XBRL fact, tries to corroborate each jump against an actual overnight price break in the raw (unadjusted) IEX close series, and only falls back to an SEC filing's stated split-effective-date or the bare XBRL date when price evidence can't confirm it. It also runs the reverse scan — price breaks with *no* matching XBRL jump — and only persists those "price-only" splits when they're either huge (≥5x) or corroborated by a filing date, specifically so an ordinary volatile trading day never gets misread as a split. The whole thing is scored with a confidence value (`CONFIDENCE_PRICE_BREAK` down to `CONFIDENCE_SHARE_FACT`) and a reconciliation path that re-dates an existing row instead of inserting a duplicate, because `PriceAdjustmentService.applyAdjustments` downstream needs exactly one row per real split — an extra or wrongly-dated one silently corrupts every adjusted price after it.

## Files to read

- `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/support/EquitySplitDetector.java` — the whole file (514 lines); focus on:
  - Lines 26–57: the tuning constants — two ratio sets (`PRIMARY_SPLIT_RATIOS` vs `EXTENDED_SPLIT_RATIOS`) and five confidence tiers — read these before the logic, they explain *why* branches below exist
  - Lines 72–123: `detectSplits` — how `EntityCommonStockSharesOutstanding` facts become a sorted, deduplicated `SharesEntry` list, and the comment at lines 110–112 on why the filing-date fetch is gated behind `ratioPairs.isEmpty() && !hasUnexplainedBreak` (it's the single largest HTTP cost per ticker)
  - Lines 126–193: the main per-pair loop — trace one `SharesPair` through `resolveSplitEffectiveDate` → `splitPriceCorroborator.corroborate` → the three-way branch (corroborated / fully-covered-but-no-break → reject / uncovered → fall back to filing or XBRL date)
  - Lines 204–222: `collectRatioJumpPairs` — how a raw share-count ratio gets snapped to the nearest known split ratio via `nearestCommonSplitRatio`
  - Lines 224–277: `persistOrReconcile` — the dedup/re-date logic, and the Javadoc at 224–231 explaining why re-dating updates in place rather than inserting
  - Lines 304–350: `detectPriceOnlySplits` — the reverse scan, and the `SOLE_SOURCE_MIN_MULTIPLIER = 5.0` gate that lets huge breaks persist without any filing confirmation
- `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/support/SplitPriceCorroborator.java` — read the class Javadoc (lines 14–20: why comparisons are done in log space) and the tolerance constants (lines 24–34): `WIDE_TOLERANCE_LOG` vs `TIGHT_TOLERANCE_LOG` vs `PERSISTENCE_TOLERANCE_LOG` — three different bands for three different confidence situations
- `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/EquityCorporateActionService.java:64-86` — `detectAndPersistWithDiagnostics`, the entry point that wraps split + dividend detection in `webService.beginSecTickerScopedCache()`/`endSecTickerScopedCache()` and returns a diagnostics report instead of throwing on a single ticker's SEC-fetch failure
- `.claude/rules/data-licensing-commercial-free.md` — re-read with this file in mind: every signal `EquitySplitDetector` touches (XBRL shares, IEX closes, SEC filing text) is commercially free by construction; there is no yfinance fallback anywhere in this class

## Questions to answer while reading

1. Why does `collectRatioJumpPairs` snap a raw ratio like 1.98 to exactly 2.0 (`nearestCommonSplitRatio`) before ever consulting price or filing evidence, rather than carrying the raw ratio through and only snapping once price corroboration confirms it's real?
2. Walk through the three outcomes in the main loop (lines 155–186): corroborated, fully-covered-with-no-break (rejected), and not-fully-covered (fallback). Why does "not fully covered" get treated as weaker evidence rather than being rejected outright like the fully-covered case?
3. `EXTENDED_SPLIT_RATIOS` (1.5, 4/3) require SEC filing confirmation (line 176: `!isPrimarySplitRatio(...) && !splitDateResolution.matchedCandidate()` → skip) but `PRIMARY_SPLIT_RATIOS` don't. Given `RATIO_TOLERANCE = 0.02`, what real-world share-count noise (buybacks, ESPP issuance between XBRL filing dates) could produce a false 1.5-looking ratio, and why is that risk different for a clean 2.0 or 3.0 ratio?
4. In `detectPriceOnlySplits`, why is the confidence for a filing-confirmed price-only split (`CONFIDENCE_PRICE_ONLY_CONFIRMED = 90`) still lower than a share-fact-jump corroborated by price (`CONFIDENCE_PRICE_BREAK = 100`) — what does the highest-confidence path have that this one doesn't?
5. `findReconcilableSplit` (lines 279–297) only re-dates an existing row when the new confidence is `>=` the existing one (line 248). Trace a scenario where `EquitySplitDetector` runs twice for the same ticker on two different nights (e.g., IEX daily prices land a day late) and explain why the `>=` comparison, not `>`, is required for the second run to actually pick up a corroborating price break that arrived after the first.

## Primer: multi-signal event detection over noisy, delayed sources

This is a classic "sensor fusion" problem even though nothing here is a literal sensor: you have several independent, imperfect sources of evidence for the same underlying event (a stock split), each with a different failure mode. XBRL share counts are precise but sparse (only updated at filing cadence, and buybacks/ESPP issuance create false ratio jumps). Raw price series are dense (daily) but ambiguous (any big overnight move looks like a split candidate, including real volatility). SEC filing text is authoritative when found but expensive to fetch and doesn't cover every filer's wording. The general pattern — score each source's evidence for the same claim, combine via a confidence hierarchy, and only act on the highest-confidence combination available, falling back gracefully as signals drop out — shows up constantly in fraud detection, log deduplication, entity resolution, and here, financial corporate-action detection. The log-space comparison in `SplitPriceCorroborator` (comparing `ln(observed) - ln(snapped)` rather than raw ratios) is the standard trick for making forward splits (2x) and reverse splits (0.5x) symmetric under one tolerance band, since a naive percentage difference treats them very differently.

## External references

- SEC XBRL frames API docs (the shape of `EntityCommonStockSharesOutstanding` facts): https://www.sec.gov/search-filings/edgar-application-programming-interfaces
- "Sensor fusion" overview (the general pattern this detector implements, despite the finance-specific framing): https://en.wikipedia.org/wiki/Sensor_fusion

## Exercise (optional)

Pick a ticker you know has split in the last few years (e.g. a large-cap that did a well-publicized forward split). Query `CorporateActionRepository` (or hit the relevant admin/debug endpoint if one exists) for its persisted `SPLIT` rows and check `getExDateSource()`/`getConfidenceScore()` — was it detected via `EX_DATE_SOURCE_PRICE_BREAK` (confidence 100) or did it fall back to `EX_DATE_SOURCE_SHARE_FACT`/`EX_DATE_SOURCE_FILING_TEXT`? Then check whether `PriceAdjustmentService`'s adjusted prices around that date look correct in `daily_prices`.

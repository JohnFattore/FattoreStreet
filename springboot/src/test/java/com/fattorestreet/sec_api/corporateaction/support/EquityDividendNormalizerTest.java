package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;

import static org.junit.jupiter.api.Assertions.*;

class EquityDividendNormalizerTest {

    private final EquityDividendNormalizer normalizer = new EquityDividendNormalizer();

    // ---- normalizeDividendFacts ----

    @Test
    void normalizeDividendFacts_emptyInput_returnsEmpty() {
        assertTrue(normalizer.normalizeDividendFacts(List.of()).isEmpty());
    }

    @Test
    void normalizeDividendFacts_singleQuarterlyFact_returnsOneEvent() {
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), 0.22, "10-Q"));

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2024, 3, 31), out.get(0).fiscalPeriodEnd());
        assertEquals(0.22, out.get(0).rawAmount(), 1e-6);
        assertFalse(out.get(0).specialEvent());
    }

    @Test
    void normalizeDividendFacts_annualFactDerivesQ4_whenThreeQuartersKnown() {
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 0.88, "10-K"));

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertEquals(4, out.size());
        EquityCorporateActionService.DividendEvent q4 = out.stream()
                .filter(e -> e.fiscalPeriodEnd().equals(LocalDate.of(2024, 12, 31)))
                .findFirst().orElseThrow();
        assertEquals(0.22, q4.rawAmount(), 1e-4);
        assertFalse(q4.specialEvent());
    }

    @Test
    void normalizeDividendFacts_derivedQ4Rejected_whenTooLargeRelativeToMedian() {
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 5.00, "10-K")); // Q4 derived = 4.34 >> median 0.22

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        // Q4 should be rejected (4.34 > 0.22 * 2.5); only Q1-Q3
        assertEquals(3, out.size());
        assertTrue(out.stream().noneMatch(e -> e.fiscalPeriodEnd().equals(LocalDate.of(2024, 12, 31))));
    }

    @Test
    void normalizeDividendFacts_cumulativeAtFyEnd_replacedByDerivedQ4() {
        // A point-in-time fact at FY-end (no startDate) with value = annual cumulative total is
        // a common SEC filing pattern; it should be replaced by the derived true Q4.
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30), 0.22, "10-Q"),
                factNoStart(LocalDate.of(2024, 12, 31), 0.88, "10-Q"),            // cumulative at year-end
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 0.88, "10-K")); // annual

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertEquals(4, out.size());
        EquityCorporateActionService.DividendEvent q4 = out.stream()
                .filter(e -> e.fiscalPeriodEnd().equals(LocalDate.of(2024, 12, 31)))
                .findFirst().orElseThrow();
        // Derived Q4 = 0.88 - 0.66 = 0.22; cumulative 0.88 looks cumulative → replaced
        assertEquals(0.22, q4.rawAmount(), 1e-4);
    }

    @Test
    void normalizeDividendFacts_8kSpecialDividend_detectedAlongsideRegular() {
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), 0.22, "10-Q"),
                factNoStart(LocalDate.of(2024, 3, 31), 2.50, "8-K")); // large 8-K amount = special

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(e -> e.specialEvent() && e.rawAmount() == 2.50));
        assertTrue(out.stream().anyMatch(e -> !e.specialEvent() && e.rawAmount() == 0.22));
    }

    @Test
    void normalizeDividendFacts_prefersTenQOverTenK_forSameEndDate() {
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30), 0.22, "10-Q"),
                fact(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 9, 30), 0.25, "10-K")); // 10-K should lose

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertEquals(1, out.size());
        assertEquals(0.22, out.get(0).rawAmount(), 1e-6);
    }

    @Test
    void normalizeDividendFacts_intermediatePeriodFact_excluded() {
        // A 180-day (6-month) period fact is neither quarterly (<=120) nor annual (>=250).
        // It should be silently excluded, not promoted as a regular dividend.
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 6, 30); // 181 days — intermediate
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(start, end, 1.54, "10-Q"));

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertTrue(out.isEmpty(), "intermediate-period (181-day) fact should be excluded");
    }

    @Test
    void normalizeDividendFacts_nullStartDate8kMatchingIntermediateAmount_excluded() {
        // An 8-K with no startDate and the same amount as a filtered intermediate-period fact
        // should be excluded too — it's typically a secondary announcement of the same cumulative.
        LocalDate end = LocalDate.of(2020, 6, 30);
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2020, 1, 1), end, 1.54, "10-Q"),    // 181 days = intermediate, filtered
                factNoStart(end, 1.54, "8-K"));                         // null startDate, same amount → also excluded

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertTrue(out.isEmpty(), "null-startDate 8-K matching intermediate amount should be excluded");
    }

    @Test
    void normalizeDividendFacts_nullStartDate8kWithDifferentAmount_kept() {
        // Null-startDate 8-K with a different amount from any intermediate fact is kept normally.
        LocalDate end = LocalDate.of(2020, 6, 30);
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2020, 1, 1), end, 1.54, "10-Q"),  // intermediate, filtered
                factNoStart(end, 0.205, "8-K"));                      // different amount → kept

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertEquals(1, out.size());
        assertEquals(0.205, out.get(0).rawAmount(), 1e-6);
    }

    @Test
    void normalizeDividendFacts_intermediatePeriodFact_doesNotMaskQuarterlyFacts() {
        // Even when an intermediate fact exists alongside quarterly facts, the intermediate
        // should be excluded and not surface as a phantom extra event.
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate endInter = LocalDate.of(2020, 6, 29); // 180 days — intermediate
        List<EquityCorporateActionService.DividendFact> facts = List.of(
                fact(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 31), 0.205, "10-Q"),
                fact(start, endInter, 1.54, "10-Q")); // intermediate — must not become an event

        List<EquityCorporateActionService.DividendEvent> out = normalizer.normalizeDividendFacts(facts);

        assertEquals(1, out.size());
        assertEquals(0.205, out.get(0).rawAmount(), 1e-6);
    }

    @Test
    void adjustDividendsForFutureSplits_usesExDividendDateForSplitOrdering() {
        LocalDate fiscal = LocalDate.of(2020, 3, 31);
        LocalDate ex = LocalDate.of(2020, 5, 11);
        CorporateAction split = split("A", LocalDate.of(2020, 8, 31), 0.25);

        List<EquityCorporateActionService.DividendEvent> events = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, ex, 0.82, 0.82, 2020, false));

        List<EquityCorporateActionService.DividendEvent> out = normalizer.adjustDividendsForFutureSplits(events, List.of(split));

        assertEquals(1, out.size());
        assertEquals(fiscal, out.get(0).fiscalPeriodEnd());
        assertEquals(ex, out.get(0).exDividendDate());
        assertEquals(0.205, out.get(0).adjustedAmount(), 1e-4);
    }

    @Test
    void adjustDividendsForFutureSplits_skipsSplitWhenExDateOnOrAfterSplit() {
        LocalDate fiscal = LocalDate.of(2020, 6, 30);
        LocalDate ex = LocalDate.of(2020, 9, 1);
        CorporateAction split = split("A", LocalDate.of(2020, 8, 31), 0.25);

        List<EquityCorporateActionService.DividendEvent> events = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, ex, 0.82, 0.82, 2020, false));

        List<EquityCorporateActionService.DividendEvent> out = normalizer.adjustDividendsForFutureSplits(events, List.of(split));

        assertEquals(0.82, out.get(0).adjustedAmount(), 1e-6);
    }

    @Test
    void adjustDividendsForFutureSplits_unRestatesRawForAlreadyAdjustedPreSplitEvents() {
        // Pre-split events whose reported amounts already sit at the post-split scale (issuer
        // restated the facts) keep their adjusted amount, but raw must be scaled back up so the
        // dividend/price factor divides a pre-split cash amount by a pre-split raw close.
        CorporateAction split = split("A", LocalDate.of(2020, 8, 31), 0.25);
        List<EquityCorporateActionService.DividendEvent> events = List.of(
                // Pre-split events already restated to post-split scale (0.205 ≈ post-split 0.82/4).
                new EquityCorporateActionService.DividendEvent(
                        LocalDate.of(2020, 3, 31), LocalDate.of(2020, 5, 11), 0.205, 0.205, 2020, false),
                new EquityCorporateActionService.DividendEvent(
                        LocalDate.of(2020, 6, 30), LocalDate.of(2020, 8, 10), 0.205, 0.205, 2020, false),
                // Post-split event at the same scale anchors the "already adjusted" inference.
                new EquityCorporateActionService.DividendEvent(
                        LocalDate.of(2020, 9, 30), LocalDate.of(2020, 11, 6), 0.205, 0.205, 2020, false));

        List<EquityCorporateActionService.DividendEvent> out = normalizer.adjustDividendsForFutureSplits(events, List.of(split));

        assertEquals(3, out.size());
        // Pre-split events: adjusted stays post-split scale, raw is un-restated to price scale.
        assertEquals(0.205, out.get(0).adjustedAmount(), 1e-6);
        assertEquals(0.82, out.get(0).rawAmount(), 1e-6);
        assertEquals(0.205, out.get(1).adjustedAmount(), 1e-6);
        assertEquals(0.82, out.get(1).rawAmount(), 1e-6);
        // Post-split event untouched.
        assertEquals(0.205, out.get(2).rawAmount(), 1e-6);
        assertEquals(0.205, out.get(2).adjustedAmount(), 1e-6);
    }

    private static EquityCorporateActionService.DividendFact fact(LocalDate start, LocalDate end, double value, String form) {
        return new EquityCorporateActionService.DividendFact(start, end, value, form, end.plusDays(30), "CommonStockDividendsPerShareDeclared");
    }

    private static EquityCorporateActionService.DividendFact factNoStart(LocalDate end, double value, String form) {
        return new EquityCorporateActionService.DividendFact(null, end, value, form, end.plusDays(30), "CommonStockDividendsPerShareDeclared");
    }

    private static CorporateAction split(String ticker, LocalDate effective, double ratioOldPerNew) {
        CorporateAction a = new CorporateAction();
        a.setTicker(ticker);
        a.setActionType(ActionType.SPLIT);
        a.setEffectiveDate(effective);
        a.setRatio(ratioOldPerNew);
        a.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
        return a;
    }
}

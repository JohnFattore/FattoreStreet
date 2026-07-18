package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquityExDateAssignerTest {

    @Mock
    private CorporateActionFilingDateService corporateActionFilingDateService;

    private EquityExDateAssigner assigner() {
        return new EquityExDateAssigner(corporateActionFilingDateService);
    }

    private static DividendDeclarationTupleExtractor.DividendDeclaration tuple(
            double amount, LocalDate recordDate, LocalDate payDate, LocalDate exDate) {
        return new DividendDeclarationTupleExtractor.DividendDeclaration(
                amount, null, recordDate, payDate, exDate,
                LocalDate.of(2021, 4, 1), "0000000000-21-000042", 85);
    }

    private EquityCorporateActionService.AssignmentResult assign(
            List<EquityCorporateActionService.DividendEvent> normalized,
            List<CorporateActionFilingDateService.RecordDateCandidate> records,
            List<CorporateActionFilingDateService.ExDividendDateCandidate> exDirect) {
        return assigner().assignExDividendDates(normalized, records, exDirect, List.of(), List.of());
    }

    @Test
    void assignExDividendDates_prefersDirectExFromFilingWhenPlausible() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate directEx = LocalDate.of(2021, 5, 10);
        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));

        List<CorporateActionFilingDateService.ExDividendDateCandidate> exDirect = List.of(
                new CorporateActionFilingDateService.ExDividendDateCandidate(
                        directEx, LocalDate.of(2021, 4, 1), "0000000000-21-000001", 130));

        List<CorporateActionFilingDateService.RecordDateCandidate> records = List.of();

        EquityCorporateActionService.AssignmentResult result = assign(normalized, records, exDirect);

        assertEquals(1, result.events().size());
        assertEquals(fiscal, result.events().get(0).fiscalPeriodEnd());
        assertEquals(directEx, result.events().get(0).exDividendDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_DIRECT_EX_TEXT, result.events().get(0).exDateSource());
        assertEquals(1, result.recordBasedAssignments());
        assertEquals(1, result.directExAssignments());
    }

    @Test
    void assignExDividendDates_recordDatePath_assignsExDate() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate recordDate = LocalDate.of(2021, 5, 10); // 40 days after fiscal end
        LocalDate expectedEx = LocalDate.of(2021, 5, 7);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));
        List<CorporateActionFilingDateService.RecordDateCandidate> records = List.of(
                new CorporateActionFilingDateService.RecordDateCandidate(recordDate, LocalDate.of(2021, 4, 1), "0000000000-21-000099", 120));
        List<CorporateActionFilingDateService.ExDividendDateCandidate> exDirect = List.of();

        when(corporateActionFilingDateService.computeExDividendDate(recordDate)).thenReturn(expectedEx);

        EquityCorporateActionService.AssignmentResult result = assign(normalized, records, exDirect);

        assertEquals(1, result.events().size());
        assertEquals(expectedEx, result.events().get(0).exDividendDate());
        assertEquals(recordDate, result.events().get(0).recordDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_RECORD_DP, result.events().get(0).exDateSource());
        assertEquals(1, result.recordBasedAssignments());
        assertEquals(1, result.dpAssignments());
        assertEquals(0, result.fallbackAssignments());
    }

    @Test
    void assignExDividendDates_noCandidate_usesFallback() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate fallbackEx = LocalDate.of(2021, 5, 12);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));

        when(corporateActionFilingDateService.computeExDividendDate(any())).thenReturn(fallbackEx);

        EquityCorporateActionService.AssignmentResult result =
                assign(normalized, List.of(), List.of());

        assertEquals(1, result.events().size());
        assertNotNull(result.events().get(0).exDividendDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_SYNTHETIC, result.events().get(0).exDateSource());
        assertEquals(0, result.recordBasedAssignments());
        assertEquals(1, result.fallbackAssignments());
        assertEquals(1, result.syntheticAssignments());
    }

    @Test
    void assignExDividendDates_specialDividend_mappedSeparately() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate recordDate = LocalDate.of(2021, 5, 10);
        LocalDate fallbackEx = LocalDate.of(2021, 5, 12);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false),
                new EquityCorporateActionService.DividendEvent(fiscal, null, 3.00, 3.00, 2021, true));
        List<CorporateActionFilingDateService.RecordDateCandidate> records = List.of(
                new CorporateActionFilingDateService.RecordDateCandidate(recordDate, LocalDate.of(2021, 4, 1), "0000000000-21-000099", 120));

        when(corporateActionFilingDateService.computeExDividendDate(any())).thenReturn(fallbackEx);

        EquityCorporateActionService.AssignmentResult result =
                assign(normalized, records, List.of());

        assertEquals(2, result.events().size());
        assertTrue(result.events().stream().anyMatch(e -> e.rawAmount() == 3.00 && e.specialEvent()));
        assertTrue(result.events().stream().anyMatch(e -> e.rawAmount() == 0.22 && !e.specialEvent()));
    }

    @Test
    void tupleMatchWinsOverDpAndCarriesProvenance() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate tupleRecord = LocalDate.of(2021, 5, 10);
        LocalDate tuplePay = LocalDate.of(2021, 5, 27);
        LocalDate expectedEx = LocalDate.of(2021, 5, 7);
        // A competing DP record candidate at a different date would otherwise win.
        List<CorporateActionFilingDateService.RecordDateCandidate> records = List.of(
                new CorporateActionFilingDateService.RecordDateCandidate(
                        LocalDate.of(2021, 5, 24), LocalDate.of(2021, 4, 1), "0000000000-21-000099", 120));

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));

        when(corporateActionFilingDateService.computeExDividendDate(tupleRecord)).thenReturn(expectedEx);

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(
                normalized, records, List.of(),
                List.of(tuple(0.22, tupleRecord, tuplePay, null)), List.of());

        assertEquals(1, result.events().size());
        EquityCorporateActionService.DividendEvent event = result.events().get(0);
        assertEquals(expectedEx, event.exDividendDate());
        assertEquals(tupleRecord, event.recordDate());
        assertEquals(tuplePay, event.payDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED, event.exDateSource());
        assertEquals(1, result.tupleMatchedAssignments());
        assertEquals(0, result.dpAssignments());
    }

    @Test
    void tupleWithExplicitExDateUsesItDirectly() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate tupleRecord = LocalDate.of(2021, 5, 10);
        LocalDate statedEx = LocalDate.of(2021, 5, 7);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(
                normalized, List.of(), List.of(),
                List.of(tuple(0.22, tupleRecord, null, statedEx)), List.of());

        assertEquals(statedEx, result.events().get(0).exDividendDate());
        assertEquals(1, result.tupleMatchedAssignments());
    }

    @Test
    void tupleAmountMismatchFallsThrough() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate fallbackEx = LocalDate.of(2021, 5, 12);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));

        when(corporateActionFilingDateService.computeExDividendDate(any())).thenReturn(fallbackEx);

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(
                normalized, List.of(), List.of(),
                List.of(tuple(0.50, LocalDate.of(2021, 5, 10), null, null)), List.of());

        assertEquals(0, result.tupleMatchedAssignments());
        assertEquals(1, result.syntheticAssignments());
    }

    @Test
    void tupleMatchesSplitRestatedAmount() {
        // XBRL restated the 2019 dividend to $0.205 after a 4:1 split; the 8-K declared $0.82.
        LocalDate fiscal = LocalDate.of(2019, 3, 30);
        LocalDate tupleRecord = LocalDate.of(2019, 5, 13);
        LocalDate expectedEx = LocalDate.of(2019, 5, 10);
        CorporateAction split = new CorporateAction();
        split.setActionType(CorporateAction.ActionType.SPLIT);
        split.setEffectiveDate(LocalDate.of(2020, 8, 31));
        split.setRatio(0.25);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.205, 0.205, 2019, false));

        when(corporateActionFilingDateService.computeExDividendDate(tupleRecord)).thenReturn(expectedEx);

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(
                normalized, List.of(), List.of(),
                List.of(tuple(0.82, tupleRecord, null, null)), List.of(split));

        assertEquals(expectedEx, result.events().get(0).exDividendDate());
        assertEquals(1, result.tupleMatchedAssignments());
    }

    @Test
    void specialDividendMatchesTupleByAmount() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate tupleRecord = LocalDate.of(2021, 5, 10);
        LocalDate expectedEx = LocalDate.of(2021, 5, 7);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 3.00, 3.00, 2021, true));

        when(corporateActionFilingDateService.computeExDividendDate(tupleRecord)).thenReturn(expectedEx);

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(
                normalized, List.of(), List.of(),
                List.of(tuple(3.00, tupleRecord, null, null)), List.of());

        assertEquals(1, result.events().size());
        assertTrue(result.events().get(0).specialEvent());
        assertEquals(expectedEx, result.events().get(0).exDividendDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED, result.events().get(0).exDateSource());
    }

    @Test
    void eachTupleAssignedOnlyOnce() {
        LocalDate q1 = LocalDate.of(2021, 3, 31);
        LocalDate q2 = LocalDate.of(2021, 6, 30);
        LocalDate tupleRecord = LocalDate.of(2021, 5, 10);
        LocalDate fallbackEx = LocalDate.of(2021, 8, 12);

        // Flat dividend: both quarters have the same amount; only Q1 should claim the tuple.
        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(q1, null, 0.22, 0.22, 2021, false),
                new EquityCorporateActionService.DividendEvent(q2, null, 0.22, 0.22, 2021, false));

        when(corporateActionFilingDateService.computeExDividendDate(any())).thenReturn(fallbackEx);

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(
                normalized, List.of(), List.of(),
                List.of(tuple(0.22, tupleRecord, null, null)), List.of());

        assertEquals(1, result.tupleMatchedAssignments());
        assertEquals(1, result.syntheticAssignments());
    }
}

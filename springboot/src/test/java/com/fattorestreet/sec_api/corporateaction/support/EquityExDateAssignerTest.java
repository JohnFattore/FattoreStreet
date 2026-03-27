package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.DividendRecordDateService;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
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
    private DividendRecordDateService dividendRecordDateService;

    private EquityExDateAssigner assigner() {
        return new EquityExDateAssigner(dividendRecordDateService);
    }

    @Test
    void assignExDividendDates_prefersDirectExFromFilingWhenPlausible() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate directEx = LocalDate.of(2021, 5, 10);
        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));

        List<DividendRecordDateService.ExDividendDateCandidate> exDirect = List.of(
                new DividendRecordDateService.ExDividendDateCandidate(
                        directEx, LocalDate.of(2021, 4, 1), "0000000000-21-000001", 130));

        List<DividendRecordDateService.RecordDateCandidate> records = List.of();

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(normalized, records, exDirect);

        assertEquals(1, result.events().size());
        assertEquals(fiscal, result.events().get(0).fiscalPeriodEnd());
        assertEquals(directEx, result.events().get(0).exDividendDate());
        assertEquals(1, result.recordBasedAssignments());
    }

    @Test
    void assignExDividendDates_recordDatePath_assignsExDate() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate recordDate = LocalDate.of(2021, 5, 10); // 40 days after fiscal end
        LocalDate expectedEx = LocalDate.of(2021, 5, 7);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));
        List<DividendRecordDateService.RecordDateCandidate> records = List.of(
                new DividendRecordDateService.RecordDateCandidate(recordDate, LocalDate.of(2021, 4, 1), "0000000000-21-000099", 120));
        List<DividendRecordDateService.ExDividendDateCandidate> exDirect = List.of();

        when(dividendRecordDateService.computeExDividendDate(recordDate)).thenReturn(expectedEx);

        EquityCorporateActionService.AssignmentResult result = assigner().assignExDividendDates(normalized, records, exDirect);

        assertEquals(1, result.events().size());
        assertEquals(expectedEx, result.events().get(0).exDividendDate());
        assertEquals(1, result.recordBasedAssignments());
        assertEquals(0, result.fallbackAssignments());
    }

    @Test
    void assignExDividendDates_noCandidate_usesFallback() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate fallbackEx = LocalDate.of(2021, 5, 12);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false));

        when(dividendRecordDateService.computeExDividendDate(any())).thenReturn(fallbackEx);

        EquityCorporateActionService.AssignmentResult result =
                assigner().assignExDividendDates(normalized, List.of(), List.of());

        assertEquals(1, result.events().size());
        assertNotNull(result.events().get(0).exDividendDate());
        assertEquals(0, result.recordBasedAssignments());
        assertEquals(1, result.fallbackAssignments());
    }

    @Test
    void assignExDividendDates_specialDividend_mappedSeparately() {
        LocalDate fiscal = LocalDate.of(2021, 3, 31);
        LocalDate recordDate = LocalDate.of(2021, 5, 10);
        LocalDate fallbackEx = LocalDate.of(2021, 5, 12);

        List<EquityCorporateActionService.DividendEvent> normalized = List.of(
                new EquityCorporateActionService.DividendEvent(fiscal, null, 0.22, 0.22, 2021, false),
                new EquityCorporateActionService.DividendEvent(fiscal, null, 3.00, 3.00, 2021, true));
        List<DividendRecordDateService.RecordDateCandidate> records = List.of(
                new DividendRecordDateService.RecordDateCandidate(recordDate, LocalDate.of(2021, 4, 1), "0000000000-21-000099", 120));

        when(dividendRecordDateService.computeExDividendDate(any())).thenReturn(fallbackEx);

        EquityCorporateActionService.AssignmentResult result =
                assigner().assignExDividendDates(normalized, records, List.of());

        assertEquals(2, result.events().size());
        assertTrue(result.events().stream().anyMatch(e -> e.rawAmount() == 3.00 && e.specialEvent()));
        assertTrue(result.events().stream().anyMatch(e -> e.rawAmount() == 0.22 && !e.specialEvent()));
    }
}

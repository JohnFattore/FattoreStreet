package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquityDividendUpserterTest {

    @Mock
    private CorporateActionRepository corporateActionRepository;

    @InjectMocks
    private EquityDividendUpserter upserter;

    private CorporateAction existingDividend(String ticker, LocalDate date, double ratio, double raw) {
        CorporateAction a = new CorporateAction();
        a.setTicker(ticker);
        a.setActionType(ActionType.DIVIDEND);
        a.setEffectiveDate(date);
        a.setRatio(ratio);
        a.setRawDividend(raw);
        a.setAdjustedDividend(ratio);
        a.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
        return a;
    }

    private EquityCorporateActionService.DividendEvent event(LocalDate exDate, double raw, double adjusted, int year) {
        return new EquityCorporateActionService.DividendEvent(
                exDate.minusDays(40), exDate, raw, adjusted, year, false);
    }

    // @spec EQUITY-DIV-030
    @Test
    void upsertDividendEvents_newEvent_isInserted() {
        LocalDate exDate = LocalDate.of(2024, 3, 14);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of());
        when(corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate("AAPL", ActionType.DIVIDEND, exDate))
                .thenReturn(List.of());
        CorporateAction saved = existingDividend("AAPL", exDate, 0.22, 0.22);
        when(corporateActionRepository.save(any())).thenReturn(saved);

        EquityCorporateActionService.UpsertStats stats =
                upserter.upsertDividendEvents("AAPL", List.of(event(exDate, 0.22, 0.22, 2024)));

        assertEquals(1, stats.changed());
        assertEquals(1, stats.inserted());
        assertEquals(0, stats.updated());
        verify(corporateActionRepository).save(argThat(a ->
                a.getEffectiveDate().equals(exDate) && Math.abs(a.getRatio() - 0.22) < 1e-9));
    }

    // @spec EQUITY-DIV-026
    @Test
    void upsertDividendEvents_exactDuplicate_skipped() {
        LocalDate exDate = LocalDate.of(2024, 3, 14);
        CorporateAction existing = existingDividend("AAPL", exDate, 0.22, 0.22);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(existing));

        EquityCorporateActionService.UpsertStats stats =
                upserter.upsertDividendEvents("AAPL", List.of(event(exDate, 0.22, 0.22, 2024)));

        assertEquals(0, stats.changed());
        assertEquals(0, stats.inserted());
        verify(corporateActionRepository, never()).save(any());
    }

    // @spec EQUITY-DIV-026
    @Test
    void upsertDividendEvents_exactMatchDateChanged_isUpdated() {
        LocalDate oldDate = LocalDate.of(2024, 1, 15);
        LocalDate newDate = LocalDate.of(2024, 1, 17);
        CorporateAction existing = existingDividend("AAPL", oldDate, 0.22, 0.22);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(existing));
        when(corporateActionRepository.save(existing)).thenReturn(existing);

        EquityCorporateActionService.UpsertStats stats =
                upserter.upsertDividendEvents("AAPL", List.of(event(newDate, 0.22, 0.22, 2024)));

        assertEquals(1, stats.changed());
        assertEquals(1, stats.updated());
        assertEquals(0, stats.inserted());
        verify(corporateActionRepository).save(existing);
        assertEquals(newDate, existing.getEffectiveDate());
    }

    // @spec EQUITY-DIV-026
    @Test
    void upsertDividendEvents_yearScopedFuzzyMatch_updatesExisting() {
        // Record date differs slightly (same year, 3 days, same amount) → year-scoped match → update
        LocalDate oldDate = LocalDate.of(2024, 6, 12);
        LocalDate newDate = LocalDate.of(2024, 6, 15);
        CorporateAction existing = existingDividend("MSFT", oldDate, 0.75, 0.75);
        when(corporateActionRepository.findByTicker("MSFT")).thenReturn(List.of(existing));
        when(corporateActionRepository.save(existing)).thenReturn(existing);

        EquityCorporateActionService.UpsertStats stats =
                upserter.upsertDividendEvents("MSFT", List.of(event(newDate, 0.75, 0.75, 2024)));

        assertEquals(1, stats.changed());
        assertEquals(newDate, existing.getEffectiveDate());
    }

    // @spec EQUITY-DIV-030
    @Test
    void upsertDividendEvents_multipleNewEvents_allInserted() {
        when(corporateActionRepository.findByTicker("T")).thenReturn(List.of());
        when(corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(eq("T"), eq(ActionType.DIVIDEND), any()))
                .thenReturn(List.of());
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EquityCorporateActionService.DividendEvent> events = List.of(
                event(LocalDate.of(2024, 3, 14), 0.2775, 0.2775, 2024),
                event(LocalDate.of(2024, 6, 13), 0.2775, 0.2775, 2024),
                event(LocalDate.of(2024, 9, 12), 0.2775, 0.2775, 2024));

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("T", events);

        assertEquals(3, stats.changed());
        assertEquals(3, stats.inserted());
    }

    // @spec EQUITY-DIV-026
    @Test
    void upsertDividendEvents_duplicateEffectiveDateDifferentAmount_bothKept() {
        // Same ex-date but different amounts (regular + special on same day) → both should be inserted
        LocalDate exDate = LocalDate.of(2024, 12, 10);
        when(corporateActionRepository.findByTicker("COST")).thenReturn(List.of());
        when(corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(eq("COST"), eq(ActionType.DIVIDEND), eq(exDate)))
                .thenReturn(List.of())
                .thenAnswer(inv -> {
                    CorporateAction already = existingDividend("COST", exDate, 0.57, 0.57);
                    return List.of(already);
                });
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EquityCorporateActionService.DividendEvent> events = List.of(
                event(exDate, 0.57, 0.57, 2024),
                event(exDate, 15.0, 15.0, 2024)); // special dividend same day

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("COST", events);

        assertEquals(2, stats.inserted());
    }

    // @spec EQUITY-DIV-032
    @Test
    void upsertDividendEvents_orphanedSecXbrlRecord_isPruned() {
        // Existing DB has 3 SEC_EQUITY_XBRL dividends in 2024; new normalization only produces 2.
        // The unmatched record should be deleted and counted as pruned.
        LocalDate kept1 = LocalDate.of(2024, 3, 14);
        LocalDate kept2 = LocalDate.of(2024, 6, 13);
        LocalDate orphan = LocalDate.of(2024, 2, 10); // no longer produced by normalization

        CorporateAction existKept1 = existingDividend("AAPL", kept1, 0.22, 0.22);
        CorporateAction existKept2 = existingDividend("AAPL", kept2, 0.22, 0.22);
        CorporateAction existOrphan = existingDividend("AAPL", orphan, 0.22, 0.22);

        when(corporateActionRepository.findByTicker("AAPL"))
                .thenReturn(List.of(existKept1, existKept2, existOrphan));

        List<EquityCorporateActionService.DividendEvent> events = List.of(
                event(kept1, 0.22, 0.22, 2024),
                event(kept2, 0.22, 0.22, 2024));

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("AAPL", events);

        assertEquals(1, stats.pruned());
        assertEquals(1, stats.changed());
        assertEquals(0, stats.inserted());
        assertEquals(0, stats.updated());
        verify(corporateActionRepository).delete(existOrphan);
    }

    // @spec EQUITY-DIV-032
    @Test
    void upsertDividendEvents_orphanOutsideYearRange_notPruned() {
        // Orphan in year 2020, but detected events only span 2024 → should not be pruned.
        LocalDate detected = LocalDate.of(2024, 3, 14);
        LocalDate outOfRange = LocalDate.of(2020, 2, 10);

        CorporateAction existDetected = existingDividend("AAPL", detected, 0.22, 0.22);
        CorporateAction existOld = existingDividend("AAPL", outOfRange, 0.205, 0.205);

        when(corporateActionRepository.findByTicker("AAPL"))
                .thenReturn(List.of(existDetected, existOld));

        List<EquityCorporateActionService.DividendEvent> events = List.of(
                event(detected, 0.22, 0.22, 2024));

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("AAPL", events);

        assertEquals(0, stats.pruned());
        verify(corporateActionRepository, never()).delete(existOld);
    }

    private EquityCorporateActionService.DividendEvent eventWithProvenance(
            LocalDate exDate, double raw, String exDateSource, LocalDate recordDate, LocalDate payDate) {
        return new EquityCorporateActionService.DividendEvent(
                exDate.minusDays(40), exDate, raw, raw, exDate.getYear(), false,
                recordDate, payDate, exDateSource);
    }

    // @spec EQUITY-DIV-030
    @Test
    void insertPersistsProvenanceFields() {
        LocalDate exDate = LocalDate.of(2024, 3, 14);
        LocalDate recordDate = LocalDate.of(2024, 3, 15);
        LocalDate payDate = LocalDate.of(2024, 3, 28);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of());
        when(corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(any(), any(), any()))
                .thenReturn(List.of());
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        upserter.upsertDividendEvents("AAPL", List.of(eventWithProvenance(
                exDate, 0.22, CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED, recordDate, payDate)));

        verify(corporateActionRepository).save(argThat(a ->
                CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED.equals(a.getExDateSource())
                        && recordDate.equals(a.getRecordDate())
                        && payDate.equals(a.getPayDate())
                        && Double.valueOf(95.0).equals(a.getConfidenceScore())));
    }

    // @spec EQUITY-DIV-027, EQUITY-DIV-028
    @Test
    void syntheticEventDoesNotMoveTupleAnchoredDate() {
        LocalDate tupleDate = LocalDate.of(2024, 3, 14);
        LocalDate syntheticDate = LocalDate.of(2024, 3, 22);
        CorporateAction existing = existingDividend("AAPL", tupleDate, 0.22, 0.22);
        existing.setExDateSource(CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED);
        existing.setConfidenceScore(95.0);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(existing));

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("AAPL",
                List.of(eventWithProvenance(syntheticDate, 0.22, CorporateAction.EX_DATE_SOURCE_SYNTHETIC, null, null)));

        // Amounts unchanged and the date is protected: nothing to save.
        assertEquals(0, stats.updated());
        assertEquals(tupleDate, existing.getEffectiveDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED, existing.getExDateSource());
        verify(corporateActionRepository, never()).save(any());
    }

    // @spec EQUITY-DIV-027, EQUITY-DIV-028
    @Test
    void tupleMatchedEventMovesSyntheticDate() {
        LocalDate syntheticDate = LocalDate.of(2024, 3, 22);
        LocalDate tupleDate = LocalDate.of(2024, 3, 14);
        LocalDate recordDate = LocalDate.of(2024, 3, 15);
        CorporateAction existing = existingDividend("AAPL", syntheticDate, 0.22, 0.22);
        existing.setExDateSource(CorporateAction.EX_DATE_SOURCE_SYNTHETIC);
        existing.setConfidenceScore(10.0);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(existing));
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("AAPL",
                List.of(eventWithProvenance(tupleDate, 0.22, CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED, recordDate, null)));

        assertEquals(1, stats.updated());
        assertEquals(tupleDate, existing.getEffectiveDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED, existing.getExDateSource());
        assertEquals(recordDate, existing.getRecordDate());
        assertEquals(95.0, existing.getConfidenceScore());
    }

    // @spec EQUITY-DIV-033
    @Test
    void orphanedTupleAnchoredRecordIsNotPruned() {
        // A declaration-anchored row must never be deleted by the pruner: delete + re-insert
        // would bypass the rank guard that protects anchored dates from synthetic re-detections.
        LocalDate anchoredDate = LocalDate.of(2024, 3, 14);
        LocalDate unrelatedDate = LocalDate.of(2024, 9, 20);
        CorporateAction anchored = existingDividend("AAPL", anchoredDate, 0.22, 0.22);
        anchored.setExDateSource(CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(anchored));
        when(corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(any(), any(), any()))
                .thenReturn(List.of());
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("AAPL",
                List.of(event(unrelatedDate, 0.25, 0.25, 2024)));

        assertEquals(0, stats.pruned());
        verify(corporateActionRepository, never()).delete(any());
    }

    // @spec EQUITY-DIV-034, EQUITY-DIV-035
    @Test
    void degradedScanSkipsPruningAndSyntheticInserts() {
        // Filing scan failures mean missing declarations are not evidence of absence: no orphan
        // pruning and no synthetic-dated inserts, but grounded events still insert.
        LocalDate orphanDate = LocalDate.of(2024, 3, 14);
        LocalDate syntheticDate = LocalDate.of(2024, 6, 20);
        LocalDate tupleDate = LocalDate.of(2024, 9, 12);
        CorporateAction orphan = existingDividend("AAPL", orphanDate, 0.22, 0.22);
        orphan.setExDateSource(CorporateAction.EX_DATE_SOURCE_SYNTHETIC);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(orphan));
        when(corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(any(), any(), any()))
                .thenReturn(List.of());
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquityCorporateActionService.UpsertStats stats = upserter.upsertDividendEvents("AAPL",
                List.of(
                        eventWithProvenance(syntheticDate, 0.25, CorporateAction.EX_DATE_SOURCE_SYNTHETIC, null, null),
                        eventWithProvenance(tupleDate, 0.25, CorporateAction.EX_DATE_SOURCE_TUPLE_MATCHED,
                                tupleDate.plusDays(1), null)),
                true);

        assertEquals(1, stats.inserted());
        assertEquals(0, stats.pruned());
        verify(corporateActionRepository, never()).delete(any());
        verify(corporateActionRepository).save(argThat(a -> tupleDate.equals(a.getEffectiveDate())));
    }
}

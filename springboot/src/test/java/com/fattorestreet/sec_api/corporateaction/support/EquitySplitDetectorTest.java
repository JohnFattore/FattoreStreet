package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService.SplitDateCandidate;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService.SplitDetectionStats;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquitySplitDetectorTest {

    private static final String TICKER = "AAPL";
    private static final long CIK = 320193L;
    private static final SplitPriceCorroborator.PriceSeries EMPTY_SERIES =
            new SplitPriceCorroborator.PriceSeries(List.of(), new double[0]);

    @Mock
    private CorporateActionFilingDateService corporateActionFilingDateService;
    @Mock
    private CorporateActionRepository corporateActionRepository;
    @Mock
    private SplitPriceCorroborator splitPriceCorroborator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EquitySplitDetector detector;

    @BeforeEach
    void setUp() {
        detector = new EquitySplitDetector(
                corporateActionFilingDateService, corporateActionRepository, splitPriceCorroborator);
        lenient().when(splitPriceCorroborator.load(anyString())).thenReturn(EMPTY_SERIES);
    }

    private JsonNode facts(String sharesEntries) {
        return objectMapper.readTree("""
                {
                  "facts": {
                    "dei": {
                      "EntityCommonStockSharesOutstanding": {
                        "units": {
                          "shares": %s
                        }
                      }
                    }
                  }
                }
                """.formatted(sharesEntries));
    }

    private static String sharesEntry(String end, long val, String form) {
        return "{\"end\": \"%s\", \"val\": %d, \"form\": \"%s\"}".formatted(end, val, form);
    }

    private static CorporateAction existingSplit(LocalDate effectiveDate, double ratio, Double confidence, String exDateSource) {
        CorporateAction action = new CorporateAction();
        action.setTicker(TICKER);
        action.setActionType(ActionType.SPLIT);
        action.setEffectiveDate(effectiveDate);
        action.setRatio(ratio);
        action.setConfidenceScore(confidence);
        action.setExDateSource(exDateSource);
        action.setSourceType(CorporateAction.SourceType.SEC_EQUITY_XBRL);
        return action;
    }

    private static SplitPriceCorroborator.Corroboration corroboration(LocalDate breakDate, double multiplier) {
        return new SplitPriceCorroborator.Corroboration(breakDate, multiplier, multiplier, 0.0);
    }

    // @spec EQUITY-SPLIT-002, EQUITY-SPLIT-008
    @Test
    void detectsForwardSplitWithFallbackDate() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        CorporateAction saved = captor.getValue();
        assertEquals(TICKER, saved.getTicker());
        assertEquals(ActionType.SPLIT, saved.getActionType());
        assertEquals(0.5, saved.getRatio());
        // No SEC candidate and no price coverage: falls back to the shares-entry date.
        assertEquals(LocalDate.of(2020, 6, 30), saved.getEffectiveDate());
        assertEquals(CorporateAction.SourceType.SEC_EQUITY_XBRL, saved.getSourceType());
        assertEquals(CorporateAction.EX_DATE_SOURCE_SHARE_FACT, saved.getExDateSource());
        assertEquals(1, stats.created());
    }

    // @spec EQUITY-SPLIT-007, EQUITY-SPLIT-010
    @Test
    void prefersSecSplitDateCandidateInsideWindow() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-09-30", 400_000_000L, "10-Q")));
        SplitDateCandidate candidate = new SplitDateCandidate(
                LocalDate.of(2020, 8, 31), LocalDate.of(2020, 7, 31), "acc-1", 90);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of(candidate));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2020, 8, 31), captor.getValue().getEffectiveDate());
        assertEquals(0.25, captor.getValue().getRatio());
        assertEquals(CorporateAction.EX_DATE_SOURCE_FILING_TEXT, captor.getValue().getExDateSource());
        assertEquals(1, stats.created());
    }

    // @spec EQUITY-SPLIT-007, EQUITY-SPLIT-008
    @Test
    void priceBreakOverridesFilingCandidateDate() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-09-30", 400_000_000L, "10-Q")));
        SplitDateCandidate candidate = new SplitDateCandidate(
                LocalDate.of(2020, 8, 31), LocalDate.of(2020, 7, 31), "acc-1", 90);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of(candidate));
        when(splitPriceCorroborator.corroborate(any(), anyDouble(), any(), any()))
                .thenReturn(Optional.of(corroboration(LocalDate.of(2020, 8, 28), 4.0)));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2020, 8, 28), captor.getValue().getEffectiveDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_PRICE_BREAK, captor.getValue().getExDateSource());
        assertEquals(100.0, captor.getValue().getConfidenceScore());
        assertEquals(1, stats.priceCorroborated());
        assertEquals(1, stats.priceSnapped());
    }

    // @spec EQUITY-SPLIT-014
    @Test
    void coveredWindowWithoutBreakRejectsSplit() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(splitPriceCorroborator.windowFullyCovered(any(), any(), any())).thenReturn(true);

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
        assertEquals(1, stats.priceRejected());
    }

    // @spec EQUITY-SPLIT-016
    @Test
    void reDatingUpdatesExistingRowInsteadOfInserting() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        CorporateAction existing = existingSplit(
                LocalDate.of(2020, 6, 30), 0.5, 40.0, CorporateAction.EX_DATE_SOURCE_SHARE_FACT);
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(existing));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(splitPriceCorroborator.corroborate(any(), anyDouble(), any(), any()))
                .thenReturn(Optional.of(corroboration(LocalDate.of(2020, 6, 15), 2.0)));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        // Same entity re-dated in place; nothing new inserted.
        verify(corporateActionRepository).save(same(existing));
        assertEquals(LocalDate.of(2020, 6, 15), existing.getEffectiveDate());
        assertEquals(CorporateAction.EX_DATE_SOURCE_PRICE_BREAK, existing.getExDateSource());
        assertEquals(100.0, existing.getConfidenceScore());
        assertEquals(0, stats.created());
        assertEquals(1, stats.priceSnapped());
    }

    // @spec EQUITY-SPLIT-016
    @Test
    void weakerResolutionDoesNotMoveWellGroundedDate() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        CorporateAction existing = existingSplit(
                LocalDate.of(2020, 6, 15), 0.5, 100.0, CorporateAction.EX_DATE_SOURCE_PRICE_BREAK);
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(existing));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        // Share-fact fallback (confidence 40) must not overwrite the price-break date.
        verify(corporateActionRepository, never()).save(any());
        assertEquals(LocalDate.of(2020, 6, 15), existing.getEffectiveDate());
        assertEquals(0, stats.created());
    }

    // @spec EQUITY-SPLIT-016
    @Test
    void skipsWhenSplitAlreadyPersistedWithSameGrounding() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        CorporateAction existing = existingSplit(
                LocalDate.of(2020, 6, 30), 0.5, 40.0, CorporateAction.EX_DATE_SOURCE_SHARE_FACT);
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(existing));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
    }

    // @spec EQUITY-SPLIT-002
    @Test
    void noSplitWhenSharesStayFlat() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 101_000_000L, "10-Q")));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        // No ratio jump and no price break: the filing scan is skipped entirely.
        verify(corporateActionFilingDateService, never()).fetchSplitEffectiveDates(anyLong());
        assertEquals(0, stats.created());
        assertEquals(2, stats.sharesFactsParsed());
    }

    // @spec EQUITY-SPLIT-001
    @Test
    void missingSharesFactsReturnsZeroStats() {
        JsonNode root = objectMapper.readTree("{\"facts\": {}}");

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        assertEquals(0, stats.sharesFactsParsed());
        assertEquals(0, stats.created());
        verifyNoInteractions(corporateActionFilingDateService);
    }

    // @spec EQUITY-SPLIT-001
    @Test
    void ignoresIrrelevantForms() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "S-1"),
                sharesEntry("2020-06-30", 200_000_000L, "S-1")));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        assertEquals(0, stats.sharesFactsParsed());
        verify(corporateActionRepository, never()).save(any());
        verify(corporateActionFilingDateService, never()).fetchSplitEffectiveDates(anyLong());
    }

    // @spec EQUITY-SPLIT-004
    @Test
    void extendedRatioRequiresSecDateConfirmation() {
        // 3:2 split (x1.5) is an "extended" ratio: skipped without a matching SEC candidate
        // or a corroborating price break.
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 150_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
    }

    // @spec EQUITY-SPLIT-004
    @Test
    void extendedRatioPersistedWhenSecCandidateMatches() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 150_000_000L, "10-Q")));
        SplitDateCandidate candidate = new SplitDateCandidate(
                LocalDate.of(2020, 6, 15), LocalDate.of(2020, 5, 20), "acc-2", 80);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of(candidate));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(1.0 / 1.5, captor.getValue().getRatio(), 1e-9);
        assertEquals(1, stats.created());
    }

    // @spec EQUITY-SPLIT-004
    @Test
    void extendedRatioPersistedWhenPriceBreakConfirms() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 150_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(splitPriceCorroborator.corroborate(any(), anyDouble(), any(), any()))
                .thenReturn(Optional.of(corroboration(LocalDate.of(2020, 6, 12), 1.5)));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2020, 6, 12), captor.getValue().getEffectiveDate());
        assertEquals(1.0 / 1.5, captor.getValue().getRatio(), 1e-9);
        assertEquals(1, stats.created());
    }

    // @spec EQUITY-SPLIT-005
    @Test
    void skipsSharesPairsWithHugeGaps() {
        // > 400 days between observations: a doubling is not credible split evidence.
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2018-03-31", 100_000_000L, "10-K"),
                sharesEntry("2020-06-30", 200_000_000L, "10-K")));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        verify(corporateActionFilingDateService, never()).fetchSplitEffectiveDates(anyLong());
        assertEquals(0, stats.created());
    }

    // @spec EQUITY-SPLIT-003
    @Test
    void detectsReverseSplit() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 25_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(4.0, captor.getValue().getRatio(), 1e-9);
    }

    // @spec EQUITY-SPLIT-028
    @Test
    void duplicateDatesKeepLargestSharesValue() {
        // Two rows for the same end date (original + amendment): the larger value wins,
        // so no bogus 2:1 "split" between them is detected.
        JsonNode root = facts("[%s, %s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-03-31", 200_000_000L, "10-Q/A"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        verify(corporateActionFilingDateService, never()).fetchSplitEffectiveDates(anyLong());
        assertEquals(0, stats.created());
    }

    // @spec EQUITY-SPLIT-021
    @Test
    void priceOnlyBreakWithFilingCandidatePersists() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 100_000_000L, "10-Q")));
        SplitDateCandidate candidate = new SplitDateCandidate(
                LocalDate.of(2020, 5, 4), LocalDate.of(2020, 4, 20), "acc-3", 85);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of(candidate));
        when(splitPriceCorroborator.scanForSplitLikeBreaks(any(), any()))
                .thenReturn(List.of(corroboration(LocalDate.of(2020, 5, 4), 2.0)));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        CorporateAction saved = captor.getValue();
        assertEquals(CorporateAction.SourceType.SEC_PRICE_CORROBORATED, saved.getSourceType());
        assertEquals(LocalDate.of(2020, 5, 4), saved.getEffectiveDate());
        assertEquals(0.5, saved.getRatio());
        assertEquals(90.0, saved.getConfidenceScore());
        assertEquals("acc-3", saved.getAccessionNumber());
        assertEquals(1, stats.priceOnlyDetected());
        assertEquals(1, stats.created());
    }

    // @spec EQUITY-SPLIT-023
    @Test
    void priceOnlyMidSizeBreakWithoutFilingCandidateStaysUnconfirmed() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 100_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(splitPriceCorroborator.scanForSplitLikeBreaks(any(), any()))
                .thenReturn(List.of(corroboration(LocalDate.of(2020, 5, 4), 2.0)));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(1, stats.priceOnlyUnconfirmed());
        assertEquals(0, stats.created());
    }

    // @spec EQUITY-SPLIT-022
    @Test
    void priceOnlyLargeBreakPersistsWithoutFilingCandidate() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 100_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(splitPriceCorroborator.scanForSplitLikeBreaks(any(), any()))
                .thenReturn(List.of(corroboration(LocalDate.of(2020, 5, 4), 10.0)));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(0.1, captor.getValue().getRatio(), 1e-9);
        assertEquals(60.0, captor.getValue().getConfidenceScore());
        assertEquals(1, stats.priceOnlyDetected());
    }

    // @spec EQUITY-SPLIT-020
    @Test
    void priceOnlyBreakNearExistingSplitIsExplained() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 100_000_000L, "10-Q")));
        CorporateAction existing = existingSplit(
                LocalDate.of(2020, 5, 1), 0.5, 100.0, CorporateAction.EX_DATE_SOURCE_PRICE_BREAK);
        when(corporateActionRepository.findByTicker(TICKER)).thenReturn(List.of(existing));
        when(splitPriceCorroborator.scanForSplitLikeBreaks(any(), any()))
                .thenReturn(List.of(corroboration(LocalDate.of(2020, 5, 4), 2.0)));

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        // The break is already explained by the persisted split, so no filing scan either.
        verify(corporateActionFilingDateService, never()).fetchSplitEffectiveDates(anyLong());
        assertEquals(0, stats.priceOnlyDetected());
        assertEquals(0, stats.priceOnlyUnconfirmed());
    }
}

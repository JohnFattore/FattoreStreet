package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService.SplitDateCandidate;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService.SplitDetectionStats;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquitySplitDetectorTest {

    private static final String TICKER = "AAPL";
    private static final long CIK = 320193L;

    @Mock
    private CorporateActionFilingDateService corporateActionFilingDateService;
    @Mock
    private CorporateActionRepository corporateActionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EquitySplitDetector detector;

    @BeforeEach
    void setUp() {
        detector = new EquitySplitDetector(corporateActionFilingDateService, corporateActionRepository);
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

    @Test
    void detectsForwardSplitWithFallbackDate() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                eq(TICKER), eq(ActionType.SPLIT), any())).thenReturn(false);

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        CorporateAction saved = captor.getValue();
        assertEquals(TICKER, saved.getTicker());
        assertEquals(ActionType.SPLIT, saved.getActionType());
        assertEquals(0.5, saved.getRatio());
        // No SEC candidate: falls back to the shares-entry date where the jump was observed.
        assertEquals(LocalDate.of(2020, 6, 30), saved.getEffectiveDate());
        assertEquals(CorporateAction.SourceType.SEC_EQUITY_XBRL, saved.getSourceType());
        assertEquals(1, stats.created());
    }

    @Test
    void prefersSecSplitDateCandidateInsideWindow() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-09-30", 400_000_000L, "10-Q")));
        SplitDateCandidate candidate = new SplitDateCandidate(
                LocalDate.of(2020, 8, 31), LocalDate.of(2020, 7, 31), "acc-1", 90);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of(candidate));
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                TICKER, ActionType.SPLIT, LocalDate.of(2020, 8, 31))).thenReturn(false);

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2020, 8, 31), captor.getValue().getEffectiveDate());
        assertEquals(0.25, captor.getValue().getRatio());
        assertEquals(1, stats.created());
    }

    @Test
    void skipsWhenSplitAlreadyPersisted() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                eq(TICKER), eq(ActionType.SPLIT), any())).thenReturn(true);

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
    }

    @Test
    void noSplitWhenSharesStayFlat() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 101_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
        assertEquals(2, stats.sharesFactsParsed());
    }

    @Test
    void missingSharesFactsReturnsZeroStats() {
        JsonNode root = objectMapper.readTree("{\"facts\": {}}");

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        assertEquals(0, stats.sharesFactsParsed());
        assertEquals(0, stats.created());
        verifyNoInteractions(corporateActionFilingDateService);
    }

    @Test
    void ignoresIrrelevantForms() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "S-1"),
                sharesEntry("2020-06-30", 200_000_000L, "S-1")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        assertEquals(0, stats.sharesFactsParsed());
        verify(corporateActionRepository, never()).save(any());
    }

    @Test
    void extendedRatioRequiresSecDateConfirmation() {
        // 3:2 split (x1.5) is an "extended" ratio: skipped without a matching SEC candidate.
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 150_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
    }

    @Test
    void extendedRatioPersistedWhenSecCandidateMatches() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 150_000_000L, "10-Q")));
        SplitDateCandidate candidate = new SplitDateCandidate(
                LocalDate.of(2020, 6, 15), LocalDate.of(2020, 5, 20), "acc-2", 80);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of(candidate));
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                TICKER, ActionType.SPLIT, LocalDate.of(2020, 6, 15))).thenReturn(false);

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(1.0 / 1.5, captor.getValue().getRatio(), 1e-9);
        assertEquals(1, stats.created());
    }

    @Test
    void skipsSharesPairsWithHugeGaps() {
        // > 400 days between observations: a doubling is not credible split evidence.
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2018-03-31", 100_000_000L, "10-K"),
                sharesEntry("2020-06-30", 200_000_000L, "10-K")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
    }

    @Test
    void detectsReverseSplit() {
        JsonNode root = facts("[%s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-06-30", 25_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(CIK)).thenReturn(List.of());
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                eq(TICKER), eq(ActionType.SPLIT), any())).thenReturn(false);

        detector.detectSplits(TICKER, CIK, root);

        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        assertEquals(4.0, captor.getValue().getRatio(), 1e-9);
    }

    @Test
    void duplicateDatesKeepLargestSharesValue() {
        // Two rows for the same end date (original + amendment): the larger value wins,
        // so no bogus 2:1 "split" between them is detected.
        JsonNode root = facts("[%s, %s, %s]".formatted(
                sharesEntry("2020-03-31", 100_000_000L, "10-Q"),
                sharesEntry("2020-03-31", 200_000_000L, "10-Q/A"),
                sharesEntry("2020-06-30", 200_000_000L, "10-Q")));
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(anyLong())).thenReturn(List.of());

        SplitDetectionStats stats = detector.detectSplits(TICKER, CIK, root);

        verify(corporateActionRepository, never()).save(any());
        assertEquals(0, stats.created());
    }
}

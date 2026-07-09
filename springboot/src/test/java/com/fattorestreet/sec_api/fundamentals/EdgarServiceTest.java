package com.fattorestreet.sec_api.fundamentals;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Quarter;
import com.fattorestreet.sec_api.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgarServiceTest {

    @Mock
    private WebService webService;

    @Mock
    private QuarterService quarterService;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private EdgarService edgarService;

    private void callDeriveBalanceSheetFields(Quarter q) throws Exception {
        Method method = EdgarService.class.getDeclaredMethod("deriveBalanceSheetFields", Quarter.class);
        method.setAccessible(true);
        method.invoke(edgarService, q);
    }

    @Test
    void derivesEquity_whenAssetsAndLiabilitiesPresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);
        q.setLiabilities(600L);

        callDeriveBalanceSheetFields(q);

        assertEquals(400L, q.getStockholdersEquity());
        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
    }

    @Test
    void derivesAssets_whenLiabilitiesAndEquityPresent() throws Exception {
        Quarter q = new Quarter();
        q.setLiabilities(600L);
        q.setStockholdersEquity(400L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
        assertEquals(400L, q.getStockholdersEquity());
    }

    @Test
    void derivesLiabilities_whenAssetsAndEquityPresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);
        q.setStockholdersEquity(400L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
        assertEquals(400L, q.getStockholdersEquity());
    }

    @Test
    void noOp_whenAllThreePresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);
        q.setLiabilities(600L);
        q.setStockholdersEquity(400L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertEquals(600L, q.getLiabilities());
        assertEquals(400L, q.getStockholdersEquity());
    }

    @Test
    void noOp_whenOnlyOnePresent() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(1000L);

        callDeriveBalanceSheetFields(q);

        assertEquals(1000L, q.getAssets());
        assertNull(q.getLiabilities());
        assertNull(q.getStockholdersEquity());
    }

    @Test
    void noOp_whenNonePresent() throws Exception {
        Quarter q = new Quarter();

        callDeriveBalanceSheetFields(q);

        assertNull(q.getAssets());
        assertNull(q.getLiabilities());
        assertNull(q.getStockholdersEquity());
    }

    @Test
    void handlesNegativeEquity() throws Exception {
        Quarter q = new Quarter();
        q.setAssets(500L);
        q.setLiabilities(800L);

        callDeriveBalanceSheetFields(q);

        assertEquals(-300L, q.getStockholdersEquity());
    }

    // --- updateFinancials (companyfacts parsing) ---

    private static Asset asset(long cik) {
        Asset a = new Asset();
        a.setId(1L);
        a.setCik(cik);
        return a;
    }

    private List<Quarter> runUpdateFinancials(String companyFactsJson) throws Exception {
        Asset apple = asset(320193L);
        when(webService.fetchFinancials(320193L)).thenReturn(companyFactsJson);

        edgarService.updateFinancials(apple);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Quarter>> captor = ArgumentCaptor.forClass(List.class);
        verify(quarterService).updateAssetQuarters(eq(apple), captor.capture());
        return captor.getValue();
    }

    @Test
    void updateFinancials_buildsQuarterFromThreeMonthFacts() throws Exception {
        List<Quarter> quarters = runUpdateFinancials("""
                {
                  "facts": {
                    "us-gaap": {
                      "Revenues": {
                        "units": {
                          "USD": [
                            {"start": "2024-01-01", "end": "2024-03-31", "val": 1000,
                             "fy": 2024, "fp": "Q1", "form": "10-Q"}
                          ]
                        }
                      },
                      "NetIncomeLoss": {
                        "units": {
                          "USD": [
                            {"start": "2024-01-01", "end": "2024-03-31", "val": 250,
                             "fy": 2024, "fp": "Q1", "form": "10-Q"}
                          ]
                        }
                      },
                      "Assets": {
                        "units": {
                          "USD": [
                            {"end": "2024-03-31", "val": 5000, "fy": 2024, "fp": "Q1", "form": "10-Q"}
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        assertEquals(1, quarters.size());
        Quarter q = quarters.get(0);
        assertEquals(2024, q.getYear());
        assertEquals(1, q.getQuarter());
        assertEquals(1000L, q.getRevenues());
        assertEquals(250L, q.getNetIncomeLoss());
        // The instant Assets fact lands in its own period key (end|end), so this quarter
        // only carries the flow fields.
        assertNull(q.getAssets());
    }

    @Test
    void updateFinancials_derivesQuarterFromYtdDifference() throws Exception {
        // Q2 revenue reported only as a 6-month YTD figure: derived as YTD6 - Q1.
        List<Quarter> quarters = runUpdateFinancials("""
                {
                  "facts": {
                    "us-gaap": {
                      "Revenues": {
                        "units": {
                          "USD": [
                            {"start": "2024-01-01", "end": "2024-03-31", "val": 1000,
                             "fy": 2024, "fp": "Q1", "form": "10-Q"},
                            {"start": "2024-01-01", "end": "2024-06-30", "val": 2200,
                             "fy": 2024, "fp": "Q2", "form": "10-Q"}
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        assertEquals(2, quarters.size());
        Quarter q2 = quarters.stream()
                .filter(q -> q.getPeriodEnd().toString().equals("2024-06-30"))
                .findFirst().orElseThrow();
        assertEquals(1200L, q2.getRevenues());
        assertEquals("2024-04-01", q2.getPeriodStart().toString());
    }

    @Test
    void updateFinancials_skipsShortPeriods() throws Exception {
        List<Quarter> quarters = runUpdateFinancials("""
                {
                  "facts": {
                    "us-gaap": {
                      "Revenues": {
                        "units": {
                          "USD": [
                            {"start": "2024-03-01", "end": "2024-03-31", "val": 300,
                             "fy": 2024, "fp": "Q1", "form": "8-K"}
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        assertTrue(quarters.isEmpty(), "a one-month flow period is not a quarter");
    }

    @Test
    void updateFinancials_missingFactsYieldsNoQuarters() throws Exception {
        assertTrue(runUpdateFinancials("{}").isEmpty());
    }

    @Test
    void updateFinancials_fallbackRevenueTagIsUsed() throws Exception {
        List<Quarter> quarters = runUpdateFinancials("""
                {
                  "facts": {
                    "us-gaap": {
                      "RevenueFromContractWithCustomerExcludingAssessedTax": {
                        "units": {
                          "USD": [
                            {"start": "2024-01-01", "end": "2024-03-31", "val": 900,
                             "fy": 2024, "fp": "Q1", "form": "10-Q"}
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        assertEquals(1, quarters.size());
        assertEquals(900L, quarters.get(0).getRevenues());
    }

    // --- syncFramesFull (XBRL frames sync) ---

    @Test
    void syncFramesFull_collectsFramesAndDerivesMissingQuarterFromAnnual() throws Exception {
        Asset apple = asset(320193L);
        when(assetRepository.findByIsFund(false)).thenReturn(List.of(apple));
        when(assetRepository.countByIsFund(true)).thenReturn(7L);
        // Default: every frame request returns an empty payload.
        lenient().when(webService.fetchXbrlFrames(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("{}");

        // Q1-Q3 2024 net income frames; Q4 comes only from the annual total.
        when(webService.fetchXbrlFrames("us-gaap", "NetIncomeLoss", "USD", "CY2024Q1"))
                .thenReturn(frame("2024-01-01", "2024-03-31", 100, 2024, "Q1"));
        when(webService.fetchXbrlFrames("us-gaap", "NetIncomeLoss", "USD", "CY2024Q2"))
                .thenReturn(frame("2024-04-01", "2024-06-30", 200, 2024, "Q2"));
        when(webService.fetchXbrlFrames("us-gaap", "NetIncomeLoss", "USD", "CY2024Q3"))
                .thenReturn(frame("2024-07-01", "2024-09-30", 300, 2024, "Q3"));
        when(webService.fetchXbrlFrames("us-gaap", "NetIncomeLoss", "USD", "CY2024"))
                .thenReturn(frame("2024-01-01", "2024-12-31", 1000, 2024, "FY"));

        var result = edgarService.syncFramesFull();

        assertEquals(1, result.get("equitiesProcessed"));
        assertEquals(7L, result.get("fundsSkipped"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Quarter>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(quarterService).batchUpsertQuarters(eq(2024), eq(1), captor.capture());
        assertEquals(100L, captor.getValue().iterator().next().getNetIncomeLoss());

        verify(quarterService).batchUpsertQuarters(eq(2024), eq(4), captor.capture());
        Quarter q4 = captor.getValue().iterator().next();
        assertEquals(400L, q4.getNetIncomeLoss(), "Q4 must be derived as annual - (Q1+Q2+Q3)");
        assertEquals("2024-12-31", q4.getPeriodEnd().toString());
    }

    @Test
    void syncFramesFull_ignoresFramesForUnknownCiksAndNonQuarterPeriods() throws Exception {
        Asset apple = asset(320193L);
        when(assetRepository.findByIsFund(false)).thenReturn(List.of(apple));
        when(assetRepository.countByIsFund(true)).thenReturn(0L);
        lenient().when(webService.fetchXbrlFrames(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("{}");

        // Unknown CIK plus a 6-month "quarter": both must be dropped.
        when(webService.fetchXbrlFrames("us-gaap", "Revenues", "USD", "CY2024Q1"))
                .thenReturn("""
                        {"data": [
                          {"cik": 999999, "start": "2024-01-01", "end": "2024-03-31", "val": 5,
                           "fy": 2024, "fp": "Q1"},
                          {"cik": 320193, "start": "2024-01-01", "end": "2024-06-30", "val": 5,
                           "fy": 2024, "fp": "Q1"}
                        ]}
                        """);

        edgarService.syncFramesFull();

        verify(quarterService, org.mockito.Mockito.never())
                .batchUpsertQuarters(any(), any(), any());
    }

    private static String frame(String start, String end, long val, int fy, String fp) {
        return """
                {"data": [
                  {"cik": 320193, "start": "%s", "end": "%s", "val": %d, "fy": %d, "fp": "%s"}
                ]}
                """.formatted(start, end, val, fy, fp);
    }
}

package com.example.sec_api.service;

import com.example.sec_api.model.CorporateAction;
import com.example.sec_api.model.CorporateAction.ActionType;
import com.example.sec_api.repository.CorporateActionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SplitDividendServiceTest {

    @Mock private WebService webService;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Spy private ObjectMapper mapper = new ObjectMapper();
    @InjectMocks private SplitDividendService service;

    @Test
    void detectsSplitFrom4to1SharesJump() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {
              "EntityCommonStockSharesOutstanding": {
                "units": {
                  "shares": [
                    {"val": 1000000000, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"},
                    {"val": 4000000000, "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"}
                  ]
                }
              }
            },
            "us-gaap": {}
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, created);
        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        CorporateAction action = captor.getValue();
        assertEquals(ActionType.SPLIT, action.getActionType());
        assertEquals(LocalDate.of(2024, 6, 30), action.getEffectiveDate());
        assertEquals(0.25, action.getRatio(), 0.01);
    }

    @Test
    void detectsDividendEntries() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.25, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"},
                    {"val": 0.25, "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(2, created);
        verify(corporateActionRepository, times(2)).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND && a.getRatio() == 0.25));
    }

    @Test
    void filtersOutAnnual10KDividends() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.25, "start": "2024-01-01", "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"},
                    {"val": 0.25, "start": "2024-04-01", "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"},
                    {"val": 0.25, "start": "2024-07-01", "end": "2024-09-30", "form": "10-Q", "filed": "2024-11-01"},
                    {"val": 1.00, "start": "2024-01-01", "end": "2024-12-31", "form": "10-K", "filed": "2025-02-01"},
                    {"val": 0.25, "start": "2024-10-01", "end": "2024-12-31", "form": "10-K", "filed": "2025-02-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        // 3 quarterly 10-Q entries + 1 quarterly 10-K entry (Q4, 92 days) = 4
        // The annual 10-K (365 days) should be filtered out
        assertEquals(4, created);
        verify(corporateActionRepository, times(4)).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND && a.getRatio() == 0.25));
    }

    @Test
    void skipsDuplicateActions() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {
              "EntityCommonStockSharesOutstanding": {
                "units": {
                  "shares": [
                    {"val": 1000000000, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"},
                    {"val": 4000000000, "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"}
                  ]
                }
              }
            },
            "us-gaap": {}
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                eq("AAPL"), eq(ActionType.SPLIT), eq(LocalDate.of(2024, 6, 30)))).thenReturn(true);

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(0, created);
        verify(corporateActionRepository, never()).save(any());
    }

    @Test
    void handlesSecFetchFailureGracefully() throws Exception {
        when(webService.fetchFinancials(999L)).thenThrow(new RuntimeException("404"));

        int created = service.detectAndPersist("FAKE", 999L);

        assertEquals(0, created);
    }

    @Test
    void ignoresNonSplitRatioChanges() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {
              "EntityCommonStockSharesOutstanding": {
                "units": {
                  "shares": [
                    {"val": 1000000000, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"},
                    {"val": 1050000000, "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"}
                  ]
                }
              }
            },
            "us-gaap": {}
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(0, created);
        verify(corporateActionRepository, never()).save(any());
    }
}

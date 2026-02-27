package com.example.sec_api.service;

import com.example.sec_api.model.CorporateAction;
import com.example.sec_api.model.CorporateAction.ActionType;
import com.example.sec_api.repository.CorporateActionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SplitDividendServiceTest {

    @Mock private WebService webService;
    @Mock private DividendRecordDateService dividendRecordDateService;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Spy private ObjectMapper mapper = new ObjectMapper();
    @InjectMocks private SplitDividendService service;

    @BeforeEach
    void setDefaults() {
        lenient().when(dividendRecordDateService.fetchDividendRecordDates(anyLong()))
                .thenReturn(java.util.Collections.emptyList());
    }

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
    void normalizesCumulativeDividendFactsToQuarterly() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.24, "start": "2024-10-01", "end": "2024-12-28", "form": "10-Q", "filed": "2025-01-31"},
                    {"val": 0.49, "start": "2024-10-01", "end": "2025-03-29", "form": "10-Q", "filed": "2025-05-02"},
                    {"val": 0.25, "start": "2024-12-29", "end": "2025-03-29", "form": "10-Q", "filed": "2025-05-02"},
                    {"val": 0.75, "start": "2024-10-01", "end": "2025-06-28", "form": "10-Q", "filed": "2025-08-01"},
                    {"val": 0.26, "start": "2025-03-30", "end": "2025-06-28", "form": "10-Q", "filed": "2025-08-01"},
                    {"val": 1.01, "start": "2024-09-29", "end": "2025-09-27", "form": "10-K", "filed": "2025-10-31"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(4, changed);
        ArgumentCaptor<CorporateAction> cap = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository, times(4)).save(cap.capture());
        List<Double> ratios = cap.getAllValues().stream()
                .filter(a -> a.getActionType() == ActionType.DIVIDEND)
                .map(CorporateAction::getRatio)
                .sorted()
                .toList();
        assertEquals(List.of(0.24, 0.25, 0.26, 0.26), ratios);
    }

    @Test
    void prefersQuarterlyRowWhenSameEndHasCumulativeAndQuarterly() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.73, "start": "2023-10-01", "end": "2024-06-29", "form": "10-Q", "filed": "2025-08-01"},
                    {"val": 0.25, "start": "2024-03-31", "end": "2024-06-29", "form": "10-Q", "filed": "2025-08-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 6, 29))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
    }

    @Test
    void derivesQ4FromAnnualWhenMissingQuarterFact() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.24, "start": "2023-10-01", "end": "2023-12-30", "form": "10-Q", "filed": "2024-02-02"},
                    {"val": 0.24, "start": "2023-12-31", "end": "2024-03-30", "form": "10-Q", "filed": "2024-05-03"},
                    {"val": 0.25, "start": "2024-03-31", "end": "2024-06-29", "form": "10-Q", "filed": "2024-08-02"},
                    {"val": 0.98, "start": "2023-10-01", "end": "2024-09-28", "form": "10-K", "filed": "2024-11-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(4, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 9, 28))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
    }

    @Test
    void snapsSplitRatioBeforeHistoricalDividendAdjustment() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.77, "start": "2019-03-31", "end": "2019-06-29", "form": "10-Q", "filed": "2019-07-31"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        CorporateAction split = new CorporateAction();
        split.setTicker("AAPL");
        split.setActionType(ActionType.SPLIT);
        split.setEffectiveDate(LocalDate.of(2020, 10, 16));
        split.setRatio(0.2514812253);

        when(corporateActionRepository.findByTicker("AAPL"))
                .thenReturn(java.util.List.of(split));
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2019, 6, 29))
                        && Math.abs(a.getRatio() - 0.1925) < 0.0001));
    }

    @Test
    void computesExDateAcrossSettlementTransition() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.25, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"},
                    {"val": 0.26, "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(dividendRecordDateService.fetchDividendRecordDates(320193L))
                .thenReturn(java.util.List.of(
                        new DividendRecordDateService.RecordDateCandidate(LocalDate.of(2024, 5, 10), LocalDate.of(2024, 5, 3), "0001"),
                        new DividendRecordDateService.RecordDateCandidate(LocalDate.of(2024, 8, 12), LocalDate.of(2024, 8, 2), "0002")
                ));
        when(dividendRecordDateService.computeExDividendDate(LocalDate.of(2024, 5, 10)))
                .thenReturn(LocalDate.of(2024, 5, 9));
        when(dividendRecordDateService.computeExDividendDate(LocalDate.of(2024, 8, 12)))
                .thenReturn(LocalDate.of(2024, 8, 12));
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());
        when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(2, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 5, 9))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 8, 12))
                        && Math.abs(a.getRatio() - 0.26) < 0.0001));
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
    void upsertsExistingDividendRowsByYearAndInsertsMissing() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.24, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"},
                    {"val": 0.25, "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"},
                    {"val": 0.25, "end": "2024-09-30", "form": "10-Q", "filed": "2024-11-01"},
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

        CorporateAction old1 = new CorporateAction();
        old1.setTicker("AAPL");
        old1.setActionType(ActionType.DIVIDEND);
        old1.setEffectiveDate(LocalDate.of(2024, 3, 30));
        old1.setRatio(0.48);
        CorporateAction old2 = new CorporateAction();
        old2.setTicker("AAPL");
        old2.setActionType(ActionType.DIVIDEND);
        old2.setEffectiveDate(LocalDate.of(2024, 6, 29));
        old2.setRatio(0.73);
        CorporateAction old3 = new CorporateAction();
        old3.setTicker("AAPL");
        old3.setActionType(ActionType.DIVIDEND);
        old3.setEffectiveDate(LocalDate.of(2024, 12, 28));
        old3.setRatio(0.25);

        when(corporateActionRepository.findByTicker("AAPL"))
                .thenReturn(java.util.List.of(old1, old2, old3));
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(4, changed);
        verify(corporateActionRepository, times(4)).save(any(CorporateAction.class));
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

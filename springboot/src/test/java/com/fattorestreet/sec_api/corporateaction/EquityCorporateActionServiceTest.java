package com.fattorestreet.sec_api.corporateaction;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquityCorporateActionServiceTest {

    @Mock private WebService webService;
    @Mock private CorporateActionFilingDateService corporateActionFilingDateService;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Spy private ObjectMapper mapper = new ObjectMapper();
    @InjectMocks private EquityCorporateActionService service;

    @BeforeEach
    void setDefaults() {
        lenient().when(corporateActionFilingDateService.scanDividendRecordDates(anyLong()))
                .thenReturn(emptyScanResult());
        lenient().when(corporateActionFilingDateService.fetchSplitEffectiveDates(anyLong()))
                .thenReturn(java.util.Collections.emptyList());
        lenient().when(corporateActionFilingDateService.computeExDividendDate(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(corporateActionRepository.existsByTickerAndActionTypeAndEffectiveDate(
                anyString(), any(), any())).thenReturn(false);
    }

    private static CorporateActionFilingDateService.RecordDateScanResult emptyScanResult() {
        return scanResult(java.util.List.of());
    }

    /** Scan result carrying only record-date candidates, matching how these tests drive the service. */
    private static CorporateActionFilingDateService.RecordDateScanResult scanResult(
            java.util.List<CorporateActionFilingDateService.RecordDateCandidate> candidates) {
        return new CorporateActionFilingDateService.RecordDateScanResult(
                candidates, java.util.List.of(), java.util.List.of(),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                candidates.size(), 0);
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
    void detectsSplitUsesSecDerivedEffectiveDateWhenCandidateExists() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {
              "EntityCommonStockSharesOutstanding": {
                "units": {
                  "shares": [
                    {"val": 1000000000, "end": "2020-07-11", "form": "10-Q", "filed": "2020-07-31"},
                    {"val": 4000000000, "end": "2020-10-16", "form": "10-Q", "filed": "2020-10-30"}
                  ]
                }
              }
            },
            "us-gaap": {}
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(320193L))
                .thenReturn(java.util.List.of(
                        new CorporateActionFilingDateService.SplitDateCandidate(
                                LocalDate.of(2020, 8, 31),
                                LocalDate.of(2020, 7, 30),
                                "0000320193-20-000062",
                                120)
                ));

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, created);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.SPLIT
                        && a.getEffectiveDate().equals(LocalDate.of(2020, 8, 31))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
    }

    @Test
    void detectsSplitUsesNearestSecCandidateWhenMultipleDatesExist() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {
              "EntityCommonStockSharesOutstanding": {
                "units": {
                  "shares": [
                    {"val": 1000000000, "end": "2020-07-11", "form": "10-Q", "filed": "2020-07-31"},
                    {"val": 4000000000, "end": "2020-10-16", "form": "10-Q", "filed": "2020-10-30"}
                  ]
                }
              }
            },
            "us-gaap": {}
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(320193L))
                .thenReturn(java.util.List.of(
                        new CorporateActionFilingDateService.SplitDateCandidate(
                                LocalDate.of(2020, 8, 24),
                                LocalDate.of(2020, 7, 30),
                                "0000320193-20-000062",
                                120),
                        new CorporateActionFilingDateService.SplitDateCandidate(
                                LocalDate.of(2020, 8, 31),
                                LocalDate.of(2020, 7, 30),
                                "0000320193-20-000062",
                                120)
                ));

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, created);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.SPLIT
                        && a.getEffectiveDate().equals(LocalDate.of(2020, 8, 31))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
    }

    @Test
    void detectsSplitFallsBackToDetectedDateWhenAllCandidatesFilteredOut() throws Exception {
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
        when(corporateActionFilingDateService.fetchSplitEffectiveDates(320193L))
                .thenReturn(java.util.List.of(
                        new CorporateActionFilingDateService.SplitDateCandidate(
                                LocalDate.of(2019, 1, 1),
                                LocalDate.of(2019, 1, 2),
                                "old-candidate",
                                200)
                ));

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, created);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.SPLIT
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 6, 30))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
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

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(2, created);
        verify(corporateActionRepository, times(2)).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getRatio() == 0.25
                        && Math.abs(a.getRawDividend() - 0.25) < 0.0001
                        && Math.abs(a.getAdjustedDividend() - 0.25) < 0.0001));
    }

    @Test
    void collectsDividendFactsFromMultipleUsGaapConcepts() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.30, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"}
                  ]
                }
              },
              "CommonStockDividendsPerShareCashPaid": {
                "units": {
                  "USD/shares": [
                    {"val": 0.31, "end": "2024-06-30", "form": "10-Q", "filed": "2024-08-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(2, created);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 5, 12))
                        && Math.abs(a.getRatio() - 0.30) < 0.0001));
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 8, 11))
                        && Math.abs(a.getRatio() - 0.31) < 0.0001));
    }

    @Test
    void keepsLargeSpecialDividendAlongsideRegularQuarterlyDividend() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 1.16, "start": "2024-09-02", "end": "2024-11-24", "form": "10-Q", "filed": "2024-12-10"},
                    {"val": 15.00, "end": "2024-11-24", "form": "8-K", "filed": "2024-12-10"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(123456L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("COST")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("COST", 123456L);

        assertEquals(2, created);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && Math.abs(a.getRatio() - 1.16) < 0.0001));
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && Math.abs(a.getRatio() - 15.00) < 0.0001));
    }

    @Test
    void allowsTwoDividendRowsOnSameExDateWhenAmountsDiffer() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 1.16, "start": "2024-09-02", "end": "2024-11-24", "form": "10-Q", "filed": "2024-12-10"},
                    {"val": 15.00, "end": "2024-11-24", "form": "8-K", "filed": "2024-12-10"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(123456L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("COST")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("COST", 123456L);

        assertEquals(2, created);
        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository, times(2)).save(captor.capture());
        List<CorporateAction> saved = captor.getAllValues().stream()
                .filter(a -> a.getActionType() == ActionType.DIVIDEND)
                .toList();
        assertEquals(2, saved.size());
        assertEquals(saved.get(0).getEffectiveDate(), saved.get(1).getEffectiveDate());
        assertTrue(saved.stream().anyMatch(a -> Math.abs(a.getRatio() - 1.16) < 0.0001));
        assertTrue(saved.stream().anyMatch(a -> Math.abs(a.getRatio() - 15.00) < 0.0001));
    }

    @Test
    void skipsExactDuplicateDividendEventBeforeInsert() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.24, "start": "2024-10-01", "end": "2024-12-28", "form": "10-Q", "filed": "2025-01-31"},
                    {"val": 0.24, "start": "2024-10-01", "end": "2024-12-28", "form": "10-Q", "filed": "2025-01-31"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, created);
        verify(corporateActionRepository, times(1)).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2025, 2, 8))
                        && Math.abs(a.getRatio() - 0.24) < 0.0001));
    }

    @Test
    void continuesWhenDividendInsertHitsUniqueConstraint() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.24, "start": "2024-10-01", "end": "2024-12-28", "form": "10-Q", "filed": "2025-01-31"},
                    {"val": 0.25, "start": "2024-12-29", "end": "2025-03-29", "form": "10-Q", "filed": "2025-05-02"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.findAllByTickerAndActionTypeAndEffectiveDate(anyString(), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(corporateActionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(inv -> inv.getArgument(0));

        int created = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, created);
        verify(corporateActionRepository, times(2)).save(any());
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

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 8, 10))
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

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(4, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 11, 9))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
    }

    @Test
    void skipsImplausibleDerivedQ4FromAnnualTotal() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.22, "start": "2023-10-01", "end": "2023-12-30", "form": "10-Q", "filed": "2024-02-02"},
                    {"val": 0.23, "start": "2023-12-31", "end": "2024-03-30", "form": "10-Q", "filed": "2024-05-03"},
                    {"val": 0.24, "start": "2024-03-31", "end": "2024-06-29", "form": "10-Q", "filed": "2024-08-02"},
                    {"val": 3.50, "start": "2023-10-01", "end": "2024-09-28", "form": "10-K", "filed": "2024-11-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(3, changed);
        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository, times(3)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .filter(a -> a.getActionType() == ActionType.DIVIDEND)
                .allMatch(a -> a.getRatio() < 1.0));
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

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2019, 8, 10))
                        && Math.abs(a.getRawDividend() - 0.77) < 0.0001
                        && Math.abs(a.getAdjustedDividend() - 0.1925) < 0.0001
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
        when(corporateActionFilingDateService.scanDividendRecordDates(320193L))
                .thenReturn(scanResult(java.util.List.of(
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2024, 5, 10), LocalDate.of(2024, 5, 3), "0001"),
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2024, 8, 12), LocalDate.of(2024, 8, 2), "0002")
                )));
        when(corporateActionFilingDateService.computeExDividendDate(LocalDate.of(2024, 5, 10)))
                .thenReturn(LocalDate.of(2024, 5, 9));
        when(corporateActionFilingDateService.computeExDividendDate(LocalDate.of(2024, 8, 12)))
                .thenReturn(LocalDate.of(2024, 8, 12));
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

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
    void fallsBackToPeriodEndWhenNoCredibleRecordDateCandidate() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.25, "end": "2024-03-31", "form": "10-Q", "filed": "2024-05-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionFilingDateService.scanDividendRecordDates(320193L))
                .thenReturn(scanResult(java.util.List.of(
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2023, 1, 10), LocalDate.of(2023, 1, 2), "bad1"),
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 2), "bad2")
                )));
        when(corporateActionFilingDateService.computeExDividendDate(LocalDate.of(2024, 5, 12)))
                .thenReturn(LocalDate.of(2024, 5, 10));
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2024, 5, 10))
                        && Math.abs(a.getRawDividend() - 0.25) < 0.0001
                        && Math.abs(a.getAdjustedDividend() - 0.25) < 0.0001));
        verify(corporateActionFilingDateService).computeExDividendDate(LocalDate.of(2024, 5, 12));
    }

    @Test
    void resolvesAapl2020AugustToExpectedExDate() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.82, "end": "2020-06-27", "form": "10-Q", "filed": "2020-07-31"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionFilingDateService.scanDividendRecordDates(320193L))
                .thenReturn(scanResult(java.util.List.of(
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2020, 8, 10), LocalDate.of(2020, 7, 31), "0001", 120)
                )));
        when(corporateActionFilingDateService.computeExDividendDate(LocalDate.of(2020, 8, 10)))
                .thenReturn(LocalDate.of(2020, 8, 7));
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(1, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2020, 8, 7))
                        && Math.abs(a.getRatio() - 0.82) < 0.0001));
    }

    @Test
    void usesGlobalAssignmentWhenGreedyChoiceWouldStarveLaterQuarter() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.25, "end": "2020-03-31", "form": "10-Q", "filed": "2020-05-01"},
                    {"val": 0.26, "end": "2020-06-30", "form": "10-Q", "filed": "2020-08-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionFilingDateService.scanDividendRecordDates(320193L))
                .thenReturn(scanResult(java.util.List.of(
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2020, 6, 15), LocalDate.of(2020, 5, 8), "alt-low", 0),
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2020, 7, 10), LocalDate.of(2020, 7, 2), "must-have", 600)
                )));
        when(corporateActionFilingDateService.computeExDividendDate(LocalDate.of(2020, 6, 15)))
                .thenReturn(LocalDate.of(2020, 6, 12));
        when(corporateActionFilingDateService.computeExDividendDate(LocalDate.of(2020, 7, 10)))
                .thenReturn(LocalDate.of(2020, 7, 9));
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.Collections.emptyList());

        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(2, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2020, 6, 12))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2020, 7, 9))
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
        CorporateAction existingSplit = new CorporateAction();
        existingSplit.setTicker("AAPL");
        existingSplit.setActionType(ActionType.SPLIT);
        existingSplit.setEffectiveDate(LocalDate.of(2024, 6, 30));
        existingSplit.setRatio(0.25);
        existingSplit.setConfidenceScore(40.0);
        existingSplit.setExDateSource(CorporateAction.EX_DATE_SOURCE_SHARE_FACT);
        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(List.of(existingSplit));

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
    void reconcilesChronologicallyWhenExDatesCrossYearBoundary() throws Exception {
        String json = """
        {
          "facts": {
            "dei": {},
            "us-gaap": {
              "CommonStockDividendsPerShareDeclared": {
                "units": {
                  "USD/shares": [
                    {"val": 0.25, "end": "2024-12-31", "form": "10-Q", "filed": "2025-02-01"},
                    {"val": 0.26, "end": "2025-03-31", "form": "10-Q", "filed": "2025-05-01"}
                  ]
                }
              }
            }
          }
        }
        """;
        when(webService.fetchFinancials(320193L)).thenReturn(json);
        when(corporateActionFilingDateService.scanDividendRecordDates(320193L))
                .thenReturn(scanResult(java.util.List.of(
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2025, 1, 13), LocalDate.of(2025, 1, 10), "r1", 100),
                        new CorporateActionFilingDateService.RecordDateCandidate(LocalDate.of(2025, 4, 14), LocalDate.of(2025, 4, 10), "r2", 100)
                )));

        CorporateAction old1 = new CorporateAction();
        old1.setTicker("AAPL");
        old1.setActionType(ActionType.DIVIDEND);
        old1.setEffectiveDate(LocalDate.of(2025, 1, 2));
        old1.setRatio(0.20);
        old1.setRawDividend(0.20);
        old1.setAdjustedDividend(0.20);

        CorporateAction old2 = new CorporateAction();
        old2.setTicker("AAPL");
        old2.setActionType(ActionType.DIVIDEND);
        old2.setEffectiveDate(LocalDate.of(2025, 4, 2));
        old2.setRatio(0.20);
        old2.setRawDividend(0.20);
        old2.setAdjustedDividend(0.20);

        when(corporateActionRepository.findByTicker("AAPL")).thenReturn(java.util.List.of(old1, old2));
        when(corporateActionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int changed = service.detectAndPersist("AAPL", 320193L);

        assertEquals(2, changed);
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2025, 1, 13))
                        && Math.abs(a.getRatio() - 0.25) < 0.0001));
        verify(corporateActionRepository).save(argThat(a ->
                a.getActionType() == ActionType.DIVIDEND
                        && a.getEffectiveDate().equals(LocalDate.of(2025, 4, 14))
                        && Math.abs(a.getRatio() - 0.26) < 0.0001));
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

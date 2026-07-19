package com.fattorestreet.sec_api.corporateaction;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.corporateaction.support.FilingExtractionStore;
import com.fattorestreet.sec_api.model.FilingExtraction;
import com.fattorestreet.sec_api.repository.FilingExtractionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CorporateActionFilingDateServiceTest {

    @Mock
    private WebService webService;

    @Mock
    private FilingExtractionRepository filingExtractionRepository;

    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    private CorporateActionFilingDateService service;

    @BeforeEach
    void setUp() {
        lenient().when(filingExtractionRepository.findByCik(any())).thenReturn(Collections.emptyList());
        lenient().when(filingExtractionRepository.save(any(FilingExtraction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        EdgarFilingDiscoveryService filingDiscoveryService = new EdgarFilingDiscoveryService(webService, mapper);
        FilingExtractionStore extractionStore = new FilingExtractionStore(filingExtractionRepository, mapper);
        service = new CorporateActionFilingDateService(webService, filingDiscoveryService, extractionStore, mapper);
    }

    @Test
    void scanDividendRecordDates_supportsMonthNameWithoutComma() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-31"]
            }
          }
        }
        """;
        String filing = """
        <html>
          <body>
            The Board declared a cash dividend payable on August 13 2020
            to shareholders of record at the close of business on Aug 10 2020.
          </body>
        </html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.RecordDateCandidate> out = service.scanDividendRecordDates(320193L).candidates();

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 10), out.get(0).recordDate());
        assertTrue(out.get(0).confidenceScore() > 0);
    }

    @Test
    void scanDividendRecordDates_scansExhibitsAndParsesNumericDate() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000063"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-08-01"]
            }
          }
        }
        """;
        String primary = """
        <html><body><a href="ex99_1.htm">Exhibit 99.1</a></body></html>
        """;
        String exhibit = """
        <html><body>Dividend record date will be 08/10/2020.</body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000063", "d8k.htm")).thenReturn(primary);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000063", "ex99_1.htm")).thenReturn(exhibit);

        List<CorporateActionFilingDateService.RecordDateCandidate> out = service.scanDividendRecordDates(320193L).candidates();

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 10), out.get(0).recordDate());
    }

    @Test
    void computeExDividendDate_handlesSettlementTransitionAndJuneteenthEra() {
        assertEquals(LocalDate.of(2020, 6, 18),
                service.computeExDividendDate(LocalDate.of(2020, 6, 19)));
        assertEquals(LocalDate.of(2020, 8, 7),
                service.computeExDividendDate(LocalDate.of(2020, 8, 10)));
        assertEquals(LocalDate.of(2024, 8, 12),
                service.computeExDividendDate(LocalDate.of(2024, 8, 12)));
    }

    @Test
    void computeExDividendDate_tPlusThreeEraUsesTwoBusinessDaysBeforeRecord() {
        // Before 2017-09-05 (T+3 settlement), the ex-date was two business days before record.
        assertEquals(LocalDate.of(2017, 6, 13),
                service.computeExDividendDate(LocalDate.of(2017, 6, 15)));
        // Weekend-spanning: record Monday 2016-08-15 -> ex Thursday 2016-08-11.
        assertEquals(LocalDate.of(2016, 8, 11),
                service.computeExDividendDate(LocalDate.of(2016, 8, 15)));
        // First day of the T+2 era keeps the one-business-day rule.
        assertEquals(LocalDate.of(2017, 9, 6),
                service.computeExDividendDate(LocalDate.of(2017, 9, 7)));
    }

    @Test
    void scanDividendRecordDates_reusesPersistedExtractionWithoutFetching() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-31"]
            }
          }
        }
        """;
        FilingExtraction stored = new FilingExtraction();
        stored.setCik(320193L);
        stored.setAccessionNumber("0000320193-20-000062");
        stored.setFilingDate(LocalDate.of(2020, 7, 31));
        stored.setFormType("8-K");
        stored.setDividendExtractorVersion(FilingExtractionStore.DIVIDEND_EXTRACTOR_VERSION);
        stored.setBestRecordDate(LocalDate.of(2020, 8, 10));
        stored.setBestRecordDateScore(155);
        stored.setExDateCandidatesJson("[{\"date\":\"2020-08-07\",\"score\":135}]");
        stored.setDeclarationsJson(
                "[{\"amountPerShare\":0.82,\"recordDate\":\"2020-08-10\",\"payableDate\":\"2020-08-13\",\"confidenceScore\":95}]");

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(filingExtractionRepository.findByCik(320193L)).thenReturn(List.of(stored));

        CorporateActionFilingDateService.RecordDateScanResult scan = service.scanDividendRecordDates(320193L);

        assertEquals(1, scan.candidates().size());
        assertEquals(LocalDate.of(2020, 8, 10), scan.candidates().get(0).recordDate());
        assertEquals(155, scan.candidates().get(0).confidenceScore());
        assertEquals(1, scan.exDividendDirectCandidates().size());
        assertEquals(LocalDate.of(2020, 8, 7), scan.exDividendDirectCandidates().get(0).exDividendDate());
        assertEquals(1, scan.declarations().size());
        assertEquals(0.82, scan.declarations().get(0).amountPerShare());
        assertEquals("0000320193-20-000062", scan.declarations().get(0).accessionNumber());
        verify(webService, times(0)).fetchFilingDocument(any(), any(), any());
    }

    @Test
    void scanDividendRecordDates_staleExtractorVersionTriggersRefetchAndRepersist() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-31"]
            }
          }
        }
        """;
        String filing = """
        <html><body>
        The Board declared a cash dividend payable on August 13 2020
        to shareholders of record at the close of business on Aug 10 2020.
        </body></html>
        """;
        FilingExtraction stale = new FilingExtraction();
        stale.setCik(320193L);
        stale.setAccessionNumber("0000320193-20-000062");
        stale.setDividendExtractorVersion(FilingExtractionStore.DIVIDEND_EXTRACTOR_VERSION - 1);
        stale.setBestRecordDate(LocalDate.of(1999, 1, 1));

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(filingExtractionRepository.findByCik(320193L)).thenReturn(List.of(stale));
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k.htm")).thenReturn(filing);

        CorporateActionFilingDateService.RecordDateScanResult scan = service.scanDividendRecordDates(320193L);

        assertEquals(1, scan.candidates().size());
        assertEquals(LocalDate.of(2020, 8, 10), scan.candidates().get(0).recordDate());
        verify(filingExtractionRepository).save(any(FilingExtraction.class));
        assertEquals(FilingExtractionStore.DIVIDEND_EXTRACTOR_VERSION, stale.getDividendExtractorVersion());
        assertEquals(LocalDate.of(2020, 8, 10), stale.getBestRecordDate());
    }

    @Test
    void fetchSplitEffectiveDates_extractsSplitAdjustedTradingDate() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-30"]
            },
            "files": []
          }
        }
        """;
        String filing = """
        <html><body>
        Trading on a split-adjusted basis will begin on August 31, 2020.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 31), out.get(0).effectiveDate());
        assertTrue(out.get(0).confidenceScore() > 0);
    }

    @Test
    void fetchSplitEffectiveDates_prefersSplitAdjustedTradingDateOverEffectiveDate() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-30"]
            },
            "files": []
          }
        }
        """;
        String filing = """
        <html><body>
        Apple's Board of Directors approved a four-for-one stock split, effective August 24, 2020.
        Trading on a split-adjusted basis will begin on August 31, 2020.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 31), out.get(0).effectiveDate());
    }

    @Test
    void fetchSplitEffectiveDates_prefersSplitAdjustedTradingDateOverDistributionDate() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-30"]
            },
            "files": []
          }
        }
        """;
        String filing = """
        <html><body>
        Apple's Board of Directors approved a four-for-one stock split with distribution date of August 24, 2020.
        Trading on a split-adjusted basis will begin on August 31, 2020.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 31), out.get(0).effectiveDate());
    }

    @Test
    void fetchSplitEffectiveDates_parsesStartOfTradingSplitAdjustedPhrase() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-30"]
            },
            "files": []
          }
        }
        """;
        String filing = """
        <html><body>
        The stock split becomes effective for shareholders at the close of business on August 24, 2020.
        Trading in Apple's common stock will begin on a split-adjusted basis at the start of trading on August 31, 2020.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 31), out.get(0).effectiveDate());
    }

    @Test
    void fetchSplitEffectiveDates_sentencePassPrefersTradingStartOverEffectiveDate() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-20-000062"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2020-07-30"]
            },
            "files": []
          }
        }
        """;
        String filing = """
        <html><body>
        The stock split will be effective on August 24, 2020.
        Trading in split shares will begin on August 31, 2020.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 31), out.get(0).effectiveDate());
    }

    @Test
    void fetchSplitEffectiveDates_keepsDeterministicWinnerWhenDateDedupesAcrossFilings() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K", "8-K"],
              "accessionNumber": ["0000320193-20-000099", "0000320193-20-000062"],
              "primaryDocument": ["d8k_newer.htm", "d8k_older.htm"],
              "filingDate": ["2020-08-01", "2020-07-30"]
            },
            "files": []
          }
        }
        """;
        String filing = """
        <html><body>
        Trading on a split-adjusted basis will begin on August 31, 2020.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000099", "d8k_newer.htm")).thenReturn(filing);
        when(webService.fetchFilingDocument(320193L, "0000320193-20-000062", "d8k_older.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 31), out.get(0).effectiveDate());
        assertEquals("0000320193-20-000062", out.get(0).accessionNumber());
    }

    @Test
    void fetchSplitEffectiveDates_scansArchivedSubmissionFiles() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": [],
              "accessionNumber": [],
              "primaryDocument": [],
              "filingDate": []
            },
            "files": [{"name":"submissions-001.json"}]
          }
        }
        """;
        String archived = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-14-000070"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2014-04-23"]
            }
          }
        }
        """;
        String filing = """
        <html><body>
        Apple's Board of Directors approved a seven-for-one stock split, effective June 9, 2014.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchSubmissionsFile(320193L, "submissions-001.json")).thenReturn(archived);
        when(webService.fetchFilingDocument(320193L, "0000320193-14-000070", "d8k.htm")).thenReturn(filing);

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2014, 6, 9), out.get(0).effectiveDate());
    }

    @Test
    void fetchSplitEffectiveDates_limitsArchivedSubmissionAttemptsEvenOnFailures() {
        String files = IntStream.range(0, 60)
                .mapToObj(i -> "{\"name\":\"submissions-" + i + ".json\"}")
                .collect(Collectors.joining(","));
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": [],
              "accessionNumber": [],
              "primaryDocument": [],
              "filingDate": []
            },
            "files": [%s]
          }
        }
        """.formatted(files);

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchSubmissionsFile(org.mockito.ArgumentMatchers.eq(320193L), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("unavailable"));

        List<CorporateActionFilingDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertTrue(out.isEmpty());
        verify(webService, times(48)).fetchSubmissionsFile(org.mockito.ArgumentMatchers.eq(320193L), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void scanDividendRecordDates_collectsDirectExDividendDates() {
        String submissions = """
        {
          "filings": {
            "recent": {
              "form": ["8-K"],
              "accessionNumber": ["0000320193-21-000099"],
              "primaryDocument": ["d8k.htm"],
              "filingDate": ["2021-04-28"]
            }
          }
        }
        """;
        String filing = """
        <html><body>
        The Board declared a dividend with an ex-dividend date of May 10, 2021.
        </body></html>
        """;

        when(webService.fetchSubmissions(320193L)).thenReturn(submissions);
        when(webService.fetchFilingDocument(320193L, "0000320193-21-000099", "d8k.htm")).thenReturn(filing);

        CorporateActionFilingDateService.RecordDateScanResult scan = service.scanDividendRecordDates(320193L);

        assertEquals(1, scan.exDividendDirectCandidates().size());
        assertEquals(LocalDate.of(2021, 5, 10), scan.exDividendDirectCandidates().get(0).exDividendDate());
    }
}


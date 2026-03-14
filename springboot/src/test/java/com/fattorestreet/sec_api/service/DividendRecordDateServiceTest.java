package com.fattorestreet.sec_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DividendRecordDateServiceTest {

    @Mock
    private WebService webService;

    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @InjectMocks
    private DividendRecordDateService service;

    @Test
    void fetchDividendRecordDates_supportsMonthNameWithoutComma() {
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

        List<DividendRecordDateService.RecordDateCandidate> out = service.fetchDividendRecordDates(320193L);

        assertEquals(1, out.size());
        assertEquals(LocalDate.of(2020, 8, 10), out.get(0).recordDate());
        assertTrue(out.get(0).confidenceScore() > 0);
    }

    @Test
    void fetchDividendRecordDates_scansExhibitsAndParsesNumericDate() {
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

        List<DividendRecordDateService.RecordDateCandidate> out = service.fetchDividendRecordDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

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

        List<DividendRecordDateService.SplitDateCandidate> out = service.fetchSplitEffectiveDates(320193L);

        assertTrue(out.isEmpty());
        verify(webService, times(48)).fetchSubmissionsFile(org.mockito.ArgumentMatchers.eq(320193L), org.mockito.ArgumentMatchers.anyString());
    }
}

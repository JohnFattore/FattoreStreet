package com.fattorestreet.sec_api.corporateaction;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.corporateaction.EdgarFilingDiscoveryService.FilingMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EdgarFilingDiscoveryServiceTest {

    private static final long CIK = 320193L;

    @Mock
    private WebService webService;

    private EdgarFilingDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new EdgarFilingDiscoveryService(webService, new ObjectMapper());
    }

    private static String recentSubmissions(String filesArray) {
        return """
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0000320193-24-000123", "0000320193-24-000100"],
                      "form": ["10-k", "8-K"],
                      "primaryDocument": ["aapl-10k.htm", "aapl-8k.htm"],
                      "filingDate": ["2024-11-01", "2024-08-01"]
                    },
                    "files": %s
                  }
                }
                """.formatted(filesArray);
    }

    @Test
    void discoversRecentFilingsSortedByDateDescWithNormalizedForms() {
        when(webService.fetchSubmissions(CIK)).thenReturn(recentSubmissions("[]"));

        List<FilingMeta> filings = service.discoverFilings(CIK);

        assertEquals(2, filings.size());
        assertEquals("0000320193-24-000123", filings.get(0).accessionNumber());
        assertEquals("10-K", filings.get(0).formType());
        assertEquals("aapl-10k.htm", filings.get(0).primaryDocument());
        assertEquals(LocalDate.of(2024, 11, 1), filings.get(0).filingDate());
        assertEquals("8-K", filings.get(1).formType());
    }

    @Test
    void mergesArchiveBundlesAndDeduplicatesByAccession() {
        when(webService.fetchSubmissions(CIK)).thenReturn(
                recentSubmissions("[{\"name\": \"submissions-001.json\"}]"));
        // The archive repeats an accession from recent (kept once) and adds an older one.
        when(webService.fetchSubmissionsFile(CIK, "submissions-001.json")).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0000320193-24-000100", "0000320193-20-000050"],
                      "form": ["8-K", "10-Q"],
                      "primaryDocument": ["dup.htm", "aapl-10q.htm"],
                      "filingDate": ["2024-08-01", "2020-05-01"]
                    }
                  }
                }
                """);

        List<FilingMeta> filings = service.discoverFilings(CIK);

        assertEquals(3, filings.size());
        // First occurrence wins for the duplicated accession.
        FilingMeta dup = filings.stream()
                .filter(f -> f.accessionNumber().equals("0000320193-24-000100"))
                .findFirst().orElseThrow();
        assertEquals("aapl-8k.htm", dup.primaryDocument());
        assertEquals("0000320193-20-000050", filings.get(2).accessionNumber());
    }

    @Test
    void honorsMaxSubmissionFilesToScan() {
        when(webService.fetchSubmissions(CIK)).thenReturn(recentSubmissions(
                "[{\"name\": \"a.json\"}, {\"name\": \"b.json\"}, {\"name\": \"c.json\"}]"));
        when(webService.fetchSubmissionsFile(eq(CIK), anyString())).thenReturn("{}");

        service.discoverFilings(CIK, 2);

        verify(webService).fetchSubmissionsFile(CIK, "a.json");
        verify(webService).fetchSubmissionsFile(CIK, "b.json");
        verify(webService, never()).fetchSubmissionsFile(CIK, "c.json");
    }

    @Test
    void blankArchiveNamesAreSkippedWithoutConsumingScanBudget() {
        when(webService.fetchSubmissions(CIK)).thenReturn(recentSubmissions(
                "[{\"name\": \"\"}, {\"other\": 1}, {\"name\": \"real.json\"}]"));
        when(webService.fetchSubmissionsFile(CIK, "real.json")).thenReturn("{}");

        service.discoverFilings(CIK, 1);

        verify(webService).fetchSubmissionsFile(CIK, "real.json");
    }

    @Test
    void archiveFetchFailureKeepsOtherFilings() {
        when(webService.fetchSubmissions(CIK)).thenReturn(recentSubmissions(
                "[{\"name\": \"broken.json\"}]"));
        when(webService.fetchSubmissionsFile(CIK, "broken.json"))
                .thenThrow(new RuntimeException("unavailable"));

        List<FilingMeta> filings = service.discoverFilings(CIK);

        assertEquals(2, filings.size());
    }

    @Test
    void malformedRecentPayloadYieldsEmptyList() {
        when(webService.fetchSubmissions(CIK)).thenReturn("this is not json{{");

        assertTrue(service.discoverFilings(CIK).isEmpty());
    }

    @Test
    void nullRecentPayloadYieldsEmptyList() {
        when(webService.fetchSubmissions(CIK)).thenReturn(null);

        assertTrue(service.discoverFilings(CIK).isEmpty());
    }

    @Test
    void rowsMissingAccessionOrPrimaryDocumentAreSkipped() {
        when(webService.fetchSubmissions(CIK)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["", "0000320193-24-000200", "0000320193-24-000300"],
                      "form": ["8-K", "8-K", "8-K"],
                      "primaryDocument": ["a.htm", "", "c.htm"],
                      "filingDate": ["2024-01-01", "2024-02-01", "2024-03-01"]
                    }
                  }
                }
                """);

        List<FilingMeta> filings = service.discoverFilings(CIK);

        assertEquals(1, filings.size());
        assertEquals("0000320193-24-000300", filings.get(0).accessionNumber());
    }

    @Test
    void unparseableFilingDatesSortLast() {
        when(webService.fetchSubmissions(CIK)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["acc-no-date", "acc-with-date"],
                      "form": ["8-K", "8-K"],
                      "primaryDocument": ["a.htm", "b.htm"],
                      "filingDate": ["not-a-date", "2024-03-01"]
                    }
                  }
                }
                """);

        List<FilingMeta> filings = service.discoverFilings(CIK);

        assertEquals("acc-with-date", filings.get(0).accessionNumber());
        assertNull(filings.get(1).filingDate());
    }

    @Test
    void parsesFilingsArrayShape() {
        when(webService.fetchSubmissions(CIK)).thenReturn("""
                {
                  "filings": [
                    {
                      "accessionNumber": "acc-array-1",
                      "form": "10-q",
                      "primaryDocument": "doc.htm",
                      "filingDate": "2023-06-30"
                    }
                  ]
                }
                """);

        List<FilingMeta> filings = service.discoverFilings(CIK);

        assertEquals(1, filings.size());
        assertEquals("10-Q", filings.get(0).formType());
        assertEquals(LocalDate.of(2023, 6, 30), filings.get(0).filingDate());
    }
}

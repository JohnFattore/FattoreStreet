package com.fattorestreet.sec_api.service;

import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtfCorporateActionServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private CorporateActionRepository corporateActionRepository;
    @Mock
    private WebService webService;
    @Mock
    private DividendRecordDateService dividendRecordDateService;

    @Test
    void detectAndPersist_with497RecordDate_persistsDividendActionAndDiagnostics() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassContractId("C000001");
        listing.setSecClassTicker("VOO");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(dividendRecordDateService.computeExDividendDate(LocalDate.of(2025, 2, 10)))
                .thenReturn(LocalDate.of(2025, 2, 7));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000001"],
                      "form": ["497"],
                      "primaryDocument": ["fund497.htm"],
                      "filingDate": ["2025-02-01"]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000001")).thenReturn("""
                {"directory":{"item":[{"name":"fund497.htm"},{"name":"ex99dividend.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000001", "fund497.htm")).thenReturn(
                "VOO C000001 S000001 announced a dividend of $0.50 per share. " +
                "Record date February 10, 2025. Payable date March 1, 2025."
        );
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000001", "ex99dividend.htm")).thenReturn(
                "Distribution notice for VOO class investors."
        );
        when(corporateActionRepository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO",
                CorporateAction.ActionType.DIVIDEND,
                LocalDate.of(2025, 2, 7),
                0.50,
                CorporateAction.SourceType.SEC_ETF_FILING
        )).thenReturn(Optional.empty());

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(1, report.saved());
        assertEquals(1, report.filingsConsidered());
        assertEquals(2, report.filingsFetched());
        assertEquals(1, report.identityMatched());
        assertEquals(1, report.amountExtracted());
        assertEquals(1, report.dateExtracted());
        assertTrue(report.sampleCreated().size() >= 1);

        ArgumentCaptor<CorporateAction> actionCaptor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(actionCaptor.capture());
        CorporateAction saved = actionCaptor.getValue();
        assertEquals("VOO", saved.getTicker());
        assertEquals(CorporateAction.SourceType.SEC_ETF_FILING, saved.getSourceType());
        assertEquals("497", saved.getFormType());
        assertEquals("0001234567-25-000001", saved.getAccessionNumber());
        assertEquals(LocalDate.of(2025, 2, 10), saved.getRecordDate());
        assertEquals(LocalDate.of(2025, 3, 1), saved.getPayDate());
        assertEquals(LocalDate.of(2025, 2, 7), saved.getEffectiveDate());
        assertEquals(0.50, saved.getAdjustedDividend());
        assertEquals(91.0, saved.getConfidenceScore());
    }

    @Test
    void detectAndPersist_readsExhibitDocumentWhenPrimaryDoesNotContainDividend() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassTicker("VOO");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000002"],
                      "form": ["485BPOS"],
                      "primaryDocument": ["fund485.htm"],
                      "filingDate": ["2025-03-20"]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000002")).thenReturn("""
                {"directory":{"item":[{"name":"fund485.htm"},{"name":"ex99_distribution.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000002", "fund485.htm")).thenReturn(
                "Prospectus update with no explicit dividend values."
        );
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000002", "ex99_distribution.htm")).thenReturn(
                "VOO ETF Shares cash distribution of $0.40 per share. Ex-dividend date 2025-04-01."
        );
        when(corporateActionRepository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO",
                CorporateAction.ActionType.DIVIDEND,
                LocalDate.of(2025, 4, 1),
                0.40,
                CorporateAction.SourceType.SEC_ETF_FILING
        )).thenReturn(Optional.empty());

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(1, report.saved());

        ArgumentCaptor<CorporateAction> actionCaptor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(actionCaptor.capture());
        CorporateAction saved = actionCaptor.getValue();
        assertEquals("485BPOS", saved.getFormType());
        assertEquals(LocalDate.of(2025, 4, 1), saved.getEffectiveDate());
        assertEquals(null, saved.getRecordDate());
        assertEquals(95.0, saved.getConfidenceScore());
    }

    @Test
    void detectAndPersist_withNcsrMissingDate_skipsWithReason() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassContractId("C000001");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000003"],
                      "form": ["N-CSR"],
                      "primaryDocument": ["ncsr.htm"],
                      "filingDate": [""]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000003")).thenReturn("""
                {"directory":{"item":[{"name":"ncsr.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000003", "ncsr.htm")).thenReturn(
                "VOO C000001 S000001 announced a dividend of $0.33 per share."
        );

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(0, report.saved());
        assertEquals(1, report.skipReasons().getOrDefault("date_missing", 0));
        verify(corporateActionRepository, never()).save(org.mockito.ArgumentMatchers.any(CorporateAction.class));
    }

    @Test
    void detectAndPersist_withIdentityMismatch_skipsWithReason() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassContractId("C000001");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000004"],
                      "form": ["497"],
                      "primaryDocument": ["fund497.htm"],
                      "filingDate": ["2025-03-20"]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000004")).thenReturn("""
                {"directory":{"item":[{"name":"fund497.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000004", "fund497.htm")).thenReturn(
                "Another fund announced a dividend of $0.25 per share. Ex-dividend date 2025-04-01."
        );

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(0, report.saved());
        assertEquals(1, report.skipReasons().getOrDefault("identity_mismatch", 0));
        verify(corporateActionRepository, never()).save(org.mockito.ArgumentMatchers.any(CorporateAction.class));
    }

    @Test
    void detectAndPersist_duplicateExistingAction_skipsInsertWithReason() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassContractId("C000001");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000005"],
                      "form": ["497"],
                      "primaryDocument": ["fund497.htm"],
                      "filingDate": ["2025-03-20"]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000005")).thenReturn("""
                {"directory":{"item":[{"name":"fund497.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000005", "fund497.htm")).thenReturn(
                "VOO C000001 S000001 announced a dividend of $0.50 per share. Ex-dividend date 2025-04-01."
        );
        when(corporateActionRepository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO",
                CorporateAction.ActionType.DIVIDEND,
                LocalDate.of(2025, 4, 1),
                0.50,
                CorporateAction.SourceType.SEC_ETF_FILING
        )).thenReturn(Optional.of(new CorporateAction()));

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(0, report.saved());
        assertEquals(1, report.skipReasons().getOrDefault("duplicate", 0));
        verify(corporateActionRepository, never()).save(org.mockito.ArgumentMatchers.any(CorporateAction.class));
    }

    @Test
    void detectAndPersist_withoutSeriesClassIdentity_skips() {
        Listing listing = new Listing();
        listing.setTicker("SPY");
        when(listingRepository.findByTicker("SPY")).thenReturn(Optional.of(listing));

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("SPY", 123456L, 70);
        assertEquals(0, report.saved());
        assertEquals(1, report.skipReasons().getOrDefault("identity_missing", 0));
        verify(corporateActionRepository, never()).save(org.mockito.ArgumentMatchers.any(CorporateAction.class));
    }

    @Test
    void detectAndPersist_withTableLikeAmountAndLabeledDates_extractsSuccessfully() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassContractId("C000001");
        listing.setSecClassTicker("VOO");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000006"],
                      "form": ["497"],
                      "primaryDocument": ["fund497.htm"],
                      "filingDate": ["2025-03-20"]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000006")).thenReturn("""
                {"directory":{"item":[{"name":"fund497.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000006", "fund497.htm")).thenReturn(
                """
                VOO C000001 S000001
                Distribution Schedule
                Cash distribution ..................... $0.34
                Ex-dividend date: 04/01/2025
                Record date: 04/02/2025
                Payable date: 04/05/2025
                """
        );
        when(corporateActionRepository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO",
                CorporateAction.ActionType.DIVIDEND,
                LocalDate.of(2025, 4, 1),
                0.34,
                CorporateAction.SourceType.SEC_ETF_FILING
        )).thenReturn(Optional.empty());

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(1, report.saved());
        Map<String, Object> row = report.sampleCreated().get(0);
        assertEquals("0001234567-25-000006", row.get("accession"));
        assertEquals("2025-04-01", row.get("effectiveDate"));
    }

    @Test
    void detectAndPersist_withAlternateDateFormats_extractsSuccessfully() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassContractId("C000001");
        listing.setSecClassTicker("VOO");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000007"],
                      "form": ["497"],
                      "primaryDocument": ["fund497.htm"],
                      "filingDate": ["2025-03-20"]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000007")).thenReturn("""
                {"directory":{"item":[{"name":"fund497.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000007", "fund497.htm")).thenReturn(
                """
                <table>
                  <tr><td>Ex Dt</td><td>04-01-2025</td></tr>
                  <tr><td>Record Dt</td><td>04/02/25</td></tr>
                  <tr><td>Pay Dt</td><td>2025/04/05</td></tr>
                </table>
                VOO C000001 S000001 cash distribution $0.36 per share.
                """
        );
        when(corporateActionRepository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO",
                CorporateAction.ActionType.DIVIDEND,
                LocalDate.of(2025, 4, 1),
                0.36,
                CorporateAction.SourceType.SEC_ETF_FILING
        )).thenReturn(Optional.empty());

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(1, report.saved());
        assertEquals(1, report.dateExtracted());
    }

    @Test
    void detectAndPersist_withPayDateFallback_canSkipByConfidence() {
        Listing listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000001");
        listing.setSecClassContractId("C000001");
        listing.setSecClassTicker("VOO");
        when(listingRepository.findByTicker("VOO")).thenReturn(Optional.of(listing));

        when(webService.fetchSubmissions(123456L)).thenReturn("""
                {
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001234567-25-000008"],
                      "form": ["497"],
                      "primaryDocument": ["fund497.htm"],
                      "filingDate": ["2025-03-20"]
                    },
                    "files": []
                  }
                }
                """);
        when(webService.fetchFilingIndex(123456L, "0001234567-25-000008")).thenReturn("""
                {"directory":{"item":[{"name":"fund497.htm"}]}}
                """);
        when(webService.fetchFilingDocument(123456L, "0001234567-25-000008", "fund497.htm")).thenReturn(
                "VOO C000001 S000001 announced a dividend of $0.22 per share. Payable date Mar. 21, 2025."
        );

        EtfCorporateActionService service = new EtfCorporateActionService(
                listingRepository,
                corporateActionRepository,
                webService,
                dividendRecordDateService,
                new ObjectMapper()
        );

        EtfCorporateActionService.EtfDetectionReport report = service.detectAndPersist("VOO", 123456L, 70);
        assertEquals(0, report.saved());
        assertEquals(1, report.skipReasons().getOrDefault("below_confidence", 0));
    }
}

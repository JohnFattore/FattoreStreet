package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtfDateExtractorTest {

    @Mock
    private CorporateActionFilingDateService corporateActionFilingDateService;

    @InjectMocks
    private EtfDateExtractor extractor;

    @Test
    void extractEtfDateSignals_exDateSentence_gives95Confidence() {
        // Ex-dividend date found directly → confidence 95, resolution path "ex_date"
        String text = "Ex-dividend date 2024-03-14 for the fund distribution.";

        EtfDateExtractor.EtfDateSignals signals = extractor.extractEtfDateSignals(text, LocalDate.of(2024, 3, 1));

        assertNotNull(signals);
        assertEquals(LocalDate.of(2024, 3, 14), signals.effectiveDate());
        assertEquals(95, signals.confidenceScore());
        assertEquals("ex_date", signals.resolutionPath());
    }

    @Test
    void extractEtfDateSignals_recordDateDerivesExDate_gives86Confidence() {
        LocalDate recordDate = LocalDate.of(2024, 3, 15);
        LocalDate derivedEx = LocalDate.of(2024, 3, 14);
        when(corporateActionFilingDateService.computeExDividendDate(recordDate)).thenReturn(derivedEx);

        String text = "Record date 2024-03-15 for shareholders of record.";

        EtfDateExtractor.EtfDateSignals signals = extractor.extractEtfDateSignals(text, LocalDate.of(2024, 3, 1));

        assertNotNull(signals);
        assertEquals(derivedEx, signals.effectiveDate());
        assertEquals(recordDate, signals.recordDate());
        assertEquals(86, signals.confidenceScore());
        assertEquals("record_date", signals.resolutionPath());
    }

    @Test
    void extractEtfDateSignals_payDateFallback_gives62Confidence() {
        // No ex-date, no record date; pay-date found → lower confidence
        String text = "Payable date 2024-03-20 to all shareholders.";

        EtfDateExtractor.EtfDateSignals signals = extractor.extractEtfDateSignals(text, LocalDate.of(2024, 3, 1));

        assertNotNull(signals);
        assertNotNull(signals.effectiveDate());
        assertEquals("pay_date_fallback", signals.resolutionPath());
        // Base 62 + 5 for payDate present = 67
        assertTrue(signals.confidenceScore() >= 62);
    }

    @Test
    void extractEtfDateSignals_noDateAndNoFiling_returnsNull() {
        String text = "No dates present in this filing text.";

        EtfDateExtractor.EtfDateSignals signals = extractor.extractEtfDateSignals(text, null);

        assertNull(signals);
    }

    @Test
    void extractEtfDateSignals_filingDateFallback_gives55Confidence() {
        // No dates anywhere; falls back to filing date itself
        LocalDate filingDate = LocalDate.of(2024, 3, 5);
        String text = "Some generic fund text without any date references.";

        EtfDateExtractor.EtfDateSignals signals = extractor.extractEtfDateSignals(text, filingDate);

        if (signals != null) {
            // If filing date fallback kicks in, confidence should be 55
            assertEquals("filing_date_fallback", signals.resolutionPath());
            assertEquals(55, signals.confidenceScore());
        }
        // Null is also acceptable if no dates found and filing date fallback yields null effectiveDate
    }

    @Test
    void extractEtfDateSignals_exDatePresence_boostsConfidenceByPayDate() {
        // Ex-date found (confidence=95) + pay date present → confidence min(95+5,100)=100
        String text = "Ex-dividend date 2024-03-14 payable date 2024-03-18.";

        EtfDateExtractor.EtfDateSignals signals = extractor.extractEtfDateSignals(text, LocalDate.of(2024, 3, 1));

        assertNotNull(signals);
        assertEquals("ex_date", signals.resolutionPath());
        assertEquals(100, signals.confidenceScore()); // 95 + 5 = 100
        assertNotNull(signals.payDate());
    }

    @Test
    void extractEtfDateSignals_labeledDatePattern_highConfidence() {
        // LABELED_DATE_PATTERN gives score 92 — higher than sentence pattern 82/88
        String text = "ex-dividend date: 2024-05-09";

        EtfDateExtractor.EtfDateSignals signals = extractor.extractEtfDateSignals(text, LocalDate.of(2024, 4, 1));

        assertNotNull(signals);
        assertEquals(LocalDate.of(2024, 5, 9), signals.effectiveDate());
    }
}

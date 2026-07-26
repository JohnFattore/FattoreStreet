package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DividendDeclarationTupleExtractorTest {

    private static final LocalDate FILING_DATE = LocalDate.of(2025, 2, 3);
    private static final String ACCESSION = "0000320193-25-000001";

    private final DividendDeclarationTupleExtractor extractor = new DividendDeclarationTupleExtractor();

    private List<DividendDeclarationTupleExtractor.DividendDeclaration> extract(String text) {
        return extractor.extract(text, FILING_DATE, ACCESSION);
    }

    @Test
    void extractsCanonicalPressReleaseParagraph() {
        String text = """
                <html><body><p>The Board of Directors declared a quarterly cash dividend of
                $0.24 per share of the Company's common stock, payable on March 27, 2025 to
                shareholders of record as of the close of business on March 10, 2025.</p></body></html>
                """;

        List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations = extract(text);

        assertEquals(1, declarations.size());
        DividendDeclarationTupleExtractor.DividendDeclaration d = declarations.get(0);
        assertEquals(0.24, d.amountPerShare());
        assertEquals(LocalDate.of(2025, 3, 10), d.recordDate());
        assertEquals(LocalDate.of(2025, 3, 27), d.payableDate());
        assertEquals(FILING_DATE, d.filingDate());
        assertEquals(ACCESSION, d.accessionNumber());
        assertTrue(d.confidenceScore() >= 85);
    }

    @Test
    void extractsTableLayout() {
        String text = """
                Dividend per share: $1.02
                Declaration Date: January 30, 2025
                Record Date: February 10, 2025
                Payment Date: February 26, 2025
                Ex-Dividend Date: February 10, 2025
                """;

        List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations = extract(text);

        assertEquals(1, declarations.size());
        DividendDeclarationTupleExtractor.DividendDeclaration d = declarations.get(0);
        assertEquals(1.02, d.amountPerShare());
        assertEquals(LocalDate.of(2025, 2, 10), d.recordDate());
        assertEquals(LocalDate.of(2025, 2, 26), d.payableDate());
        assertEquals(LocalDate.of(2025, 1, 30), d.declarationDate());
        assertEquals(LocalDate.of(2025, 2, 10), d.exDate());
    }

    @Test
    void extractsTwoDividendsFromOneRelease() {
        String text = """
                The Board declared a regular quarterly dividend of $0.50 per share payable on
                April 15, 2025 to holders of record on March 31, 2025. The Board also declared
                a special dividend of $3.00 per share payable on April 15, 2025 to holders of
                record on March 31, 2025.
                """;

        List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations = extract(text);

        assertEquals(2, declarations.size());
        assertTrue(declarations.stream().anyMatch(d -> d.amountPerShare() == 0.50));
        assertTrue(declarations.stream().anyMatch(d -> d.amountPerShare() == 3.00));
        assertTrue(declarations.stream().allMatch(d -> LocalDate.of(2025, 3, 31).equals(d.recordDate())));
    }

    @Test
    void amountWithoutRecordDateYieldsNoTuple() {
        String text = "The Company paid dividends of $0.24 per share during the quarter.";

        assertTrue(extract(text).isEmpty());
    }

    @Test
    void parsesNumericDateFormats() {
        String text = "Cash dividend of $0.13 per share, record date 3/10/2025, payable 3/27/2025.";

        List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations = extract(text);

        assertEquals(1, declarations.size());
        assertEquals(LocalDate.of(2025, 3, 10), declarations.get(0).recordDate());
        assertEquals(LocalDate.of(2025, 3, 27), declarations.get(0).payableDate());
    }

    @Test
    void dedupesOverlappingAnchorPatterns() {
        // "dividend of $0.24 per share" matches both anchor patterns; one tuple results.
        String text = "declared a dividend of $0.24 per share to shareholders of record on March 10, 2025";

        List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations = extract(text);

        assertEquals(1, declarations.size());
        assertEquals(0.24, declarations.get(0).amountPerShare());
    }

    @Test
    void ignoresImplausibleAmounts() {
        String text = "recorded revenue of $4500.00 per share of record on March 10, 2025";

        assertTrue(extract(text).isEmpty());
    }

    @Test
    void blankTextYieldsNothing() {
        assertTrue(extract("").isEmpty());
        assertTrue(extract(null).isEmpty());
    }

    /**
     * Many "record date" labels with no date inside the pattern's 160-char gap, then a real record
     * date last. Each dead label drives the lazy quantifier through its full range, so the guard's
     * deadline is checked well before the scan reaches the date at the end.
     */
    private static String backtrackHeavyWindow() {
        return "The Board declared a dividend of $0.24 per share. "
                + "record date no ".repeat(20)
                + "record date March 10, 2025.";
    }

    @Test
    void labeledDateTimeoutIsContainedAndDegradesToNoMatch() {
        // Control: with the production budget the scan completes and finds the trailing date.
        List<DividendDeclarationTupleExtractor.DividendDeclaration> completed = extract(backtrackHeavyWindow());
        assertEquals(1, completed.size());
        assertEquals(LocalDate.of(2025, 3, 10), completed.get(0).recordDate());

        // A budget of zero trips the deadline on its first check. The timeout must be swallowed as
        // "no further matches" rather than propagating out of extract().
        DividendDeclarationTupleExtractor timingOut = new DividendDeclarationTupleExtractor(0L);

        List<DividendDeclarationTupleExtractor.DividendDeclaration> degraded =
                assertDoesNotThrow(() -> timingOut.extract(backtrackHeavyWindow(), FILING_DATE, ACCESSION));

        // No record date survives the abort, and a tuple without one is dropped, so the pathological
        // document costs this declaration instead of the whole run.
        assertTrue(degraded.isEmpty());
    }
}

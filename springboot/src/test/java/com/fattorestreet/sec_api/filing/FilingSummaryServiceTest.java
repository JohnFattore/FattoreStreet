package com.fattorestreet.sec_api.filing;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.FilingSummary;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.FilingSummaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilingSummaryServiceTest {

    @Mock private FilingSummaryRepository filingSummaryRepository;
    @Mock private AssetRepository assetRepository;
    @InjectMocks private FilingSummaryService service;

    private static String padContent(String core) {
        return core + "<p>" + "Lorem ipsum dolor sit amet. ".repeat(60) + "</p>";
    }

    @Test
    void extractMda_findsItem7Section() {
        String html = "<html><body>" +
                "<p>Some preamble text.</p>" +
                "<h2>Item 7. Management's Discussion and Analysis</h2>" +
                padContent("<p>Revenue increased 15% year over year driven by strong demand. " +
                "Operating margins improved due to cost efficiencies.</p>") +
                "<h2>Item 8. Financial Statements and Supplementary Data</h2>" +
                "<p>Balance sheet data here.</p>" +
                "</body></html>";

        String result = service.extractMda(html);

        assertNotNull(result);
        assertTrue(result.contains("Revenue increased 15%"));
        assertFalse(result.contains("Balance sheet data here"));
    }

    @Test
    void extractMda_handlesNumericHtmlEntities() {
        String html = "<html><body>" +
                "<h2>Item 7. Management&#8217;s Discussion and Analysis</h2>" +
                padContent("<p>The company experienced growth across all segments.</p>") +
                "<h2>Item 8. Financial Statements</h2>" +
                "</body></html>";

        String result = service.extractMda(html);

        assertNotNull(result);
        assertTrue(result.contains("experienced growth"));
    }

    @Test
    void extractMda_skipsTocAndFindsRealSection() {
        String html = "<html><body>" +
                "<p>Item 7. Management's Discussion and Analysis 21</p>" +
                "<p>Item 8. Financial Statements 27</p>" +
                "<h2>Item 7. Management's Discussion and Analysis</h2>" +
                padContent("<p>Actual MD&amp;A content with real analysis here.</p>") +
                "<h2>Item 8. Financial Statements and Supplementary Data</h2>" +
                "</body></html>";

        String result = service.extractMda(html);

        assertNotNull(result);
        assertTrue(result.contains("Actual MD&A content"));
    }

    @Test
    void extractMda_returnsNullWhenNoItem7() {
        String html = "<html><body><p>No MD&A section here.</p></body></html>";

        String result = service.extractMda(html);

        assertNull(result);
    }

    @Test
    void truncateToWords_truncatesLongText() {
        String text = "word ".repeat(3000).trim();

        String result = service.truncateToWords(text, 2500);

        assertEquals(2500, result.split("\\s+").length);
    }

    @Test
    void truncateToWords_preservesShortText() {
        String text = "short text here";

        String result = service.truncateToWords(text, 2500);

        assertEquals("short text here", result);
    }

    @Test
    void summarizeAll_returnsResultMap() {
        when(assetRepository.findByIsFund(false)).thenReturn(Collections.emptyList());

        var result = service.summarizeAll();

        assertEquals(0, result.get("filingsSummarized"));
        assertEquals(0, result.get("errors"));
    }
}

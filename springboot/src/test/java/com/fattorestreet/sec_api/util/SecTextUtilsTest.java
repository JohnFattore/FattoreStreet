package com.fattorestreet.sec_api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SecTextUtilsTest {

    // --- notBlank ---

    @Test
    void notBlank_trueForText() {
        assertTrue(SecTextUtils.notBlank("x"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void notBlank_falseForBlank(String input) {
        assertFalse(SecTextUtils.notBlank(input));
    }

    // --- normalizeText ---

    @ParameterizedTest
    @CsvSource({
            "'Vanguard S&P 500 ETF', 'vanguard s p 500 etf'",
            "'  MiXeD   Case  ', 'mixed case'",
            "'already normal', 'already normal'",
    })
    void normalizeText_lowercasesAndStripsPunctuation(String input, String expected) {
        assertEquals(expected, SecTextUtils.normalizeText(input));
    }

    @Test
    void normalizeText_nullReturnsEmpty() {
        assertEquals("", SecTextUtils.normalizeText(null));
    }

    // --- containsToken ---

    @Test
    void containsToken_matchesSubstring() {
        assertTrue(SecTextUtils.containsToken("vanguard 500 index fund", "500 index"));
        assertFalse(SecTextUtils.containsToken("vanguard 500 index fund", "growth"));
    }

    @Test
    void containsToken_blankArgumentsAreFalse() {
        assertFalse(SecTextUtils.containsToken(null, "x"));
        assertFalse(SecTextUtils.containsToken("x", null));
        assertFalse(SecTextUtils.containsToken("", "x"));
        assertFalse(SecTextUtils.containsToken("x", " "));
    }

    // --- normalizeDateExtractionText ---

    @Test
    void normalizeDateExtractionText_stripsTagsAndEntities() {
        String html = "<div>Record&nbsp;date:</div><p>March 15, 2024 &amp; beyond</p>";
        assertEquals("Record date:\nMarch 15, 2024 & beyond",
                SecTextUtils.normalizeDateExtractionText(html));
    }

    @Test
    void normalizeDateExtractionText_dropsStyleAndScriptBlocks() {
        String html = "<style>td { color: red; }</style>before<script>alert(1)</script>after";
        assertEquals("before after", SecTextUtils.normalizeDateExtractionText(html));
    }

    @Test
    void normalizeDateExtractionText_collapsesWhitespaceAndNewlines() {
        String html = "a<br/>b</tr>\n\n\nc\t\td";
        assertEquals("a\nb\nc d", SecTextUtils.normalizeDateExtractionText(html));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void normalizeDateExtractionText_blankReturnsEmpty(String input) {
        assertEquals("", SecTextUtils.normalizeDateExtractionText(input));
    }
}

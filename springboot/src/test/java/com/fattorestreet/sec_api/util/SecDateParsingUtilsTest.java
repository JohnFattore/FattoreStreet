package com.fattorestreet.sec_api.util;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SecDateParsingUtilsTest {

    // --- parseIsoDate ---

    @Test
    void parseIsoDate_valid() {
        assertEquals(LocalDate.of(2024, 3, 15), SecDateParsingUtils.parseIsoDate("2024-03-15"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "garbage", "2024-02-30", "03/15/2024"})
    void parseIsoDate_invalidReturnsNull(String input) {
        assertNull(SecDateParsingUtils.parseIsoDate(input));
    }

    // --- parseUsDate ---

    @ParameterizedTest
    @CsvSource({
            "'January 5, 2024', 2024-01-05",
            "'Jan 5, 2024', 2024-01-05",
            "'Jan. 5, 2024', 2024-01-05",
            "'Sept. 30, 2023', 2023-09-30",
            "'February 29 2024', 2024-02-29",
            "'December 31, 1999', 1999-12-31",
            "'may 12, 2025', 2025-05-12",
    })
    void parseUsDate_valid(String input, LocalDate expected) {
        assertEquals(expected, SecDateParsingUtils.parseUsDate(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "Febtember 5, 2024", "2024-01-05"})
    void parseUsDate_invalidReturnsNull(String input) {
        assertNull(SecDateParsingUtils.parseUsDate(input));
    }

    @Test
    void parseUsDate_smartResolvesOutOfRangeDay() {
        // ofPattern defaults to ResolverStyle.SMART: Feb 30 clamps to the month's last day
        assertEquals(LocalDate.of(2024, 2, 29), SecDateParsingUtils.parseUsDate("February 30, 2024"));
    }

    // --- numeric formats ---

    @ParameterizedTest
    @CsvSource({
            "3/15/2024, 2024-03-15",
            "12/1/2024, 2024-12-01",
            "1/5/2024, 2024-01-05",
    })
    void parseSlashDate_valid(String input, LocalDate expected) {
        assertEquals(expected, SecDateParsingUtils.parseSlashDate(input));
    }

    @Test
    void parseSlashDate_invalidReturnsNull() {
        assertNull(SecDateParsingUtils.parseSlashDate("not a date"));
        // SMART resolution clamps out-of-range days rather than rejecting them
        assertEquals(LocalDate.of(2024, 2, 29), SecDateParsingUtils.parseSlashDate("2/30/2024"));
    }

    @ParameterizedTest
    @CsvSource({
            // 'uu' maps every two-digit year onto 2000-2099
            "3/15/24, 2024-03-15",
            "1/5/99, 2099-01-05",
            "12/31/00, 2000-12-31",
    })
    void parseSlashShortYearDate_valid(String input, LocalDate expected) {
        assertEquals(expected, SecDateParsingUtils.parseSlashShortYearDate(input));
    }

    @Test
    void parseYearSlashDate_valid() {
        assertEquals(LocalDate.of(2024, 3, 15), SecDateParsingUtils.parseYearSlashDate("2024/3/15"));
        assertNull(SecDateParsingUtils.parseYearSlashDate("15/3/2024"));
    }

    @Test
    void parseDashDate_valid() {
        assertEquals(LocalDate.of(2024, 3, 15), SecDateParsingUtils.parseDashDate("3-15-2024"));
        assertNull(SecDateParsingUtils.parseDashDate("2024-03-15"));
    }

    // --- parseAnyDate dispatch ---

    @ParameterizedTest
    @CsvSource({
            "2024-03-15, 2024-03-15",
            "2024/3/15, 2024-03-15",
            "'March 15, 2024', 2024-03-15",
            "3/15/2024, 2024-03-15",
            "3/15/24, 2024-03-15",
            "3-15-2024, 2024-03-15",
    })
    void parseAnyDate_dispatchesAcrossFormats(String input, LocalDate expected) {
        assertEquals(expected, SecDateParsingUtils.parseAnyDate(input));
    }

    @Test
    void parseAnyDate_unparseableReturnsNull() {
        assertNull(SecDateParsingUtils.parseAnyDate("on or about the record date"));
    }

    // --- extractAllDates ---

    @Test
    void extractAllDates_findsMultipleFormatsInText() {
        String text = "Record date 2024-03-15, payable on April 1, 2024; declared 3/1/2024.";
        List<LocalDate> dates = SecDateParsingUtils.extractAllDates(text);
        assertTrue(dates.contains(LocalDate.of(2024, 3, 15)));
        assertTrue(dates.contains(LocalDate.of(2024, 4, 1)));
        assertTrue(dates.contains(LocalDate.of(2024, 3, 1)));
    }

    @Test
    void extractAllDates_isoIsStrictButSlashSmartResolves() {
        // ISO parsing is strict (2024-02-30 dropped); slash parsing SMART-clamps to Feb 29
        List<LocalDate> dates = SecDateParsingUtils.extractAllDates("due 2024-02-30 or 2/30/2024");
        assertEquals(List.of(LocalDate.of(2024, 2, 29)), dates);
    }

    @Test
    void extractAllDates_emptyTextReturnsEmpty() {
        assertTrue(SecDateParsingUtils.extractAllDates("").isEmpty());
    }

    @Test
    void extractAllDates_fullYearSlashDateDoesNotAlsoMatchAsShortYear() {
        // "3/15/2024" must not additionally be read as the short-year date "3/15/20"
        assertEquals(List.of(LocalDate.of(2024, 3, 15)),
                SecDateParsingUtils.extractAllDates("payable 3/15/2024 to holders"));
    }

    @Test
    void extractAllDates_shortYearDateStillMatches() {
        assertEquals(List.of(LocalDate.of(2024, 3, 15)),
                SecDateParsingUtils.extractAllDates("payable 3/15/24 to holders"));
    }

    // --- previousBusinessDay ---

    @ParameterizedTest
    @CsvSource({
            // Tuesday -> Monday
            "2024-03-12, 2024-03-11",
            // Monday -> previous Friday
            "2024-03-11, 2024-03-08",
            // Sunday -> Friday
            "2024-03-10, 2024-03-08",
            // Saturday -> Friday
            "2024-03-09, 2024-03-08",
    })
    void previousBusinessDay_skipsWeekends(LocalDate input, LocalDate expected) {
        assertEquals(expected, SecDateParsingUtils.previousBusinessDay(input));
    }

    @Test
    void previousBusinessDay_nullReturnsNull() {
        assertNull(SecDateParsingUtils.previousBusinessDay(null));
    }
}

package com.fattorestreet.sec_api.util;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuarterUtilsTest {

    // --- standardizeDate: snaps to last day of the same month ---

    @Test
    void standardizeDate_alreadyEndOfMonth_unchanged() {
        assertEquals(LocalDate.of(2023, 3, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 3, 31)));
        assertEquals(LocalDate.of(2023, 6, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 6, 30)));
        assertEquals(LocalDate.of(2023, 9, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 9, 30)));
        assertEquals(LocalDate.of(2023, 12, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 12, 31)));
    }

    @Test
    void standardizeDate_midMonth_snapsToEndOfSameMonth() {
        assertEquals(LocalDate.of(2023, 3, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 3, 27)));
        assertEquals(LocalDate.of(2023, 6, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 6, 25)));
        assertEquals(LocalDate.of(2023, 9, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 9, 28)));
        assertEquals(LocalDate.of(2023, 12, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 12, 28)));
    }

    @Test
    void standardizeDate_nonQuarterMonth_snapsToEndOfThatMonth() {
        assertEquals(LocalDate.of(2023, 4, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 4, 3)));
        assertEquals(LocalDate.of(2023, 7, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 7, 5)));
        assertEquals(LocalDate.of(2023, 10, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 10, 2)));
        assertEquals(LocalDate.of(2024, 1, 31), QuarterUtils.standardizeDate(LocalDate.of(2024, 1, 5)));
    }

    @Test
    void standardizeDate_february_handlesLeapYear() {
        assertEquals(LocalDate.of(2023, 2, 28), QuarterUtils.standardizeDate(LocalDate.of(2023, 2, 1)));
        assertEquals(LocalDate.of(2024, 2, 29), QuarterUtils.standardizeDate(LocalDate.of(2024, 2, 15)));
    }

    // --- standardizeStartDate: snaps to first day of the month ---

    @Test
    void standardizeStartDate_snapsToFirstOfMonth() {
        assertEquals(LocalDate.of(2023, 3, 1), QuarterUtils.standardizeStartDate(LocalDate.of(2023, 3, 15)));
        assertEquals(LocalDate.of(2023, 6, 1), QuarterUtils.standardizeStartDate(LocalDate.of(2023, 6, 30)));
        assertEquals(LocalDate.of(2023, 1, 1), QuarterUtils.standardizeStartDate(LocalDate.of(2023, 1, 1)));
    }

    // --- parseQuarter: parses "CY2024Q1" format ---

    @Test
    void parseQuarter_validFormats() {
        assertEquals(LocalDate.of(2024, 3, 31), QuarterUtils.parseQuarter("CY2024Q1"));
        assertEquals(LocalDate.of(2024, 6, 30), QuarterUtils.parseQuarter("CY2024Q2"));
        assertEquals(LocalDate.of(2024, 9, 30), QuarterUtils.parseQuarter("CY2024Q3"));
        assertEquals(LocalDate.of(2024, 12, 31), QuarterUtils.parseQuarter("CY2024Q4"));
    }

    // --- nextQuarter: advances 3 months and snaps to end of month ---

    @Test
    void nextQuarter_advancesThreeMonths() {
        assertEquals(LocalDate.of(2024, 6, 30), QuarterUtils.nextQuarter(LocalDate.of(2024, 3, 31)));
        assertEquals(LocalDate.of(2024, 9, 30), QuarterUtils.nextQuarter(LocalDate.of(2024, 6, 30)));
        assertEquals(LocalDate.of(2024, 12, 31), QuarterUtils.nextQuarter(LocalDate.of(2024, 9, 30)));
        assertEquals(LocalDate.of(2025, 3, 31), QuarterUtils.nextQuarter(LocalDate.of(2024, 12, 31)));
    }

    // --- getLast3Quarters ---

    @Test
    void getLast3Quarters_returnsThreePreviousQuarterEnds() {
        List<LocalDate> result = QuarterUtils.getLast3Quarters(LocalDate.of(2024, 12, 31));
        assertEquals(3, result.size());
        assertEquals(LocalDate.of(2024, 9, 30), result.get(0));
        assertEquals(LocalDate.of(2024, 6, 30), result.get(1));
        assertEquals(LocalDate.of(2024, 3, 31), result.get(2));
    }
}

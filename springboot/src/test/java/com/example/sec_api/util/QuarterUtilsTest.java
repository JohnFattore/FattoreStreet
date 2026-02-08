package com.example.sec_api.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuarterUtilsTest {

    @Test
    public void testStandardizeDate() {
        // Q1
        assertEquals(LocalDate.of(2023, 3, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 3, 31)));
        assertEquals(LocalDate.of(2023, 3, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 3, 27)));
        assertEquals(LocalDate.of(2023, 3, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 4, 3)));

        // Q2
        assertEquals(LocalDate.of(2023, 6, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 6, 30)));
        assertEquals(LocalDate.of(2023, 6, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 6, 25)));
        assertEquals(LocalDate.of(2023, 6, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 7, 5)));

        // Q3
        assertEquals(LocalDate.of(2023, 9, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 9, 30)));
        assertEquals(LocalDate.of(2023, 9, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 9, 28)));
        assertEquals(LocalDate.of(2023, 9, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 10, 2)));

        // Q4
        assertEquals(LocalDate.of(2023, 12, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 12, 31)));
        assertEquals(LocalDate.of(2023, 12, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 12, 28)));
        assertEquals(LocalDate.of(2023, 12, 31), QuarterUtils.standardizeDate(LocalDate.of(2024, 1, 5)));

        // Edge cases
        assertEquals(LocalDate.of(2023, 3, 31), QuarterUtils.standardizeDate(LocalDate.of(2023, 2, 1)));
        assertEquals(LocalDate.of(2023, 6, 30), QuarterUtils.standardizeDate(LocalDate.of(2023, 5, 20)));
    }
}

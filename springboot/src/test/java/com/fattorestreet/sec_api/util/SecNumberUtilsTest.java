package com.fattorestreet.sec_api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SecNumberUtilsTest {

    // --- round4 ---

    @ParameterizedTest
    @CsvSource({
            "0.123456, 0.1235",
            "0.12344, 0.1234",
            "0.00005, 0.0001",
            "1.0, 1.0",
            "0.0, 0.0",
            "-0.123456, -0.1235",
    })
    void round4_roundsHalfUpToFourDecimals(double input, double expected) {
        assertEquals(expected, SecNumberUtils.round4(input));
    }

    // --- parsePositiveDouble ---

    @ParameterizedTest
    @CsvSource({
            "0.25, 0.25",
            "4, 4.0",
            "1e2, 100.0",
    })
    void parsePositiveDouble_valid(String input, double expected) {
        assertEquals(expected, SecNumberUtils.parsePositiveDouble(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1.5", "abc", "1,000", " "})
    void parsePositiveDouble_invalidOrNonPositiveReturnsNull(String input) {
        assertNull(SecNumberUtils.parsePositiveDouble(input));
    }

    // --- roughlyEqual ---

    @Test
    void roughlyEqual_withinEpsilon() {
        assertTrue(SecNumberUtils.roughlyEqual(0.25, 0.2501, 0.001));
        assertTrue(SecNumberUtils.roughlyEqual(0.25, 0.2509, 0.001));
    }

    @Test
    void roughlyEqual_outsideEpsilon() {
        assertFalse(SecNumberUtils.roughlyEqual(0.25, 0.2512, 0.001));
    }

    @Test
    void roughlyEqual_nullLeftIsFalse() {
        assertFalse(SecNumberUtils.roughlyEqual(null, 0.25, 0.001));
    }
}

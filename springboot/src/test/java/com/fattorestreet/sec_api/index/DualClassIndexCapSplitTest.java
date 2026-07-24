package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualClassIndexCapSplitTest {

    @Test
    void halvesCapForTicker_onlyGoogGoogl() {
        assertTrue(DualClassIndexCapSplit.halvesCapForTicker("GOOG"));
        assertTrue(DualClassIndexCapSplit.halvesCapForTicker("GOOGL"));
        assertFalse(DualClassIndexCapSplit.halvesCapForTicker("AAPL"));
        assertFalse(DualClassIndexCapSplit.halvesCapForTicker("BRKB"));
    }

    @Test
    void apply_halvesValueForGoogPair() {
        BigDecimal v = new BigDecimal("1000.00000");
        assertEquals(new BigDecimal("500.00000"), DualClassIndexCapSplit.apply("GOOG", v));
        assertEquals(new BigDecimal("500.00000"), DualClassIndexCapSplit.apply("GOOGL", v));
    }

    @Test
    void apply_unchangedForOtherTickers() {
        BigDecimal v = new BigDecimal("1000.00000");
        assertEquals(v, DualClassIndexCapSplit.apply("MSFT", v));
    }
}

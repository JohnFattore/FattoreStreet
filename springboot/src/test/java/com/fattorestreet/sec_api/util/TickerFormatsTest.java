package com.fattorestreet.sec_api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickerFormatsTest {

    @Test
    void iexSymbol_swapsSecClassSeparator() {
        assertEquals("BRK.B", TickerFormats.iexSymbol("BRK-B"));
        assertEquals("AAPL", TickerFormats.iexSymbol("AAPL"));
        assertNull(TickerFormats.iexSymbol(null));
    }

    @Test
    void compact_dropsEitherSeparator() {
        assertEquals("BRKB", TickerFormats.compact("BRK-B"));
        assertEquals("HEIA", TickerFormats.compact("HEI.A"));
        assertNull(TickerFormats.compact(null));
    }

    @Test
    void hasClassSeparator_detectsDashOrDot() {
        assertTrue(TickerFormats.hasClassSeparator("BF-B"));
        assertTrue(TickerFormats.hasClassSeparator("BF.B"));
        assertFalse(TickerFormats.hasClassSeparator("BFB"));
        assertFalse(TickerFormats.hasClassSeparator(null));
    }
}

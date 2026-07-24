package com.fattorestreet.sec_api.index;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IwbHoldingsCsvTest {

    @Test
    void parseTickers_equityOnly_skipsFutures() throws Exception {
        String csv = """
                iShares header
                Ticker,Name,Sector,Asset Class,x
                "ZZZ","CO","Industrials","Equity","100.00","2.50","100.00"
                "FAM6","FUT","Cash and/or Derivatives","Futures","0"
                """;
        Set<String> t = IwbHoldingsCsv.parseTickers(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(Set.of("ZZZ"), t);
    }

    @Test
    void parseEquityRows_readsTickerAndWeightPercent() throws Exception {
        String csv = """
                Ticker,Name,Sector,Asset Class,x
                "AAA","A INC","Tech","Equity","1,000.00","3.25","1,000.00"
                """;
        List<IwbHoldingsCsv.IwbEquityRow> rows = IwbHoldingsCsv.parseEquityRows(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, rows.size());
        assertEquals("AAA", rows.get(0).ticker());
        assertEquals(new BigDecimal("3.25"), rows.get(0).weightPercent());
    }

    @Test
    void parseTickers_bundledFile_loadsManyEquityRows() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/IWB_holdings.csv")) {
            assertNotNull(in, "classpath:/data/IWB_holdings.csv must be on test classpath");
            Set<String> t = IwbHoldingsCsv.parseTickers(in);
            assertTrue(t.size() > 900, "expected ~1000 equity names, got " + t.size());
            assertTrue(t.contains("NVDA") && t.contains("AAPL"));
            assertTrue(!t.contains("FAM6") && !t.contains("ESM6"));
        }
    }
}

package com.fattorestreet.sec_api.index;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.*;

class IwbHoldingsTickerSetTest {

    @Test
    void loadsEquityTickersFromClasspathCsv() throws IOException {
        IwbHoldingsTickerSet tickerSet = new IwbHoldingsTickerSet(new DefaultResourceLoader());

        Set<String> tickers = tickerSet.tickers();
        assertFalse(tickers.isEmpty(), "bundled IWB_holdings.csv should yield tickers");
        assertEquals(tickers.size(), tickerSet.equityRows().size(),
                "rows and deduplicated ticker set should agree");
        assertTrue(tickers.stream().allMatch(t -> t.equals(t.toUpperCase())),
                "tickers are uppercase symbols");
    }

    @Test
    void equityRowsCarryPositiveWeights() throws IOException {
        IwbHoldingsTickerSet tickerSet = new IwbHoldingsTickerSet(new DefaultResourceLoader());

        assertTrue(tickerSet.equityRows().stream()
                        .allMatch(r -> r.weightPercent() != null && r.weightPercent().signum() >= 0),
                "every equity row should have a non-negative weight");
    }

    @Test
    void missingResourceYieldsEmptySetWithoutThrowing() throws IOException {
        DefaultResourceLoader missingEverything = new DefaultResourceLoader() {
            @Override
            public org.springframework.core.io.Resource getResource(String location) {
                return super.getResource("classpath:/data/does-not-exist.csv");
            }
        };
        IwbHoldingsTickerSet tickerSet = new IwbHoldingsTickerSet(missingEverything);

        assertTrue(tickerSet.tickers().isEmpty());
        assertTrue(tickerSet.equityRows().isEmpty());
    }
}

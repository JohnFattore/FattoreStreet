package com.fattorestreet.sec_api.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Equity tickers and fund-reported weights from bundled {@code data/IWB_holdings.csv}
 * (iShares Russell 1000 ETF holdings export).
 */
@Component
public class IwbHoldingsTickerSet {

    private static final Logger log = LoggerFactory.getLogger(IwbHoldingsTickerSet.class);
    private static final String LOCATION = "classpath:/data/IWB_holdings.csv";

    private final Set<String> tickers;
    private final List<IwbHoldingsCsv.IwbEquityRow> equityRows;

    public IwbHoldingsTickerSet(ResourceLoader resourceLoader) throws IOException {
        Resource resource = resourceLoader.getResource(LOCATION);
        if (!resource.exists()) {
            log.warn("Missing resource {} (springboot/data/IWB_holdings.csv is gitignored); IWB ticker set will be empty", LOCATION);
            this.tickers = Collections.emptySet();
            this.equityRows = List.of();
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            List<IwbHoldingsCsv.IwbEquityRow> rows = IwbHoldingsCsv.parseEquityRows(in);
            this.equityRows = List.copyOf(rows);
            LinkedHashSet<String> tickerSet = rows.stream()
                    .map(IwbHoldingsCsv.IwbEquityRow::ticker)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            this.tickers = Collections.unmodifiableSet(tickerSet);
        }
        log.info("Loaded {} IWB equity tickers from {}", tickers.size(), LOCATION);
    }

    public Set<String> tickers() {
        return tickers;
    }

    /**
     * Ordered equity rows (ticker + iShares-reported weight %) for UI / benchmark comparison.
     */
    public List<IwbHoldingsCsv.IwbEquityRow> equityRows() {
        return equityRows;
    }
}

package com.fattorestreet.sec_api.index;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Equity tickers and fund-reported weights from {@code src/main/resources/data/IWB_holdings.csv}
 * ({@code classpath:/data/IWB_holdings.csv}; iShares Russell 1000 ETF export).
 */
@Component
public class IwbHoldingsTickerSet {

    private static final Logger log = LoggerFactory.getLogger(IwbHoldingsTickerSet.class);
    private static final String LOCATION = "classpath:/data/IWB_holdings.csv";

    private final Set<String> tickers;
    private final List<IwbHoldingsCsv.IwbEquityRow> equityRows;
    private final LocalDate asOfDate;

    public IwbHoldingsTickerSet(ResourceLoader resourceLoader) throws IOException {
        Resource resource = resourceLoader.getResource(LOCATION);
        if (!resource.exists()) {
            log.warn("Missing resource {}; IWB ticker set will be empty", LOCATION);
            this.tickers = Collections.emptySet();
            this.equityRows = List.of();
            this.asOfDate = null;
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            IwbHoldingsCsv.IwbHoldings holdings = IwbHoldingsCsv.parse(in);
            List<IwbHoldingsCsv.IwbEquityRow> rows = holdings.equityRows();
            this.equityRows = rows;
            this.asOfDate = holdings.asOfDate();
            LinkedHashSet<String> tickerSet = rows.stream()
                    .map(IwbHoldingsCsv.IwbEquityRow::ticker)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            this.tickers = Collections.unmodifiableSet(tickerSet);
        }
        log.info("Loaded {} IWB equity tickers (holdings as of {}) from {}", tickers.size(), asOfDate, LOCATION);
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

    /** Fund holdings date from the file preamble; {@code null} when absent or unparseable. */
    public LocalDate asOfDate() {
        return asOfDate;
    }
}

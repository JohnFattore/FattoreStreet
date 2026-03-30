package com.fattorestreet.sec_api.index;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses iShares-style IWB (Russell 1000) holdings export: reads the {@code Ticker,Name,...} table
 * and returns equity tickers only (excludes futures / cash rows and trailing disclaimer text).
 */
public final class IwbHoldingsCsv {

    private static final Pattern TICKER_START = Pattern.compile("^\\s*\"([A-Z0-9][A-Z0-9.\\-]*)\"\\s*,");
    /** After {@code ","Equity","}, captures fund "Weight (%)" (column 6). */
    private static final Pattern AFTER_EQUITY_WEIGHT = Pattern.compile(
            "\",\"Equity\",\"[^\"]*\",\"([0-9.]+)\"");

    public record IwbEquityRow(String ticker, BigDecimal weightPercent) {
    }

    private IwbHoldingsCsv() {
    }

    /**
     * Equity rows in CSV order: ticker and fund-reported weight percent (same scale as index member {@code percent}).
     */
    public static List<IwbEquityRow> parseEquityRows(InputStream in) throws IOException {
        List<IwbEquityRow> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean pastHeader = false;
            while ((line = br.readLine()) != null) {
                if (!pastHeader) {
                    if (line.startsWith("Ticker,Name,")) {
                        pastHeader = true;
                    }
                    continue;
                }
                if (!line.contains("\",\"Equity\",\"")) {
                    continue;
                }
                Matcher tm = TICKER_START.matcher(line);
                if (!tm.find()) {
                    continue;
                }
                Matcher wm = AFTER_EQUITY_WEIGHT.matcher(line);
                if (!wm.find()) {
                    continue;
                }
                out.add(new IwbEquityRow(tm.group(1), new BigDecimal(wm.group(1))));
            }
        }
        return out;
    }

    public static Set<String> parseTickers(InputStream in) throws IOException {
        LinkedHashSet<String> tickers = new LinkedHashSet<>();
        for (IwbEquityRow row : parseEquityRows(in)) {
            tickers.add(row.ticker());
        }
        return tickers;
    }
}

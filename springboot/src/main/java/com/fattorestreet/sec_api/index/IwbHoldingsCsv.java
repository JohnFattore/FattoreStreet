package com.fattorestreet.sec_api.index;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    /** Captures the date from the preamble line {@code Fund Holdings as of,"Nov 06, 2026"}. */
    private static final Pattern AS_OF_LINE = Pattern.compile("^Fund Holdings as of,\\s*\"([^\"]+)\"");
    private static final DateTimeFormatter AS_OF_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    public record IwbEquityRow(String ticker, BigDecimal weightPercent) {
    }

    /**
     * Parsed export: the fund's holdings date ({@code null} when the preamble line is missing or
     * unparseable) and equity rows in CSV order.
     */
    public record IwbHoldings(LocalDate asOfDate, List<IwbEquityRow> equityRows) {
        public IwbHoldings {
            equityRows = List.copyOf(equityRows);
        }
    }

    private IwbHoldingsCsv() {
    }

    /**
     * Equity rows in CSV order: ticker and fund-reported weight percent (same scale as index member {@code percent}).
     */
    public static List<IwbEquityRow> parseEquityRows(InputStream in) throws IOException {
        return parse(in).equityRows();
    }

    /** Parses the holdings date from the preamble and the equity rows from the table. */
    public static IwbHoldings parse(InputStream in) throws IOException {
        List<IwbEquityRow> out = new ArrayList<>();
        LocalDate asOfDate = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean pastHeader = false;
            while ((line = br.readLine()) != null) {
                if (!pastHeader) {
                    Matcher am = AS_OF_LINE.matcher(line);
                    if (am.find()) {
                        asOfDate = parseAsOfDate(am.group(1));
                    } else if (line.startsWith("Ticker,Name,")) {
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
        return new IwbHoldings(asOfDate, out);
    }

    private static LocalDate parseAsOfDate(String raw) {
        try {
            return LocalDate.parse(raw.trim(), AS_OF_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Set<String> parseTickers(InputStream in) throws IOException {
        LinkedHashSet<String> tickers = new LinkedHashSet<>();
        for (IwbEquityRow row : parseEquityRows(in)) {
            tickers.add(row.ticker());
        }
        return tickers;
    }
}

package com.fattorestreet.sec_api.util;

/**
 * Converts share-class tickers between the formats the sources use. One Berkshire Class B share is
 * {@code BRK-B} in SEC's ticker index (and so in {@code listings}), {@code BRK.B} in IEX HIST (and so
 * in {@code daily_prices}), and {@code BRKB} in iShares holdings exports.
 */
public final class TickerFormats {

    private TickerFormats() {
    }

    /**
     * IEX symbol for a listing ticker: SEC's {@code -} class separator becomes IEX's {@code .}. A
     * ticker already in IEX form ({@code BRK.B}) or with no separator passes through unchanged.
     */
    public static String iexSymbol(String listingTicker) {
        return listingTicker == null ? null : listingTicker.replace('-', '.');
    }

    /** Ticker with class separators removed, the form iShares exports use ({@code BRK-B} → {@code BRKB}). */
    public static String compact(String ticker) {
        return ticker == null ? null : ticker.replace("-", "").replace(".", "");
    }

    /** Whether the ticker carries a share-class separator ({@code BRK-B}, {@code BRK.B}). */
    public static boolean hasClassSeparator(String ticker) {
        return ticker != null && (ticker.indexOf('-') >= 0 || ticker.indexOf('.') >= 0);
    }
}

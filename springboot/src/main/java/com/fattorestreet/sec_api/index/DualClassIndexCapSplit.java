package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * SEC companyfacts {@code EntityCommonStockSharesOutstanding} is typically one consolidated figure per CIK.
 * Multiple listed tickers for the same issuer (e.g. Alphabet Class A vs Class C) would each multiply that
 * full share count by a different price, overstating aggregate market cap. For known dual-listed common
 * pairs where each line should represent roughly half of the issuer's equity value for index purposes,
 * we scale market cap and free-float market cap by one half.
 */
public final class DualClassIndexCapSplit {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final int CAP_SCALE = 5;

    /**
     * Alphabet Inc. Class C (GOOG) and Class A (GOOGL): same CIK; DEI shares outstanding is consolidated.
     */
    private static final Set<String> HALF_WEIGHT_TICKERS = Set.of("GOOG", "GOOGL");

    private DualClassIndexCapSplit() {
    }

    public static boolean halvesCapForTicker(String ticker) {
        return ticker != null && HALF_WEIGHT_TICKERS.contains(ticker);
    }

    /**
     * Returns {@code value × ½} (same scale) when this ticker participates in a consolidated-share split; otherwise unchanged.
     */
    public static BigDecimal apply(String ticker, BigDecimal value) {
        if (value == null || !halvesCapForTicker(ticker)) {
            return value;
        }
        return value.multiply(HALF).setScale(CAP_SCALE, RoundingMode.HALF_UP);
    }
}

package com.fattorestreet.sec_api.util;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Explicit time zones for "now"-based date arithmetic.
 *
 * <p>{@code LocalDate.now()}, {@code LocalDateTime.now()} and {@code Year.now()} silently read the
 * host's default zone, so the same code decides a different "today" on a developer laptop, a CI
 * runner and a Fargate task. For trading-day and filing-window logic that difference is a
 * correctness bug, not a cosmetic one. Error Prone's {@code JavaTimeDefaultTimeZone} check fails
 * the build on the zone-less overloads; pass one of the constants below instead.
 */
public final class MarketTime {

    /**
     * US market and SEC calendar zone.
     *
     * <p>Use for anything that answers "what trading day / filing year is it": IEX trading-date
     * windows, SEC filing staleness, and the default year for index metrics. SEC deadlines and US
     * exchange sessions are both defined in Eastern time, so this stays correct across DST.
     */
    public static final ZoneId MARKET = ZoneId.of("America/New_York");

    /**
     * Zone for stored audit timestamps.
     *
     * <p>Use for columns that are only ever compared to other stored timestamps (created-at,
     * extracted-at, last-detected-at). UTC keeps those comparisons monotonic and host-independent,
     * and avoids the DST fold that would make an hour of local timestamps ambiguous.
     */
    public static final ZoneOffset STORAGE = ZoneOffset.UTC;

    private MarketTime() {
    }
}

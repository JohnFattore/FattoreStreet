package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SplitPriceCorroboratorTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    private SplitPriceCorroborator corroborator() {
        return new SplitPriceCorroborator(dailyPriceRepository);
    }

    /** Builds a series of consecutive calendar days from the given closes. */
    private static SplitPriceCorroborator.PriceSeries series(double... closes) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            dates.add(START.plusDays(i));
        }
        return new SplitPriceCorroborator.PriceSeries(dates, closes);
    }

    @Test
    void corroboratesForwardFourForOneSplit() {
        // 400 -> 100 overnight with a bit of same-day drift
        SplitPriceCorroborator.PriceSeries s =
                series(395, 398, 400, 102, 101, 103, 102, 101);
        Optional<SplitPriceCorroborator.Corroboration> result = corroborator()
                .corroborate(s, 4.0, START, START.plusDays(7));
        assertTrue(result.isPresent());
        assertEquals(START.plusDays(3), result.get().breakDate());
        assertEquals(4.0, result.get().snappedMultiplier());
    }

    @Test
    void corroboratesReverseOneForTenSplit() {
        SplitPriceCorroborator.PriceSeries s =
                series(5.1, 5.0, 4.9, 49.5, 50.2, 49.8);
        Optional<SplitPriceCorroborator.Corroboration> result = corroborator()
                .corroborate(s, 0.1, START, START.plusDays(5));
        assertTrue(result.isPresent());
        assertEquals(START.plusDays(3), result.get().breakDate());
    }

    @Test
    void extendedRatioInsideTightBandMatches() {
        // 3:2 split (multiplier 1.5) with ~4% same-day move: inside ln(1.06)
        SplitPriceCorroborator.PriceSeries s =
                series(150, 150, 96.1, 96, 95);
        assertTrue(corroborator().corroborate(s, 1.5, START, START.plusDays(4)).isPresent());
    }

    @Test
    void extendedRatioOutsideTightBandRejected() {
        // observed move 150 -> 92 is ~8.7% past 1.5: outside ln(1.06)
        SplitPriceCorroborator.PriceSeries s =
                series(150, 150, 92, 92, 92);
        assertTrue(corroborator().corroborate(s, 1.5, START, START.plusDays(4)).isEmpty());
    }

    @Test
    void picksClosestWhenTwoCandidatesInWindow() {
        // two ~2x drops; second is the cleaner match to 2.0
        SplitPriceCorroborator.PriceSeries s =
                series(100, 44, 44, 21.9, 22, 22);
        Optional<SplitPriceCorroborator.Corroboration> result = corroborator()
                .corroborate(s, 2.0, START, START.plusDays(5));
        assertTrue(result.isPresent());
        assertEquals(START.plusDays(3), result.get().breakDate());
    }

    @Test
    void corroborateOutsideWindowReturnsEmpty() {
        SplitPriceCorroborator.PriceSeries s =
                series(400, 400, 100, 100, 100);
        assertTrue(corroborator()
                .corroborate(s, 4.0, START.plusDays(3), START.plusDays(4)).isEmpty());
    }

    @Test
    void windowCoverageDetection() {
        SplitPriceCorroborator.PriceSeries s = series(100, 100, 100);
        SplitPriceCorroborator c = corroborator();
        assertTrue(c.windowFullyCovered(s, START, START.plusDays(2)));
        assertFalse(c.windowFullyCovered(s, START.minusDays(1), START.plusDays(2)));
        assertFalse(c.windowFullyCovered(s, START, START.plusDays(3)));
        assertFalse(c.windowFullyCovered(
                new SplitPriceCorroborator.PriceSeries(List.of(), new double[0]), START, START));
    }

    @Test
    void scanFindsPersistedSplitBreak() {
        SplitPriceCorroborator.PriceSeries s =
                series(199, 200, 201, 200, 199, 100, 101, 99, 100, 101, 100);
        List<SplitPriceCorroborator.Corroboration> breaks =
                corroborator().scanForSplitLikeBreaks(s, null);
        assertEquals(1, breaks.size());
        assertEquals(START.plusDays(5), breaks.get(0).breakDate());
        assertEquals(2.0, breaks.get(0).snappedMultiplier());
    }

    @Test
    void scanRejectsCrashThatRecovers() {
        // -50% day that V-shapes back up: snaps to 2.0 but fails the median persistence check
        SplitPriceCorroborator.PriceSeries s =
                series(200, 200, 200, 200, 200, 100, 180, 195, 200, 200, 200);
        assertTrue(corroborator().scanForSplitLikeBreaks(s, null).isEmpty());
    }

    @Test
    void scanRejectsSingleDayGlitch() {
        SplitPriceCorroborator.PriceSeries s =
                series(200, 200, 200, 200, 200, 100, 200, 200, 200, 200, 200);
        assertTrue(corroborator().scanForSplitLikeBreaks(s, null).isEmpty());
    }

    @Test
    void scanIgnoresBreaksBeforeMinDate() {
        SplitPriceCorroborator.PriceSeries s =
                series(200, 200, 200, 200, 200, 100, 100, 100, 100, 100, 100);
        assertTrue(corroborator().scanForSplitLikeBreaks(s, START.plusDays(6)).isEmpty());
        assertEquals(1, corroborator().scanForSplitLikeBreaks(s, START.plusDays(5)).size());
    }

    @Test
    void scanIgnoresOrdinaryDailyMoves() {
        SplitPriceCorroborator.PriceSeries s =
                series(100, 103, 99, 104, 98, 101, 105, 100, 97, 102);
        assertTrue(corroborator().scanForSplitLikeBreaks(s, null).isEmpty());
    }

    @Test
    void loadDropsRowsWithoutPositiveClose() {
        DailyPrice good = price("AAPL", START, 100.0);
        DailyPrice nullClose = price("AAPL", START.plusDays(1), null);
        DailyPrice zeroClose = price("AAPL", START.plusDays(2), 0.0);
        DailyPrice good2 = price("AAPL", START.plusDays(3), 101.0);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc("AAPL"))
                .thenReturn(List.of(good, nullClose, zeroClose, good2));

        SplitPriceCorroborator.PriceSeries s = corroborator().load("AAPL");
        assertEquals(List.of(START, START.plusDays(3)), s.dates());
        assertArrayEquals(new double[]{100.0, 101.0}, s.closes());
    }

    private static DailyPrice price(String ticker, LocalDate date, Double close) {
        DailyPrice p = new DailyPrice();
        p.setTicker(ticker);
        p.setTradeDate(date);
        p.setClosePrice(close);
        return p;
    }
}

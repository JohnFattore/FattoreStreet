package com.fattorestreet.sec_api.corporateaction.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService.DividendEvent;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceInferredExDateEstimatorTest {

    private static final String TICKER = "T";
    private static final double DIVIDEND = 0.5;
    /** Record date this synthetic issuer actually uses, in days after the period end. */
    private static final int TRUE_RECORD_OFFSET = 10;

    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private CorporateActionFilingDateService corporateActionFilingDateService;

    private PriceInferredExDateEstimator estimator() {
        // Weekend-only calendar and ex = record (T+1) keep the fixture's arithmetic transparent.
        lenient().when(corporateActionFilingDateService.businessDayOnOrBefore(any())).thenAnswer(inv -> {
            LocalDate d = inv.getArgument(0);
            while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                d = d.minusDays(1);
            }
            return d;
        });
        lenient().when(corporateActionFilingDateService.computeExDividendDate(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        return new PriceInferredExDateEstimator(dailyPriceRepository, corporateActionFilingDateService);
    }

    private static List<LocalDate> quarterEnds(int fromYear, int toYear) {
        List<LocalDate> out = new ArrayList<>();
        for (int y = fromYear; y <= toYear; y++) {
            out.add(LocalDate.of(y, 3, 31));
            out.add(LocalDate.of(y, 6, 30));
            out.add(LocalDate.of(y, 9, 30));
            out.add(LocalDate.of(y, 12, 31));
        }
        return out;
    }

    private static LocalDate trueExDate(LocalDate periodEnd) {
        LocalDate d = periodEnd.plusDays(TRUE_RECORD_OFFSET);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    private static DividendEvent synthetic(LocalDate periodEnd) {
        return new DividendEvent(periodEnd, periodEnd.plusDays(41), DIVIDEND, DIVIDEND, periodEnd.getYear(), false,
                null, null, CorporateAction.EX_DATE_SOURCE_SYNTHETIC);
    }

    /**
     * Weekday price series around $30 with ~0.6% daily noise; when {@code withDrops}, the price
     * on each {@code exDates} entry gaps down by the dividend.
     */
    private static List<DailyPrice> prices(LocalDate from, LocalDate to, Set<LocalDate> exDates, boolean withDrops) {
        Random random = new Random(42);
        List<DailyPrice> out = new ArrayList<>();
        double prevClose = 30.0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            double base = withDrops && exDates.contains(d) ? prevClose - DIVIDEND : prevClose;
            double open = base * Math.exp(0.005 * random.nextGaussian());
            double close = open * Math.exp(0.003 * random.nextGaussian());
            DailyPrice dp = new DailyPrice();
            dp.setTicker(TICKER);
            dp.setTradeDate(d);
            dp.setOpenPrice(open);
            dp.setClosePrice(close);
            out.add(dp);
            prevClose = close;
        }
        return out;
    }

    private static Set<LocalDate> trueExDates(List<LocalDate> periodEnds) {
        Set<LocalDate> out = new HashSet<>();
        periodEnds.forEach(p -> out.add(trueExDate(p)));
        return out;
    }

    @Test
    void redateSyntheticEvents_recoversIssuerRecordOffsetFromPriceDrops() {
        List<LocalDate> periodEnds = quarterEnds(2018, 2023);
        List<DividendEvent> events = periodEnds.stream().map(PriceInferredExDateEstimatorTest::synthetic).toList();
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(
                prices(LocalDate.of(2018, 1, 2), LocalDate.of(2024, 3, 29), trueExDates(periodEnds), true));

        List<DividendEvent> out = estimator().redateSyntheticEvents(TICKER, events);

        assertEquals(events.size(), out.size());
        for (DividendEvent event : out) {
            assertEquals(CorporateAction.EX_DATE_SOURCE_PRICE_INFERRED, event.exDateSource());
            assertEquals(trueExDate(event.fiscalPeriodEnd()), event.exDividendDate(),
                    "period " + event.fiscalPeriodEnd());
            assertNull(event.recordDate());
        }
    }

    @Test
    void redateSyntheticEvents_leavesFilingGroundedAndSpecialEventsAlone() {
        List<LocalDate> periodEnds = quarterEnds(2018, 2023);
        List<DividendEvent> events = new ArrayList<>(periodEnds.stream().map(PriceInferredExDateEstimatorTest::synthetic).toList());
        DividendEvent grounded = new DividendEvent(LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 9), DIVIDEND, DIVIDEND,
                2024, false, LocalDate.of(2024, 4, 9), null, CorporateAction.EX_DATE_SOURCE_RECORD_DP);
        DividendEvent special = new DividendEvent(LocalDate.of(2022, 6, 30), LocalDate.of(2022, 8, 10), 3.0, 3.0,
                2022, true, null, null, CorporateAction.EX_DATE_SOURCE_SYNTHETIC);
        events.add(grounded);
        events.add(special);
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(
                prices(LocalDate.of(2018, 1, 2), LocalDate.of(2024, 6, 28), trueExDates(periodEnds), true));

        List<DividendEvent> out = estimator().redateSyntheticEvents(TICKER, events);

        assertTrue(out.contains(grounded));
        assertTrue(out.contains(special));
        assertEquals(periodEnds.size(), out.stream()
                .filter(e -> CorporateAction.EX_DATE_SOURCE_PRICE_INFERRED.equals(e.exDateSource()))
                .count());
    }

    @Test
    void redateSyntheticEvents_keepsSyntheticDatesWhenPricesShowNoDrops() {
        List<LocalDate> periodEnds = quarterEnds(2018, 2023);
        List<DividendEvent> events = periodEnds.stream().map(PriceInferredExDateEstimatorTest::synthetic).toList();
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(
                prices(LocalDate.of(2018, 1, 2), LocalDate.of(2024, 3, 29), Set.of(), false));

        assertSame(events, estimator().redateSyntheticEvents(TICKER, events));
    }

    @Test
    void redateSyntheticEvents_keepsSyntheticDatesWithTooFewObservedEvents() {
        List<LocalDate> periodEnds = quarterEnds(2023, 2023);
        List<DividendEvent> events = periodEnds.stream().map(PriceInferredExDateEstimatorTest::synthetic).toList();
        when(dailyPriceRepository.findByTickerOrderByTradeDateAsc(TICKER)).thenReturn(
                prices(LocalDate.of(2023, 1, 2), LocalDate.of(2024, 3, 29), trueExDates(periodEnds), true));

        assertSame(events, estimator().redateSyntheticEvents(TICKER, events));
    }

    @Test
    void redateSyntheticEvents_skipsPriceLookupWhenNothingIsSynthetic() {
        List<DividendEvent> events = List.of(new DividendEvent(LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 9),
                DIVIDEND, DIVIDEND, 2024, false, LocalDate.of(2024, 4, 9), null, CorporateAction.EX_DATE_SOURCE_RECORD_DP));

        assertSame(events, estimator().redateSyntheticEvents(TICKER, events));
        verifyNoInteractions(dailyPriceRepository);
    }
}

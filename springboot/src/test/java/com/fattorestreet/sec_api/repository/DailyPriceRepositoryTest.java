package com.fattorestreet.sec_api.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.fattorestreet.sec_api.model.DailyPrice;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class DailyPriceRepositoryTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 3, 10);
    private static final LocalDate TUESDAY = LocalDate.of(2025, 3, 11);
    private static final LocalDate WEDNESDAY = LocalDate.of(2025, 3, 12);

    @Autowired
    private DailyPriceRepository repository;
    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void seed() {
        price("AAPL", MONDAY, 170.0, 169.5);
        price("AAPL", TUESDAY, 171.0, null);
        price("MSFT", MONDAY, 410.0, 409.0);
        em.flush();
        em.clear();
    }

    private void price(String ticker, LocalDate date, double close, Double adjustedClose) {
        DailyPrice dp = new DailyPrice();
        dp.setTicker(ticker);
        dp.setTradeDate(date);
        dp.setClosePrice(close);
        dp.setAdjustedClose(adjustedClose);
        em.persist(dp);
    }

    @Test
    void findDistinctTickers() {
        List<String> tickers = repository.findDistinctTickers();

        assertEquals(2, tickers.size());
        assertTrue(tickers.containsAll(List.of("AAPL", "MSFT")));
    }

    @Test
    void findTickersWithUnadjustedPrices_onlyNullAdjustedClose() {
        assertEquals(List.of("AAPL"), repository.findTickersWithUnadjustedPrices());
    }

    @Test
    void existsByTickerAndAdjustedCloseIsNull() {
        assertTrue(repository.existsByTickerAndAdjustedCloseIsNull("AAPL"));
        assertFalse(repository.existsByTickerAndAdjustedCloseIsNull("MSFT"));
    }

    @Test
    void findTopByTickerAndTradeDateLessThanEqual_picksLatestOnOrBefore() {
        Optional<DailyPrice> exact = repository
                .findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc("AAPL", TUESDAY);
        assertEquals(TUESDAY, exact.orElseThrow().getTradeDate());

        Optional<DailyPrice> later = repository
                .findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc("AAPL", WEDNESDAY);
        assertEquals(TUESDAY, later.orElseThrow().getTradeDate());

        Optional<DailyPrice> tooEarly = repository
                .findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc("AAPL", MONDAY.minusDays(1));
        assertTrue(tooEarly.isEmpty());
    }

    @Test
    void findByTickerAndTradeDateBetween_ordersDescending() {
        List<DailyPrice> range = repository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(
                "AAPL", MONDAY, WEDNESDAY);

        assertEquals(2, range.size());
        assertEquals(TUESDAY, range.get(0).getTradeDate());
        assertEquals(MONDAY, range.get(1).getTradeDate());
    }

    @Test
    void existsByTradeDate() {
        assertTrue(repository.existsByTradeDate(MONDAY));
        assertFalse(repository.existsByTradeDate(WEDNESDAY));
    }

    @Test
    void uniqueConstraintRejectsDuplicateTickerDate() {
        price("AAPL", MONDAY, 999.0, null);

        assertThrows(RuntimeException.class, em::flush,
                "duplicate (ticker, trade_date) must violate the unique constraint");
    }
}

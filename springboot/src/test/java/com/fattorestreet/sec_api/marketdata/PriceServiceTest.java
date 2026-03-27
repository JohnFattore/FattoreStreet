package com.fattorestreet.sec_api.marketdata;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private PriceService priceService;

    @Test
    void getPrices_delegatesToRepository() {
        DailyPrice dp = new DailyPrice();
        dp.setTicker("AAPL");
        dp.setTradeDate(LocalDate.of(2025, 6, 15));
        dp.setClosePrice(175.0);

        when(dailyPriceRepository.findByTickerOrderByTradeDateDesc("AAPL"))
                .thenReturn(List.of(dp));

        List<DailyPrice> result = priceService.getPrices("AAPL");

        assertEquals(1, result.size());
        assertEquals("AAPL", result.get(0).getTicker());
        verify(dailyPriceRepository).findByTickerOrderByTradeDateDesc("AAPL");
    }

    @Test
    void getPrices_withDateRange_delegatesToRepository() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 6, 30);

        when(dailyPriceRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("AAPL", start, end))
                .thenReturn(List.of());

        List<DailyPrice> result = priceService.getPrices("AAPL", start, end);

        assertTrue(result.isEmpty());
        verify(dailyPriceRepository).findByTickerAndTradeDateBetweenOrderByTradeDateDesc("AAPL", start, end);
    }
}

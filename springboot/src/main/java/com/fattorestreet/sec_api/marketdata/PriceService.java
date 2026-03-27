package com.fattorestreet.sec_api.marketdata;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PriceService {

    private final DailyPriceRepository dailyPriceRepository;

    public PriceService(DailyPriceRepository dailyPriceRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
    }

    public List<DailyPrice> getPrices(String ticker) {
        return dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
    }

    public List<DailyPrice> getPrices(String ticker, LocalDate start, LocalDate end) {
        return dailyPriceRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(ticker, start, end);
    }
}

package com.fattorestreet.sec_api.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.fattorestreet.sec_api.model.DailyPrice;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    List<DailyPrice> findByTickerOrderByTradeDateDesc(String ticker);

    Optional<DailyPrice> findTopByTickerOrderByTradeDateDesc(String ticker);

    List<DailyPrice> findByTickerAndTradeDateBetweenOrderByTradeDateDesc(
            String ticker, LocalDate start, LocalDate end);

    Optional<DailyPrice> findByTickerAndTradeDate(String ticker, LocalDate tradeDate);

    Optional<DailyPrice> findTopByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(
            String ticker, LocalDate tradeDate);

    boolean existsByTradeDate(LocalDate tradeDate);

    List<DailyPrice> findByTradeDate(LocalDate tradeDate);

    @Query("SELECT DISTINCT d.ticker FROM DailyPrice d")
    List<String> findDistinctTickers();

    List<DailyPrice> findByTickerOrderByTradeDateAsc(String ticker);

    boolean existsByTickerAndAdjustedCloseIsNull(String ticker);

    @Query("SELECT DISTINCT d.ticker FROM DailyPrice d WHERE d.adjustedClose IS NULL")
    List<String> findTickersWithUnadjustedPrices();

    List<DailyPrice> findTop2ByTickerOrderByTradeDateDesc(String ticker);
}

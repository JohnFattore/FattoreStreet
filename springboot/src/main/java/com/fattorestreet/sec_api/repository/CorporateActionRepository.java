package com.fattorestreet.sec_api.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;

public interface CorporateActionRepository extends JpaRepository<CorporateAction, Long> {

    List<CorporateAction> findByTickerOrderByEffectiveDateDesc(String ticker);

    boolean existsByTickerAndActionTypeAndEffectiveDate(String ticker, ActionType actionType, LocalDate effectiveDate);

    Optional<CorporateAction> findByTickerAndActionTypeAndEffectiveDate(String ticker, ActionType actionType, LocalDate effectiveDate);

    List<CorporateAction> findAllByTickerAndActionTypeAndEffectiveDate(String ticker, ActionType actionType, LocalDate effectiveDate);

    List<CorporateAction> findByTicker(String ticker);

    Optional<CorporateAction> findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
            String ticker,
            ActionType actionType,
            LocalDate effectiveDate,
            Double ratio,
            CorporateAction.SourceType sourceType);

    List<CorporateAction> findByTickerAndActionTypeAndEffectiveDateAndSourceType(
            String ticker,
            ActionType actionType,
            LocalDate effectiveDate,
            CorporateAction.SourceType sourceType);

    @Query("SELECT DISTINCT c.ticker FROM CorporateAction c")
    List<String> findDistinctTickers();
}

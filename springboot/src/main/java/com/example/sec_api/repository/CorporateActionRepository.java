package com.example.sec_api.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.example.sec_api.model.CorporateAction;
import com.example.sec_api.model.CorporateAction.ActionType;

public interface CorporateActionRepository extends JpaRepository<CorporateAction, Long> {

    List<CorporateAction> findByTickerOrderByEffectiveDateDesc(String ticker);

    boolean existsByTickerAndActionTypeAndEffectiveDate(String ticker, ActionType actionType, LocalDate effectiveDate);

    Optional<CorporateAction> findByTickerAndActionTypeAndEffectiveDate(String ticker, ActionType actionType, LocalDate effectiveDate);

    List<CorporateAction> findByTicker(String ticker);

    @Query("SELECT DISTINCT c.ticker FROM CorporateAction c")
    List<String> findDistinctTickers();
}

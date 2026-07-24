package com.fattorestreet.sec_api.repository;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.CorporateAction.SourceType;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CorporateActionRepositoryTest {

    private static final LocalDate SPLIT_DATE = LocalDate.of(2020, 8, 31);
    private static final LocalDate DIV_DATE = LocalDate.of(2025, 2, 10);

    @Autowired
    private CorporateActionRepository repository;
    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void seed() {
        action("AAPL", ActionType.SPLIT, SPLIT_DATE, 0.25, SourceType.SEC_EQUITY_XBRL);
        action("AAPL", ActionType.DIVIDEND, DIV_DATE, 0.25, SourceType.SEC_EQUITY_XBRL);
        action("VOO", ActionType.DIVIDEND, DIV_DATE, 1.72, SourceType.SEC_ETF_FILING);
        em.flush();
        em.clear();
    }

    private void action(String ticker, ActionType type, LocalDate date, double ratio, SourceType source) {
        CorporateAction a = new CorporateAction();
        a.setTicker(ticker);
        a.setActionType(type);
        a.setEffectiveDate(date);
        a.setRatio(ratio);
        a.setSourceType(source);
        em.persist(a);
    }

    @Test
    void findDistinctTickers() {
        List<String> tickers = repository.findDistinctTickers();

        assertEquals(2, tickers.size());
        assertTrue(tickers.containsAll(List.of("AAPL", "VOO")));
    }

    @Test
    void existsByTickerAndActionTypeAndEffectiveDate() {
        assertTrue(repository.existsByTickerAndActionTypeAndEffectiveDate("AAPL", ActionType.SPLIT, SPLIT_DATE));
        assertFalse(repository.existsByTickerAndActionTypeAndEffectiveDate("AAPL", ActionType.DIVIDEND, SPLIT_DATE));
    }

    @Test
    void findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType_matchesExactRow() {
        assertTrue(repository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO", ActionType.DIVIDEND, DIV_DATE, 1.72, SourceType.SEC_ETF_FILING).isPresent());
        assertTrue(repository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO", ActionType.DIVIDEND, DIV_DATE, 1.72, SourceType.SEC_EQUITY_XBRL).isEmpty());
        assertTrue(repository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO", ActionType.DIVIDEND, DIV_DATE, 1.73, SourceType.SEC_ETF_FILING).isEmpty());
    }

    @Test
    void findByTickerOrderByEffectiveDateDesc() {
        List<CorporateAction> actions = repository.findByTickerOrderByEffectiveDateDesc("AAPL");

        assertEquals(2, actions.size());
        assertEquals(DIV_DATE, actions.get(0).getEffectiveDate());
        assertEquals(SPLIT_DATE, actions.get(1).getEffectiveDate());
    }

    @Test
    void uniqueConstraintAllowsSameDateDifferentRatio() {
        // (ticker, action_type, effective_date, ratio) is unique — a different ratio is a new row.
        action("AAPL", ActionType.DIVIDEND, DIV_DATE, 0.26, SourceType.SEC_EQUITY_XBRL);
        em.flush();

        assertEquals(2, repository.findAllByTickerAndActionTypeAndEffectiveDate(
                "AAPL", ActionType.DIVIDEND, DIV_DATE).size());
    }

    @Test
    void uniqueConstraintRejectsExactDuplicate() {
        // Identity id generation inserts on persist, so the violation surfaces there already.
        assertThrows(RuntimeException.class, () -> {
            action("AAPL", ActionType.DIVIDEND, DIV_DATE, 0.25, SourceType.SEC_EQUITY_XBRL);
            em.flush();
        });
    }
}

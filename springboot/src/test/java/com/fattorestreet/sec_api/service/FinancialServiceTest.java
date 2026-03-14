package com.fattorestreet.sec_api.service;

import com.fattorestreet.sec_api.model.Quarter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FinancialServiceTest {

    private FinancialService financialService;

    @BeforeEach
    void setUp() {
        financialService = new FinancialService();
    }

    private Quarter buildQuarter(LocalDate periodEnd, Long revenue, Long netIncome,
                                  Long ocf, Long grossProfit, Long operatingIncome,
                                  Long assets, Long liabilities, Long equity,
                                  Long cash, Long inventory, Double epsBasic) {
        Quarter q = new Quarter();
        q.setPeriodEnd(periodEnd);
        q.setRevenues(revenue);
        q.setNetIncomeLoss(netIncome);
        q.setNetCashProvidedByUsedInOperatingActivities(ocf);
        q.setGrossProfit(grossProfit);
        q.setOperatingIncomeLoss(operatingIncome);
        q.setAssets(assets);
        q.setLiabilities(liabilities);
        q.setStockholdersEquity(equity);
        q.setCashAndCashEquivalentsAtCarryingValue(cash);
        q.setInventoryNet(inventory);
        q.setEarningsPerShareBasic(epsBasic);
        return q;
    }

    @Test
    void calculateMetrics_withEightQuarters_returnsTTMAndYoY() {
        List<Quarter> quarters = new ArrayList<>();
        // Current 4 quarters (most recent first by periodEnd; calculateMetrics sorts internally)
        quarters.add(buildQuarter(LocalDate.of(2024, 12, 31), 100L, 25L, 30L, 50L, 35L,
                500L, 300L, 200L, 80L, 40L, 1.50));
        quarters.add(buildQuarter(LocalDate.of(2024, 9, 30), 90L, 20L, 28L, 45L, 30L,
                480L, 290L, 190L, 75L, 38L, 1.40));
        quarters.add(buildQuarter(LocalDate.of(2024, 6, 30), 85L, 18L, 25L, 42L, 28L,
                470L, 285L, 185L, 70L, 35L, 1.30));
        quarters.add(buildQuarter(LocalDate.of(2024, 3, 31), 80L, 15L, 22L, 40L, 25L,
                460L, 280L, 180L, 65L, 33L, 1.20));
        // Previous 4 quarters
        quarters.add(buildQuarter(LocalDate.of(2023, 12, 31), 70L, 12L, 20L, 35L, 22L,
                450L, 275L, 175L, 60L, 30L, 1.10));
        quarters.add(buildQuarter(LocalDate.of(2023, 9, 30), 65L, 10L, 18L, 32L, 20L,
                440L, 270L, 170L, 55L, 28L, 1.00));
        quarters.add(buildQuarter(LocalDate.of(2023, 6, 30), 60L, 8L, 15L, 30L, 18L,
                430L, 265L, 165L, 50L, 25L, 0.90));
        quarters.add(buildQuarter(LocalDate.of(2023, 3, 31), 55L, 6L, 12L, 28L, 15L,
                420L, 260L, 160L, 45L, 22L, 0.80));

        Map<String, Object> metrics = financialService.calculateMetrics(quarters);

        // TTM = sum of most recent 4 quarters
        assertEquals(355.0, (double) metrics.get("ttmRevenue"), 0.01);
        assertEquals(78.0, (double) metrics.get("ttmNetIncome"), 0.01);
        assertEquals(105.0, (double) metrics.get("ttmOperatingCashFlow"), 0.01);
        assertEquals(177.0, (double) metrics.get("ttmGrossProfit"), 0.01);
        assertEquals(118.0, (double) metrics.get("ttmOperatingIncome"), 0.01);

        // Previous TTM revenue = 70+65+60+55 = 250
        double prevTTMRevenue = 250.0;
        double expectedRevenueYoY = (355.0 - prevTTMRevenue) / Math.abs(prevTTMRevenue);
        assertEquals(expectedRevenueYoY, (Double) metrics.get("ttmRevenueYoY"), 0.001);

        // Previous TTM net income = 12+10+8+6 = 36
        double prevTTMNetIncome = 36.0;
        double expectedNetIncomeYoY = (78.0 - prevTTMNetIncome) / Math.abs(prevTTMNetIncome);
        assertEquals(expectedNetIncomeYoY, (Double) metrics.get("ttmNetIncomeYoY"), 0.001);

        // Latest balance sheet = most recent quarter (2024-12-31)
        assertEquals(500L, metrics.get("latestAssets"));
        assertEquals(300L, metrics.get("latestLiabilities"));
        assertEquals(200L, metrics.get("latestEquity"));
        assertEquals(80L, metrics.get("latestCash"));
        assertEquals(40L, metrics.get("latestInventory"));
        assertEquals(1.50, metrics.get("latestEps"));

        // Ratios
        assertNotNull(metrics.get("netMargin"));
        assertNotNull(metrics.get("grossMargin"));
        assertNotNull(metrics.get("debtToAssets"));
        assertNotNull(metrics.get("roA"));
        assertNotNull(metrics.get("cashToLiabilities"));
        assertNotNull(metrics.get("ocfToNetIncome"));
    }

    @Test
    void calculateMetrics_withFourQuarters_returnsTTMButNoYoY() {
        List<Quarter> quarters = new ArrayList<>();
        quarters.add(buildQuarter(LocalDate.of(2024, 12, 31), 100L, 25L, 30L, 50L, 35L,
                500L, 300L, 200L, 80L, 40L, 1.50));
        quarters.add(buildQuarter(LocalDate.of(2024, 9, 30), 90L, 20L, 28L, 45L, 30L,
                480L, 290L, 190L, 75L, 38L, 1.40));
        quarters.add(buildQuarter(LocalDate.of(2024, 6, 30), 85L, 18L, 25L, 42L, 28L,
                470L, 285L, 185L, 70L, 35L, 1.30));
        quarters.add(buildQuarter(LocalDate.of(2024, 3, 31), 80L, 15L, 22L, 40L, 25L,
                460L, 280L, 180L, 65L, 33L, 1.20));

        Map<String, Object> metrics = financialService.calculateMetrics(quarters);

        assertEquals(355.0, (double) metrics.get("ttmRevenue"), 0.01);
        assertEquals(78.0, (double) metrics.get("ttmNetIncome"), 0.01);

        // YoY requires 8 quarters; previous TTM returns 0 so calculateYoY returns null
        assertNull(metrics.get("ttmRevenueYoY"));
        assertNull(metrics.get("ttmNetIncomeYoY"));
    }

    @Test
    void calculateMetrics_withEmptyList_returnsEmptyMap() {
        Map<String, Object> metrics = financialService.calculateMetrics(Collections.emptyList());
        assertTrue(metrics.isEmpty());
    }

    @Test
    void calculateMetrics_withNullList_returnsEmptyMap() {
        Map<String, Object> metrics = financialService.calculateMetrics(null);
        assertTrue(metrics.isEmpty());
    }

    @Test
    void calculateTTMRevenue_withNullFields_treatsAsZero() {
        List<Quarter> quarters = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Quarter q = new Quarter();
            q.setPeriodEnd(LocalDate.of(2024, 12 - i * 3, 28));
            // revenue is null for all
            quarters.add(q);
        }
        quarters.sort(Comparator.comparing(Quarter::getPeriodEnd).reversed());

        double ttm = financialService.calculateTTMRevenue(quarters, 0);
        assertEquals(0.0, ttm);
    }

    @Test
    void calculateTTMRevenue_insufficientQuarters_returnsZero() {
        List<Quarter> quarters = List.of(
                buildQuarter(LocalDate.of(2024, 12, 31), 100L, 25L, 30L, 50L, 35L,
                        500L, 300L, 200L, 80L, 40L, 1.50));

        double ttm = financialService.calculateTTMRevenue(quarters, 0);
        assertEquals(0.0, ttm);
    }

    @Test
    void calculateMetrics_zeroAssets_skipsDebtAndROA() {
        List<Quarter> quarters = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            quarters.add(buildQuarter(LocalDate.of(2024, 12 - i * 3, 28),
                    100L, 25L, 30L, 50L, 35L,
                    0L, 300L, 200L, 80L, 40L, 1.50));
        }

        Map<String, Object> metrics = financialService.calculateMetrics(quarters);

        assertFalse(metrics.containsKey("debtToAssets"));
        assertFalse(metrics.containsKey("roA"));
    }
}

package com.example.sec_api.service;

import com.example.sec_api.model.Quarter;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FinancialService {

    public Map<String, Object> calculateMetrics(List<Quarter> quarters) {
        if (quarters == null || quarters.isEmpty()) {
            return Collections.emptyMap();
        }

        // Sort by period end descending
        quarters.sort(Comparator.comparing(Quarter::getPeriodEnd).reversed());

        Map<String, Object> metrics = new HashMap<>();

        // Latest TTM
        double currentTTMNetIncome = calculateTTMNetIncome(quarters, 0);
        double currentTTMRevenue = calculateTTMRevenue(quarters, 0);
        double currentTTMOCF = calculateTTMOperatingCashFlow(quarters, 0);
        double currentTTMGrossProfit = calculateTTMGrossProfit(quarters, 0);
        double currentTTMOperatingIncome = calculateTTMOperatingIncome(quarters, 0);

        // Previous TTM (shifted by 4 quarters)
        double previousTTMNetIncome = calculateTTMNetIncome(quarters, 4);
        double previousTTMRevenue = calculateTTMRevenue(quarters, 4);

        metrics.put("ttmNetIncome", currentTTMNetIncome);
        metrics.put("ttmRevenue", currentTTMRevenue);
        metrics.put("ttmOperatingCashFlow", currentTTMOCF);
        metrics.put("ttmGrossProfit", currentTTMGrossProfit);
        metrics.put("ttmOperatingIncome", currentTTMOperatingIncome);

        metrics.put("ttmNetIncomeYoY", calculateYoY(currentTTMNetIncome, previousTTMNetIncome));
        metrics.put("ttmRevenueYoY", calculateYoY(currentTTMRevenue, previousTTMRevenue));

        // Latest point-in-time metrics
        Quarter latest = quarters.get(0);
        metrics.put("latestAssets", latest.getAssets());
        metrics.put("latestLiabilities", latest.getLiabilities());
        metrics.put("latestEquity", latest.getStockholdersEquity());
        metrics.put("latestInventory", latest.getInventoryNet());
        metrics.put("latestCash", latest.getCashAndCashEquivalentsAtCarryingValue());
        metrics.put("latestEps", latest.getEarningsPerShareBasic());

        // Ratios
        if (currentTTMRevenue != 0) {
            metrics.put("netMargin", currentTTMNetIncome / currentTTMRevenue);
            metrics.put("grossMargin", currentTTMGrossProfit / currentTTMRevenue);
        }

        if (latest.getAssets() != null && latest.getAssets() != 0) {
            metrics.put("debtToAssets", (latest.getLiabilities() != null ? latest.getLiabilities().doubleValue() : 0.0)
                    / latest.getAssets());
            metrics.put("roA", currentTTMNetIncome / latest.getAssets());
        }

        if (latest.getLiabilities() != null && latest.getLiabilities() != 0) {
            metrics.put("cashToLiabilities",
                    (latest.getCashAndCashEquivalentsAtCarryingValue() != null
                            ? latest.getCashAndCashEquivalentsAtCarryingValue().doubleValue()
                            : 0.0) / latest.getLiabilities());
        }

        if (currentTTMNetIncome != 0) {
            metrics.put("ocfToNetIncome", currentTTMOCF / currentTTMNetIncome);
        }

        return metrics;
    }

    // Refined TTM calculation
    public double calculateTTMNetIncome(List<Quarter> quarters, int offset) {
        if (quarters.size() < offset + 4)
            return 0.0;
        return quarters.subList(offset, offset + 4).stream()
                .mapToLong(q -> q.getNetIncomeLoss() != null ? q.getNetIncomeLoss() : 0L)
                .sum();
    }

    public double calculateTTMRevenue(List<Quarter> quarters, int offset) {
        if (quarters.size() < offset + 4)
            return 0.0;
        return quarters.subList(offset, offset + 4).stream()
                .mapToLong(q -> q.getRevenues() != null ? q.getRevenues() : 0L)
                .sum();
    }

    public double calculateTTMOperatingCashFlow(List<Quarter> quarters, int offset) {
        if (quarters.size() < offset + 4)
            return 0.0;
        return quarters.subList(offset, offset + 4).stream()
                .mapToLong(q -> q.getNetCashProvidedByUsedInOperatingActivities() != null
                        ? q.getNetCashProvidedByUsedInOperatingActivities()
                        : 0L)
                .sum();
    }

    public double calculateTTMGrossProfit(List<Quarter> quarters, int offset) {
        if (quarters.size() < offset + 4)
            return 0.0;
        return quarters.subList(offset, offset + 4).stream()
                .mapToLong(q -> q.getGrossProfit() != null ? q.getGrossProfit() : 0L)
                .sum();
    }

    public double calculateTTMOperatingIncome(List<Quarter> quarters, int offset) {
        if (quarters.size() < offset + 4)
            return 0.0;
        return quarters.subList(offset, offset + 4).stream()
                .mapToLong(q -> q.getOperatingIncomeLoss() != null ? q.getOperatingIncomeLoss() : 0L)
                .sum();
    }

    private Double calculateYoY(double current, double previous) {
        if (previous == 0)
            return null;
        return (current - previous) / Math.abs(previous);
    }
}

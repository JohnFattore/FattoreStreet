package com.example.sec_api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.example.sec_api.model.Asset;
import com.example.sec_api.model.Quarter;
import com.example.sec_api.repository.QuarterRepository;

@Service
public class QuarterService {

    private final QuarterRepository quarterRepository;

    // constructor
    public QuarterService(QuarterRepository quarterRepository) {
        this.quarterRepository = quarterRepository;
    }

    public Quarter saveQuarter(Quarter quarter) {
        return quarterRepository.save(quarter);
    }

    public Quarter createOrUpdateQuarter(Quarter quarter) {
        if (quarter.getYear() != null && quarter.getQuarter() != null) {
            return quarterRepository.findByAssetAndYearAndQuarter(quarter.getAsset(), quarter.getYear(),
                    quarter.getQuarter())
                    .map(existing -> {
                        updateQuarterFields(existing, quarter);
                        return quarterRepository.save(existing);
                    })
                    .orElseGet(() -> {
                        // secondary check by dates if year/quarter lookup failed
                        return quarterRepository.findByAssetAndPeriodStartAndPeriodEnd(quarter.getAsset(),
                                quarter.getPeriodStart(), quarter.getPeriodEnd())
                                .map(existing -> {
                                    updateQuarterFields(existing, quarter);
                                    return quarterRepository.save(existing);
                                })
                                .orElseGet(() -> quarterRepository.save(quarter));
                    });
        }

        if (quarter.getPeriodStart() != null && quarter.getPeriodEnd() != null) {
            return quarterRepository
                    .findByAssetAndPeriodStartAndPeriodEnd(quarter.getAsset(), quarter.getPeriodStart(),
                            quarter.getPeriodEnd())
                    .map(existing -> {
                        updateQuarterFields(existing, quarter);
                        return quarterRepository.save(existing);
                    })
                    .orElseGet(() -> quarterRepository.save(quarter));
        }
        return quarterRepository.save(quarter);
    }

    public void updateAssetQuarters(Asset asset, List<Quarter> newQuarters) {
        // Safeguard: Merge duplicates within the incoming list first
        Map<String, Quarter> consolidatedNew = new HashMap<>();
        for (Quarter q : newQuarters) {
            if (q.getPeriodStart() == null || q.getPeriodEnd() == null)
                continue;
            String key = q.getPeriodStart() + "|" + q.getPeriodEnd();
            if (consolidatedNew.containsKey(key)) {
                updateQuarterFields(consolidatedNew.get(key), q);
            } else {
                consolidatedNew.put(key, q);
            }
        }

        List<Quarter> existingQuarters = quarterRepository.findByAsset(asset);
        Map<String, Quarter> existingMap = existingQuarters.stream()
                .filter(q -> q.getPeriodStart() != null && q.getPeriodEnd() != null)
                .collect(Collectors.toMap(q -> q.getPeriodStart() + "|" + q.getPeriodEnd(), q -> q, (a, b) -> a));

        List<Quarter> toSave = new ArrayList<>();
        for (Quarter newQ : consolidatedNew.values()) {
            Quarter existing = existingMap.get(newQ.getPeriodStart() + "|" + newQ.getPeriodEnd());

            if (existing != null) {
                updateQuarterFields(existing, newQ);
                toSave.add(existing);
            } else {
                toSave.add(newQ);
            }
        }
        quarterRepository.saveAll(toSave);
    }

    private void updateQuarterFields(Quarter existing, Quarter source) {
        if (source.getPeriodStart() != null)
            existing.setPeriodStart(source.getPeriodStart());
        if (source.getPeriodEnd() != null)
            existing.setPeriodEnd(source.getPeriodEnd());
        if (source.getYear() != null)
            existing.setYear(source.getYear());
        if (source.getQuarter() != null)
            existing.setQuarter(source.getQuarter());

        if (source.getRevenues() != null)
            existing.setRevenues(source.getRevenues());
        if (source.getNetIncomeLoss() != null)
            existing.setNetIncomeLoss(source.getNetIncomeLoss());
        if (source.getOperatingIncomeLoss() != null)
            existing.setOperatingIncomeLoss(source.getOperatingIncomeLoss());
        if (source.getGrossProfit() != null)
            existing.setGrossProfit(source.getGrossProfit());
        if (source.getEarningsPerShareBasic() != null)
            existing.setEarningsPerShareBasic(source.getEarningsPerShareBasic());
        if (source.getEarningsPerShareDiluted() != null)
            existing.setEarningsPerShareDiluted(source.getEarningsPerShareDiluted());

        if (source.getAssets() != null)
            existing.setAssets(source.getAssets());
        if (source.getLiabilities() != null)
            existing.setLiabilities(source.getLiabilities());
        if (source.getStockholdersEquity() != null)
            existing.setStockholdersEquity(source.getStockholdersEquity());
        if (source.getCashAndCashEquivalentsAtCarryingValue() != null)
            existing.setCashAndCashEquivalentsAtCarryingValue(source.getCashAndCashEquivalentsAtCarryingValue());
        if (source.getAccountsReceivableNetCurrent() != null)
            existing.setAccountsReceivableNetCurrent(source.getAccountsReceivableNetCurrent());
        if (source.getInventoryNet() != null)
            existing.setInventoryNet(source.getInventoryNet());

        if (source.getNetCashProvidedByUsedInOperatingActivities() != null)
            existing
                    .setNetCashProvidedByUsedInOperatingActivities(
                            source.getNetCashProvidedByUsedInOperatingActivities());
        if (source.getPaymentsOfDividends() != null)
            existing.setPaymentsOfDividends(source.getPaymentsOfDividends());
        if (source.getPaymentsForRepurchaseOfCommonStock() != null)
            existing.setPaymentsForRepurchaseOfCommonStock(source.getPaymentsForRepurchaseOfCommonStock());
    }
}
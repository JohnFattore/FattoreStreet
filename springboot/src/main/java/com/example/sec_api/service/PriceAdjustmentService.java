package com.example.sec_api.service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.sec_api.model.Asset;
import com.example.sec_api.model.CorporateAction;
import com.example.sec_api.model.CorporateAction.ActionType;
import com.example.sec_api.model.DailyPrice;
import com.example.sec_api.model.Listing;
import com.example.sec_api.repository.AssetRepository;
import com.example.sec_api.repository.CorporateActionRepository;
import com.example.sec_api.repository.DailyPriceRepository;

@Service
public class PriceAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(PriceAdjustmentService.class);

    private final DailyPriceRepository dailyPriceRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final AssetRepository assetRepository;
    private final SplitDividendService splitDividendService;

    public PriceAdjustmentService(DailyPriceRepository dailyPriceRepository,
                                  CorporateActionRepository corporateActionRepository,
                                  AssetRepository assetRepository,
                                  SplitDividendService splitDividendService) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.assetRepository = assetRepository;
        this.splitDividendService = splitDividendService;
    }

    /**
     * Detect corporate actions from SEC and adjust prices for a single ticker.
     * @return summary map
     */
    public Map<String, Object> adjustTicker(String ticker) {
        Asset asset = assetRepository.findByListings_Ticker(ticker);
        if (asset == null) {
            return Map.of("ticker", ticker, "status", "no_asset", "pricesUpdated", 0);
        }

        int newActions = splitDividendService.detectAndPersist(ticker, asset.getCik());

        List<CorporateAction> actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker);
        int splits = (int) actions.stream().filter(a -> a.getActionType() == ActionType.SPLIT).count();
        int dividends = (int) actions.stream().filter(a -> a.getActionType() == ActionType.DIVIDEND).count();

        int updated = applyAdjustments(ticker, actions);

        return Map.of(
                "ticker", ticker,
                "status", "ok",
                "newActions", newActions,
                "splits", splits,
                "dividends", dividends,
                "pricesUpdated", updated
        );
    }

    /**
     * Detect and adjust for all tickers that have price data and a corresponding SEC CIK.
     *
     * @param force when false, skips SEC re-fetch for tickers that already have corporate
     *              actions detected; when true, re-fetches SEC data for all tickers to
     *              catch new splits/dividends
     */
    public Map<String, Object> adjustAllTickers(boolean force) {
        Set<String> tickersNeedingAdjustment = new HashSet<>(dailyPriceRepository.findTickersWithUnadjustedPrices());
        Set<String> tickersWithExistingActions = new HashSet<>(corporateActionRepository.findDistinctTickers());

        Map<String, Asset> assetByTicker = assetRepository.findAll().stream()
                .flatMap(a -> a.getListings().stream()
                        .map(listing -> Map.entry(listing.getTicker(), a)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

        Set<String> allPriceTickers = new HashSet<>(dailyPriceRepository.findDistinctTickers());

        Set<String> tickersToProcess = new HashSet<>(tickersNeedingAdjustment);
        if (force) {
            tickersToProcess.addAll(allPriceTickers);
        }

        log.info("Adjust prices: {} total price tickers, {} needing adjustment, {} with existing actions, force={}",
                allPriceTickers.size(), tickersNeedingAdjustment.size(), tickersWithExistingActions.size(), force);

        int tickersProcessed = 0;
        int totalSplits = 0;
        int totalDividends = 0;
        int totalPricesUpdated = 0;
        int skippedNoAsset = 0;

        for (String ticker : tickersToProcess) {
            Asset asset = assetByTicker.get(ticker);
            if (asset == null) {
                if (tickersNeedingAdjustment.contains(ticker)) {
                    setRawAsAdjusted(ticker);
                }
                skippedNoAsset++;
                continue;
            }

            try {
                boolean hasExistingActions = tickersWithExistingActions.contains(ticker);
                boolean shouldFetchSec = force || !hasExistingActions;
                int newActions = 0;

                if (shouldFetchSec) {
                    newActions = splitDividendService.detectAndPersist(ticker, asset.getCik());
                    Thread.sleep(100);
                }

                List<CorporateAction> actions = corporateActionRepository.findByTickerOrderByEffectiveDateDesc(ticker);
                totalSplits += actions.stream().filter(a -> a.getActionType() == ActionType.SPLIT).count();
                totalDividends += actions.stream().filter(a -> a.getActionType() == ActionType.DIVIDEND).count();
                totalPricesUpdated += applyAdjustments(ticker, actions);
                tickersProcessed++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[{}] Error during adjustment: {}", ticker, e.getMessage());
                if (tickersNeedingAdjustment.contains(ticker)) {
                    setRawAsAdjusted(ticker);
                }
            }
        }

        log.info("Adjustment complete. Processed: {}, Skipped (no asset): {}, Splits: {}, Dividends: {}, Prices updated: {}",
                tickersProcessed, skippedNoAsset, totalSplits, totalDividends, totalPricesUpdated);

        return Map.of(
                "tickersProcessed", tickersProcessed,
                "skippedNoAsset", skippedNoAsset,
                "totalSplits", totalSplits,
                "totalDividends", totalDividends,
                "totalPricesUpdated", totalPricesUpdated
        );
    }

    /**
     * Apply cumulative adjustment factors to all DailyPrice records for a ticker.
     * Walks from most recent to oldest, accumulating split and dividend factors.
     */
    private int applyAdjustments(String ticker, List<CorporateAction> actions) {
        List<DailyPrice> prices = dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
        if (prices.isEmpty()) return 0;

        if (actions.isEmpty()) {
            for (DailyPrice dp : prices) {
                dp.setAdjustedOpen(dp.getOpenPrice());
                dp.setAdjustedHigh(dp.getHighPrice());
                dp.setAdjustedLow(dp.getLowPrice());
                dp.setAdjustedClose(dp.getClosePrice());
            }
            dailyPriceRepository.saveAll(prices);
            return prices.size();
        }

        Map<LocalDate, List<CorporateAction>> actionsByDate = new HashMap<>();
        for (CorporateAction a : actions) {
            actionsByDate.computeIfAbsent(a.getEffectiveDate(), k -> new ArrayList<>()).add(a);
        }

        double cumulativeFactor = 1.0;
        Double prevClose = null;

        for (DailyPrice dp : prices) {
            LocalDate date = dp.getTradeDate();
            List<CorporateAction> dayActions = actionsByDate.get(date);

            if (dayActions != null) {
                for (CorporateAction action : dayActions) {
                    if (action.getActionType() == ActionType.SPLIT) {
                        cumulativeFactor *= action.getRatio();
                    } else if (action.getActionType() == ActionType.DIVIDEND && prevClose != null && prevClose > 0) {
                        cumulativeFactor *= (1.0 - action.getRatio() / prevClose);
                    }
                }
            }

            dp.setAdjustedOpen(round(dp.getOpenPrice() * cumulativeFactor));
            dp.setAdjustedHigh(round(dp.getHighPrice() * cumulativeFactor));
            dp.setAdjustedLow(round(dp.getLowPrice() * cumulativeFactor));
            dp.setAdjustedClose(round(dp.getClosePrice() * cumulativeFactor));

            prevClose = dp.getClosePrice();
        }

        dailyPriceRepository.saveAll(prices);
        return prices.size();
    }

    /** For tickers without SEC data, set adjusted = raw. */
    private void setRawAsAdjusted(String ticker) {
        List<DailyPrice> prices = dailyPriceRepository.findByTickerOrderByTradeDateDesc(ticker);
        for (DailyPrice dp : prices) {
            dp.setAdjustedOpen(dp.getOpenPrice());
            dp.setAdjustedHigh(dp.getHighPrice());
            dp.setAdjustedLow(dp.getLowPrice());
            dp.setAdjustedClose(dp.getClosePrice());
        }
        if (!prices.isEmpty()) {
            dailyPriceRepository.saveAll(prices);
        }
    }

    private Double round(Double val) {
        if (val == null) return null;
        return Math.round(val * 10000.0) / 10000.0;
    }
}

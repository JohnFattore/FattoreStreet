package com.fattorestreet.sec_api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fattorestreet.sec_api.index.IwbHoldingsTickerSet;

/**
 * Serves bundled iShares IWB (Russell 1000 ETF) reference weights for benchmark UI.
 */
@RestController
public class IwbReferenceHoldingsController {

    private final IwbHoldingsTickerSet iwbHoldingsTickerSet;

    public IwbReferenceHoldingsController(IwbHoldingsTickerSet iwbHoldingsTickerSet) {
        this.iwbHoldingsTickerSet = iwbHoldingsTickerSet;
    }

    public record IwbReferenceHoldingRow(String ticker, double weightPercent) {
    }

    @GetMapping("/iwb-reference-holdings")
    public List<IwbReferenceHoldingRow> listIwbReferenceHoldings() {
        return iwbHoldingsTickerSet.equityRows().stream()
                .map(r -> new IwbReferenceHoldingRow(r.ticker(), r.weightPercent().doubleValue()))
                .toList();
    }
}

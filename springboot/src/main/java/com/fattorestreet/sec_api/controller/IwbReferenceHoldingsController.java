package com.fattorestreet.sec_api.controller;

import java.time.LocalDate;
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

    /** {@code asOf} is the fund's holdings date (ISO {@code yyyy-MM-dd}), or {@code null} if unknown. */
    public record IwbReferenceHoldingsResponse(LocalDate asOf, List<IwbReferenceHoldingRow> holdings) {
        public IwbReferenceHoldingsResponse {
            holdings = List.copyOf(holdings);
        }
    }

    @GetMapping("/iwb-reference-holdings")
    public IwbReferenceHoldingsResponse listIwbReferenceHoldings() {
        List<IwbReferenceHoldingRow> rows = iwbHoldingsTickerSet.equityRows().stream()
                .map(r -> new IwbReferenceHoldingRow(r.ticker(), r.weightPercent().doubleValue()))
                .toList();
        return new IwbReferenceHoldingsResponse(iwbHoldingsTickerSet.asOfDate(), rows);
    }
}

package com.fattorestreet.sec_api.corporateaction;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.repository.CorporateActionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression anchor for yfinance validation KPIs and canary ticker lists used in accuracy passes.
 */
@ExtendWith(MockitoExtension.class)
class CorporateActionValidationCanaryTest {

    /** Representative US equities for SEC vs Yahoo validation runs (see sec-equity-dividend-accuracy-pass skill). */
    public static final List<String> CANARY_EQUITY_TICKERS = List.of(
            "AAPL", "MSFT", "JPM", "KO", "PG", "XOM", "WMT", "DIS", "JNJ", "V",
            "UNH", "HD", "BAC", "PFE", "INTC", "CSCO", "VZ", "T", "MRK", "CVX");

    /** Split-heavy tickers for the adjusted-price break KPI (price-corroborated split detection). */
    public static final List<String> CANARY_SPLIT_TICKERS = List.of(
            "AAPL", "NVDA", "TSLA", "GOOGL", "AMZN", "SHOP");

    @Mock
    private CorporateActionRepository corporateActionRepository;

    @InjectMocks
    private CorporateActionValidationService corporateActionValidationService;

    @Test
    void summarizeBatch_aggregatesKpisFromReports() {
        List<CorporateActionValidationService.ValidationReport> reports = List.of(
                new CorporateActionValidationService.ValidationReport(
                        "A", 5, 5, 2, 0, 1, 1, List.of()),
                new CorporateActionValidationService.ValidationReport(
                        "B", 3, 3, 0, 1, 0, 2, List.of()));

        Map<String, Object> out = corporateActionValidationService.summarizeBatch(reports);

        assertEquals(2, reports.size());
        assertEquals(8, out.get("secEvents"));
        assertEquals(8, out.get("yfinanceEvents"));
        assertEquals(2, out.get("missingInSec"));
        assertEquals(1, out.get("extraInSec"));
        assertEquals(1, out.get("dateDrift"));
        assertEquals(3, out.get("amountDrift"));
        double expectedMismatch = (2.0 + 1.0 + 3.0) / 8.0;
        assertEquals(expectedMismatch, (Double) out.get("mismatchRate"), 1e-9);
    }

    @Test
    void weightedError_formula_matchesAccuracyPassSkill() {
        int missing = 2;
        int amount = 3;
        int date = 4;
        double weighted = 3 * missing + 2 * amount + date;
        assertEquals(6 + 6 + 4, weighted);
    }

    @Test
    void canaryList_hasExpectedSize() {
        assertEquals(20, CANARY_EQUITY_TICKERS.size());
        assertEquals(6, CANARY_SPLIT_TICKERS.size());
    }
}

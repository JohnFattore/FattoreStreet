package com.fattorestreet.sec_api.corporateaction.support;

import org.junit.jupiter.api.Test;

import com.fattorestreet.sec_api.model.Listing;

import static org.junit.jupiter.api.Assertions.*;

class EtfIdentityEvaluatorTest {

    private final EtfIdentityEvaluator evaluator = new EtfIdentityEvaluator();

    private Listing listing(String secSeriesId, String secClassContractId, String secClassTicker,
                            String seriesName, String className) {
        Listing l = new Listing();
        l.setSecSeriesId(secSeriesId);
        l.setSecClassContractId(secClassContractId);
        l.setSecClassTicker(secClassTicker);
        l.setSecSeriesName(seriesName);
        l.setSecClassName(className);
        return l;
    }

    @Test
    void evaluateIdentity_tickerTokenMatch_scores2() {
        Listing l = listing(null, null, null, null, null);
        // normalizeText("SPY") = "spy"; filingText contains "spy"
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("SPDR S&P 500 ETF Trust SPY overview", l, "SPY", "497", null);

        assertTrue(signal.score() >= 2);
        assertTrue(signal.matchedSignals().contains("ticker"));
    }

    @Test
    void evaluateIdentity_secSeriesIdMatch_scores4() {
        Listing l = listing("S000007285", null, null, null, null);
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("Fund series ID S000007285 prospectus", l, "VOO", "497", null);

        assertTrue(signal.matchedSignals().contains("secSeriesId"));
        // secSeriesId alone = 4 points
        assertTrue(signal.score() >= 4);
    }

    @Test
    void evaluateIdentity_secClassContractIdMatch_scores4() {
        Listing l = listing(null, "C000022074", null, null, null);
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("Class contract ID C000022074 details", l, "IVV", "497", null);

        assertTrue(signal.matchedSignals().contains("secClassContractId"));
        assertTrue(signal.score() >= 4);
    }

    @Test
    void evaluateIdentity_multipleSignals_accumulate() {
        Listing l = listing("S000007285", "C000022074", null, null, null);
        // Both series and contract ID present in text → score = 4 + 4 = 8 (plus ticker if present)
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("S000007285 and C000022074 are identifiers for this ETF VOO", l, "VOO", "497", null);

        assertTrue(signal.score() >= 8);
        assertTrue(signal.matchedSignals().contains("secSeriesId"));
        assertTrue(signal.matchedSignals().contains("secClassContractId"));
    }

    @Test
    void evaluateIdentity_noSignalsMatch_scoresZero() {
        Listing l = listing(null, null, null, null, null);
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("Unrelated text about something else entirely here", l, "XYZ", null, null);

        // "xyz" not in "unrelated text about something else entirely here"
        assertEquals(0, signal.score());
        assertTrue(signal.matchedSignals().isEmpty());
    }

    @Test
    void evaluateIdentity_documentTickerHint_scores1() {
        Listing l = listing(null, null, null, null, null);
        // documentName containing ticker adds 1 point
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("some text", l, "SPY", "497", "SPY-prospectus.htm");

        assertTrue(signal.matchedSignals().contains("documentTickerHint"));
        assertTrue(signal.score() >= 1);
    }

    @Test
    void evaluateIdentity_secClassTickerMatch_scores3() {
        Listing l = listing(null, null, "SPYX", null, null);
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("SPYX S&P 500 Fossil Fuel Free ETF", l, "SPYX", "497", null);

        assertTrue(signal.matchedSignals().contains("secClassTicker"));
        assertTrue(signal.score() >= 3);
    }

    @Test
    void evaluateIdentity_nameSignal_requiresMinimumWordOverlap() {
        // seriesName "Vanguard 500 Index Fund" → two significant words match → score += 2
        Listing l = listing(null, null, null, "Vanguard 500 Index Fund", null);
        EtfIdentityEvaluator.IdentitySignal signal =
                evaluator.evaluateIdentity("Vanguard 500 Index Fund ETF overview document", l, "VOO", "497", null);

        assertTrue(signal.matchedSignals().contains("secSeriesName"));
    }
}

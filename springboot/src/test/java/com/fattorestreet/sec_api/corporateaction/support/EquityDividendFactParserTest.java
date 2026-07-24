package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class EquityDividendFactParserTest {

    private final EquityDividendFactParser parser = new EquityDividendFactParser();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseDividendFacts_validQuarterlyFact_parsed() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "CommonStockDividendsPerShareDeclared": {
                        "units": {
                          "USD/shares": [
                            {"form":"10-Q","val":0.22,"end":"2024-03-31","start":"2024-01-01","filed":"2024-04-30"}
                          ]
                        }
                      }
                    }
                  }
                }""");

        List<EquityCorporateActionService.DividendFact> facts = parser.parseDividendFacts(root, "AAPL");

        assertEquals(1, facts.size());
        assertEquals(0.22, facts.get(0).value(), 1e-9);
        assertEquals(LocalDate.of(2024, 3, 31), facts.get(0).endDate());
        assertEquals(LocalDate.of(2024, 1, 1), facts.get(0).startDate());
        assertEquals("10-Q", facts.get(0).form());
        assertEquals("CommonStockDividendsPerShareDeclared", facts.get(0).concept());
    }

    @Test
    void parseDividendFacts_irrelevantForm_skipped() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "CommonStockDividendsPerShareDeclared": {
                        "units": {
                          "USD/shares": [
                            {"form":"SC 13G","val":0.22,"end":"2024-03-31"}
                          ]
                        }
                      }
                    }
                  }
                }""");

        List<EquityCorporateActionService.DividendFact> facts = parser.parseDividendFacts(root, "AAPL");

        assertTrue(facts.isEmpty());
    }

    @Test
    void parseDividendFacts_nonUsdPerShareUnit_skipped() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "CommonStockDividendsPerShareDeclared": {
                        "units": {
                          "USD": [
                            {"form":"10-Q","val":0.22,"end":"2024-03-31","start":"2024-01-01"}
                          ]
                        }
                      }
                    }
                  }
                }""");

        List<EquityCorporateActionService.DividendFact> facts = parser.parseDividendFacts(root, "AAPL");

        assertTrue(facts.isEmpty());
    }

    @Test
    void parseDividendFacts_negativeOrZeroValue_skipped() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "CommonStockDividendsPerShareDeclared": {
                        "units": {
                          "USD/shares": [
                            {"form":"10-Q","val":-0.22,"end":"2024-03-31"},
                            {"form":"10-Q","val":0,"end":"2024-06-30"}
                          ]
                        }
                      }
                    }
                  }
                }""");

        List<EquityCorporateActionService.DividendFact> facts = parser.parseDividendFacts(root, "AAPL");

        assertTrue(facts.isEmpty());
    }

    @Test
    void parseDividendFacts_deduplicatesIdenticalRows() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "CommonStockDividendsPerShareDeclared": {
                        "units": {
                          "USD/shares": [
                            {"form":"10-Q","val":0.22,"end":"2024-03-31","start":"2024-01-01","filed":"2024-04-30"},
                            {"form":"10-Q","val":0.22,"end":"2024-03-31","start":"2024-01-01","filed":"2024-04-30"}
                          ]
                        }
                      }
                    }
                  }
                }""");

        List<EquityCorporateActionService.DividendFact> facts = parser.parseDividendFacts(root, "AAPL");

        assertEquals(1, facts.size());
    }

    @Test
    void parseDividendFacts_emptyOrMissingUsGaap_returnsEmpty() throws Exception {
        JsonNode emptyRoot = mapper.readTree("{}");
        assertTrue(parser.parseDividendFacts(emptyRoot, "AAPL").isEmpty());

        JsonNode noUsGaap = mapper.readTree("""
                {"facts":{"dei":{}}}""");
        assertTrue(parser.parseDividendFacts(noUsGaap, "AAPL").isEmpty());
    }

    @Test
    void parseDividendFacts_nonPreferredDividendConcept_alsoAccepted() throws Exception {
        JsonNode root = mapper.readTree("""
                {
                  "facts": {
                    "us-gaap": {
                      "PreferredStockDividendsPerShare": {
                        "units": {
                          "USD/shares": [
                            {"form":"10-Q","val":0.50,"end":"2024-03-31","start":"2024-01-01"}
                          ]
                        }
                      }
                    }
                  }
                }""");

        List<EquityCorporateActionService.DividendFact> facts = parser.parseDividendFacts(root, "PSA");

        assertEquals(1, facts.size());
        assertEquals(0.50, facts.get(0).value(), 1e-9);
    }
}

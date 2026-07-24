package com.fattorestreet.sec_api.index;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class SecIndexFactsParserTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseShareFacts_deiEntityCommonStock_preferredFirst() {
        JsonNode root = buildFacts("dei", "EntityCommonStockSharesOutstanding", 1_000_000L);
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(1_000_000L, facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_fallsBackToUsGaapCommonStock() {
        JsonNode root = buildFacts("us-gaap", "CommonStockSharesOutstanding", 2_000_000L);
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(2_000_000L, facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_fallsBackToSharesOutstanding() {
        JsonNode root = buildFacts("us-gaap", "SharesOutstanding", 3_000_000L);
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(3_000_000L, facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_fallsBackToCommonStockSharesIssued() {
        JsonNode root = buildFacts("us-gaap", "CommonStockSharesIssued", 4_000_000L);
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(4_000_000L, facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_fallsBackToWeightedAvgBasicAndDiluted() {
        JsonNode root = buildFacts("us-gaap", "WeightedAverageNumberOfShareOutstandingBasicAndDiluted", 5_000_000L);
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(5_000_000L, facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_fallsBackToWeightedAvgBasic() {
        JsonNode root = buildFacts("us-gaap", "WeightedAverageNumberOfSharesOutstandingBasic", 6_000_000L);
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(6_000_000L, facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_fallsBackToWeightedAvgDiluted() {
        JsonNode root = buildFacts("us-gaap", "WeightedAverageNumberOfDilutedSharesOutstanding", 7_000_000L);
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(7_000_000L, facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_skipsZeroValues() {
        // dei tag reports 0, but us-gaap has a real value
        ObjectNode root = mapper.createObjectNode();
        ObjectNode facts = root.putObject("facts");
        addTagData(facts, "dei", "EntityCommonStockSharesOutstanding", 0L);
        addTagData(facts, "us-gaap", "CommonStockSharesOutstanding", 8_000_000L);

        var result = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(8_000_000L, result.sharesOutstanding());
    }

    @Test
    void parseShareFacts_nullRootReturnsEmpty() {
        var facts = SecIndexFactsParser.parseShareFacts(null);
        assertNull(facts.sharesOutstanding());
        assertNull(facts.publicFloatUsd());
    }

    @Test
    void parseShareFacts_noShareTagsReturnsNull() {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("facts").putObject("dei");
        var facts = SecIndexFactsParser.parseShareFacts(root);
        assertNull(facts.sharesOutstanding());
    }

    @Test
    void parseShareFacts_deiTakesPriorityOverUsGaap() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode facts = root.putObject("facts");
        addTagData(facts, "dei", "EntityCommonStockSharesOutstanding", 100L);
        addTagData(facts, "us-gaap", "CommonStockSharesOutstanding", 200L);

        var result = SecIndexFactsParser.parseShareFacts(root);
        assertEquals(100L, result.sharesOutstanding());
    }

    @Test
    void normalizeCountry_usVariants() {
        assertEquals("United States", SecIndexFactsParser.normalizeCountry("US"));
        assertEquals("United States", SecIndexFactsParser.normalizeCountry("USA"));
        assertEquals("Unknown", SecIndexFactsParser.normalizeCountry(null));
        assertEquals("Unknown", SecIndexFactsParser.normalizeCountry(""));
        assertEquals("CA", SecIndexFactsParser.normalizeCountry("CA"));
    }

    @Test
    void parseSubmissionsLocation_usStateCodes_splitToUnitedStatesAndState() {
        ObjectNode root = mapper.createObjectNode();
        root.put("stateOfIncorporationDescription", "DE");
        ObjectNode addresses = root.putObject("addresses");
        ObjectNode biz = addresses.putObject("business");
        biz.put("stateOrCountryDescription", "CA");

        var loc = SecIndexFactsParser.parseSubmissionsLocation(root);
        assertEquals("United States", loc.countryIncorp());
        assertEquals("DE", loc.stateIncorp());
        assertEquals("United States", loc.countryHq());
        assertEquals("CA", loc.stateHq());
    }

    @Test
    void parseSubmissionsLocation_secOpaqueJurisdictionCodes_unknownCountryAndCodeInState() {
        ObjectNode root = mapper.createObjectNode();
        root.put("stateOfIncorporation", "M0");
        ObjectNode addresses = root.putObject("addresses");
        ObjectNode biz = addresses.putObject("business");
        biz.put("stateOrCountry", "P7");

        var loc = SecIndexFactsParser.parseSubmissionsLocation(root);
        assertEquals("Unknown", loc.countryIncorp());
        assertEquals("M0", loc.stateIncorp());
        assertEquals("Unknown", loc.countryHq());
        assertEquals("P7", loc.stateHq());
    }

    @Test
    void parseSubmissionsLocation_prefersHumanReadableHqDescriptionOverOpaqueCode() {
        ObjectNode root = mapper.createObjectNode();
        root.put("stateOfIncorporationDescription", "DE");
        ObjectNode addresses = root.putObject("addresses");
        ObjectNode biz = addresses.putObject("business");
        biz.put("stateOrCountry", "X0");
        biz.put("stateOrCountryDescription", "United Kingdom");

        var loc = SecIndexFactsParser.parseSubmissionsLocation(root);
        assertEquals("United States", loc.countryIncorp());
        assertEquals("DE", loc.stateIncorp());
        assertEquals("United Kingdom", loc.countryHq());
        assertNull(loc.stateHq());
    }

    @Test
    void parseSubmissionsLocation_blankIncorp_reportsUnknown() {
        // AVGO scenario: SEC leaves stateOfIncorporation blank after redomiciliation
        ObjectNode root = mapper.createObjectNode();
        root.put("stateOfIncorporation", "");
        ObjectNode addresses = root.putObject("addresses");
        ObjectNode biz = addresses.putObject("business");
        biz.put("stateOrCountry", "CA");

        var loc = SecIndexFactsParser.parseSubmissionsLocation(root);
        assertNull(loc.countryIncorp());
        assertNull(loc.stateIncorp());
        assertEquals("United States", loc.countryHq());
        assertEquals("CA", loc.stateHq());
    }

    @Test
    void parseSubmissionsLocation_caymanIslandsIncorp_jurisdictionNameAsCountry() {
        ObjectNode root = mapper.createObjectNode();
        root.put("stateOfIncorporationDescription", "Cayman Islands");
        ObjectNode addresses = root.putObject("addresses");
        addresses.putObject("business");

        var loc = SecIndexFactsParser.parseSubmissionsLocation(root);
        assertEquals("Cayman Islands", loc.countryIncorp());
        assertNull(loc.stateIncorp());
    }

    @Test
    void parseSubmissionsLocation_businessCountryFieldOverridesStateOrCountry() {
        ObjectNode root = mapper.createObjectNode();
        root.put("stateOfIncorporationDescription", "DE");
        ObjectNode addresses = root.putObject("addresses");
        ObjectNode biz = addresses.putObject("business");
        biz.put("country", "Germany");
        biz.put("stateOrCountryDescription", "HE");

        var loc = SecIndexFactsParser.parseSubmissionsLocation(root);
        assertEquals("United States", loc.countryIncorp());
        assertEquals("DE", loc.stateIncorp());
        assertEquals("Germany", loc.countryHq());
        assertNull(loc.stateHq());
    }

    @Test
    void parseSubmissionsLocation_nullRootReturnsEmpty() {
        var loc = SecIndexFactsParser.parseSubmissionsLocation(null);
        assertNull(loc.countryIncorp());
        assertNull(loc.countryHq());
        assertNull(loc.stateIncorp());
        assertNull(loc.stateHq());
    }

    // --- helpers ---

    private JsonNode buildFacts(String taxonomy, String tag, long value) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode facts = root.putObject("facts");
        addTagData(facts, taxonomy, tag, value);
        return root;
    }

    private void addTagData(ObjectNode factsNode, String taxonomy, String tag, long value) {
        ObjectNode taxonomyNode = factsNode.has(taxonomy)
                ? (ObjectNode) factsNode.get(taxonomy)
                : factsNode.putObject(taxonomy);
        ObjectNode tagNode = taxonomyNode.putObject(tag);
        ObjectNode units = tagNode.putObject("units");
        var arr = units.putArray("shares");
        ObjectNode entry = arr.addObject();
        entry.put("val", value);
        entry.put("end", "2025-12-31");
    }
}

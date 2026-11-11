package com.fattorestreet.sec_api.index;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.fattorestreet.sec_api.index.CoverPageFixtures.Fact;

import static com.fattorestreet.sec_api.index.CoverPageFixtures.CLASS_A;
import static com.fattorestreet.sec_api.index.CoverPageFixtures.CLASS_B;
import static com.fattorestreet.sec_api.index.CoverPageFixtures.instance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverPageSharesTest {

    @Test
    void matchesEachListedClassBySymbol_berkshireShape() throws Exception {
        CoverPageShares cp = CoverPageShares.parse(instance(
                Fact.symbol(CLASS_A, "BRK.A"),
                Fact.symbol(CLASS_B, "BRK.B"),
                Fact.symbol("brka:SeniorNotesDue2027Member", "BRK27"),
                Fact.shares(CLASS_A, 480_476L),
                Fact.shares(CLASS_B, 1_403_805_594L)));

        assertTrue(cp.hasClassShares());
        assertEquals(480_476L, cp.sharesFor("BRK-A"));
        assertEquals(1_403_805_594L, cp.sharesFor("BRK-B"));
        assertNull(cp.sharesFor("BRK27"), "listed notes have no share count");
        assertEquals(List.of(
                new CoverPageShares.ListedClass("BRKA", 480_476L),
                new CoverPageShares.ListedClass("BRKB", 1_403_805_594L)), cp.listedClasses());
    }

    @Test
    void matchesSymbolsWrittenWithoutSeparator_brownFormanShape() throws Exception {
        CoverPageShares cp = CoverPageShares.parse(instance(
                Fact.symbol(CLASS_A, "BFA"),
                Fact.symbol("us-gaap:NonvotingCommonStockMember", "BFB"),
                Fact.shares(CLASS_A, 168_480_849L),
                Fact.shares("us-gaap:NonvotingCommonStockMember", 290_383_416L)));

        assertEquals(168_480_849L, cp.sharesFor("BF-A"));
        assertEquals(290_383_416L, cp.sharesFor("BF-B"));
    }

    @Test
    void unlistedClassHasNoSymbolAndIsNotListed_alphabetShape() throws Exception {
        CoverPageShares cp = CoverPageShares.parse(instance(
                Fact.symbol(CLASS_A, "GOOGL"),
                Fact.symbol("goog:CapitalClassCMember", "GOOG"),
                Fact.shares(CLASS_A, 5_888_000_000L),
                Fact.shares(CLASS_B, 827_000_000L),
                Fact.shares("goog:CapitalClassCMember", 5_579_000_000L)));

        assertEquals(5_888_000_000L, cp.sharesFor("GOOGL"));
        assertEquals(5_579_000_000L, cp.sharesFor("GOOG"));
        assertEquals(2, cp.listedClasses().size(), "Class B is unlisted");
    }

    @Test
    void undimensionedSymbolMapsToClassA_metaShape() throws Exception {
        CoverPageShares cp = CoverPageShares.parse(instance(
                Fact.symbol(null, "META"),
                Fact.shares(CLASS_A, 2_214_472_367L),
                Fact.shares(CLASS_B, 341_768_886L)));

        assertEquals(2_214_472_367L, cp.sharesFor("META"));
        assertEquals(List.of(new CoverPageShares.ListedClass("META", 2_214_472_367L)), cp.listedClasses());
    }

    @Test
    void undimensionedSymbolWithoutClassAMapsToLargestClass() throws Exception {
        CoverPageShares cp = CoverPageShares.parse(instance(
                Fact.symbol(null, "XYZ"),
                Fact.shares("xyz:SeriesOneMember", 10L),
                Fact.shares("xyz:SeriesTwoMember", 900L)));

        assertEquals(900L, cp.sharesFor("XYZ"));
    }

    @Test
    void singleClassFilerReportsPlainShares() throws Exception {
        CoverPageShares cp = CoverPageShares.parse(instance(
                Fact.symbol(null, "AAPL"),
                Fact.shares(null, 14_532_182_000L)));

        assertFalse(cp.hasClassShares());
        assertEquals(14_532_182_000L, cp.plainShares());
        assertEquals(14_532_182_000L, cp.sharesFor("AAPL"));
    }

    @Test
    void ignoresContextsWithOtherDimensions_simonShape() throws Exception {
        String xml = instance(
                Fact.symbol(CLASS_A, "SPG"),
                Fact.shares(CLASS_A, 322_462_211L)).replace("</xbrli:xbrl>", """
                <xbrli:context id="lp"><xbrli:entity>
                <xbrli:identifier scheme="http://www.sec.gov/CIK">1</xbrli:identifier>
                <xbrli:segment><xbrldi:explicitMember dimension="dei:LegalEntityAxis">spg:PartnershipMember</xbrldi:explicitMember></xbrli:segment>
                </xbrli:entity><xbrli:period><xbrli:instant>2026-09-30</xbrli:instant></xbrli:period></xbrli:context>
                <dei:EntityCommonStockSharesOutstanding contextRef="lp">0</dei:EntityCommonStockSharesOutstanding>
                </xbrli:xbrl>""");

        CoverPageShares cp = CoverPageShares.parse(xml);

        assertEquals(322_462_211L, cp.sharesFor("SPG"));
        assertNull(cp.plainShares(), "the partnership's figure is not the company's");
    }

    @Test
    void parsesTextBlocksPastTheJdkDefaultEntityLimits() throws Exception {
        // Real instances embed whole notes as escaped HTML; Berkshire's broke the JDK 24+ defaults.
        String block = "&lt;p&gt;note&lt;/p&gt;".repeat(20_000);
        String xml = instance(Fact.symbol(null, "BIG"), Fact.shares(null, 5L)).replace("</xbrli:xbrl>",
                "<us-gaap:SomeTextBlock contextRef=\"c0\">" + block + "</us-gaap:SomeTextBlock></xbrli:xbrl>");

        assertEquals(5L, CoverPageShares.parse(xml).sharesFor("BIG"));
    }

    @Test
    void rejectsDoctypeDeclarations() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
                <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance">&e;</xbrli:xbrl>
                """;

        assertThrows(XMLStreamException.class, () -> CoverPageShares.parse(xxe));
    }

    @Test
    void malformedShareCountLosesThatClassOnly() throws Exception {
        CoverPageShares cp = CoverPageShares.parse(instance(
                Fact.symbol(CLASS_A, "XA"),
                Fact.symbol(CLASS_B, "XB"),
                new Fact("EntityCommonStockSharesOutstanding", "n/a", CLASS_A, "2026-10-28"),
                Fact.shares(CLASS_B, 42L)));

        assertNull(cp.sharesFor("XA"));
        assertEquals(42L, cp.sharesFor("XB"));
    }

    @Test
    void blankInputIsEmpty() throws Exception {
        assertSame(CoverPageShares.EMPTY, CoverPageShares.parse(""));
        assertSame(CoverPageShares.EMPTY, CoverPageShares.parse(null));
    }
}

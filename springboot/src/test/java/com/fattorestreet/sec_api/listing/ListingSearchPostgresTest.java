package com.fattorestreet.sec_api.listing;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.fattorestreet.sec_api.listing.ListingSearchService.SearchResult;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the company search against real Postgres, because its SQL is Postgres-only (pg_trgm's
 * {@code <%} operator and {@code set_config}) and the H2 database the other tests use can't run it.
 * The schema comes from the Flyway migrations, so the V6 extension and trigram indexes are covered
 * too. Skipped when no Docker daemon is available; CI runners have one.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@Import(ListingSearchService.class)
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ListingSearchPostgresTest {

    // Same exact image as production (deploy/docker-compose.infra.yml)
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-trixie");

    @Autowired
    private ListingSearchService listingSearchService;
    @Autowired
    private TestEntityManager em;

    private long nextCik = 1;

    @BeforeEach
    void seed() {
        listing("AAPL", "Apple Inc.", null, false);
        listing("AAPI", "Apple iSports Group, Inc.", null, false);
        listing("APLE", "Apple Hospitality REIT, Inc.", null, false);
        listing("PNPL", "PINEAPPLE, INC.", null, false);
        listing("MSFT", "MICROSOFT CORP", null, false);
        listing("SMSI", "SMITH MICRO SOFTWARE, INC.", null, false);
        listing("BRK-A", "BERKSHIRE HATHAWAY INC", null, false);
        listing("BRK-B", "BERKSHIRE HATHAWAY INC", null, false);
        listing("BRKU", "BRKU", null, true);
        listing("VTI", "VTI", null, true);
        listing("VTSAX", "VANGUARD INDEX FUNDS", "Vanguard Total Stock Market Index Fund", true);
        em.flush();
        em.clear();
    }

    private void listing(String ticker, String title, String seriesName, boolean isFund) {
        Asset asset = new Asset();
        asset.setCik(nextCik++);
        asset.setIsFund(isFund);
        em.persist(asset);
        Listing listing = new Listing();
        listing.setTicker(ticker);
        listing.setTitle(title);
        listing.setSecSeriesName(seriesName);
        listing.setAsset(asset);
        em.persist(listing);
    }

    private List<String> tickers(String query, boolean includeFunds) {
        return listingSearchService.search(query, 10, includeFunds).stream().map(SearchResult::ticker).toList();
    }

    @Test
    void namePrefixRanksTheShortestMatchingNameFirst() {
        List<String> tickers = tickers("apple", true);

        assertEquals(List.of("AAPL", "AAPI", "APLE"), tickers.subList(0, 3));
    }

    @Test
    void oneLetterTypoStillFindsTheCompany() {
        assertEquals("MSFT", tickers("micrsoft", true).getFirst());
    }

    @Test
    void exactTickerRanksFirstAndExchangeShareClassFormMatches() {
        assertEquals("AAPL", tickers("aapl", true).getFirst());
        assertEquals("BRK-B", tickers("brk.b", true).getFirst());
    }

    @Test
    void fundsAreFoundBySeriesNameAndShowIt() {
        List<SearchResult> results = listingSearchService.search("vanguard total", 10, true);

        assertEquals(new SearchResult("VTSAX", "Vanguard Total Stock Market Index Fund", true), results.getFirst());
    }

    @Test
    void fundWithoutANameIsFoundByTickerWithNoName() {
        assertEquals(new SearchResult("VTI", null, true), listingSearchService.search("vti", 10, true).getFirst());
    }

    @Test
    void excludingFundsLeavesOnlyCompanies() {
        List<String> withFunds = tickers("brk", true);
        List<String> companiesOnly = tickers("brk", false);

        assertTrue(withFunds.contains("BRKU"));
        assertEquals(List.of("BRK-A", "BRK-B"), companiesOnly);
    }

    @Test
    void likeWildcardsInTheQueryMatchLiterally() {
        // Unescaped, "_" would match any one character and "%" any run, so "a_pl" would find AAPL
        assertFalse(tickers("a_pl", true).contains("AAPL"));
        assertTrue(tickers("%", true).isEmpty());
    }
}

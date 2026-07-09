package com.fattorestreet.sec_api.repository;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ListingIndexMetricsRepositoryTest {

    @Autowired
    private ListingIndexMetricsRepository repository;
    @Autowired
    private TestEntityManager em;

    private Listing aapl;

    @BeforeEach
    void seed() {
        aapl = listing("AAPL", "Apple Inc.", 320193L);
        Listing msft = listing("MSFT", "Microsoft", 789019L);
        metrics(aapl, 2026, "3000");
        metrics(aapl, 2025, "2800");
        metrics(msft, 2026, "2900");
        em.flush();
        em.clear();
    }

    private Listing listing(String ticker, String title, long cik) {
        Asset a = new Asset();
        a.setCik(cik);
        em.persist(a);
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setTitle(title);
        l.setAsset(a);
        return em.persist(l);
    }

    private void metrics(Listing l, int year, String marketCap) {
        ListingIndexMetrics m = new ListingIndexMetrics();
        m.setListing(l);
        m.setYear(year);
        m.setMarketCap(new BigDecimal(marketCap));
        em.persist(m);
    }

    @Test
    void findAllWithListingAndAssetByYear_filtersYearAndFetchJoins() {
        List<ListingIndexMetrics> rows = repository.findAllWithListingAndAssetByYear(2026);

        assertEquals(2, rows.size());
        for (ListingIndexMetrics m : rows) {
            assertEquals(2026, m.getYear());
            assertTrue(Hibernate.isInitialized(m.getListing()), "listing must be fetch-joined");
            assertTrue(Hibernate.isInitialized(m.getListing().getAsset()), "asset must be fetch-joined");
        }
    }

    @Test
    void findAllByYear_returnsOnlyThatYear() {
        assertEquals(1, repository.findAllByYear(2025).size());
        assertTrue(repository.findAllByYear(2020).isEmpty());
    }

    @Test
    void findByListingAndYear() {
        Listing managedAapl = em.find(Listing.class, aapl.getId());

        assertTrue(repository.findByListingAndYear(managedAapl, 2026).isPresent());
        assertTrue(repository.findByListingAndYear(managedAapl, 2019).isEmpty());
    }
}

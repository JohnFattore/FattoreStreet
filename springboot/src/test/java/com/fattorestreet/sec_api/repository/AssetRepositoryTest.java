package com.fattorestreet.sec_api.repository;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class AssetRepositoryTest {

    @Autowired
    private AssetRepository repository;
    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void seed() {
        asset(320193L, false, "AAPL");
        asset(789019L, false, "MSFT");
        asset(102909L, true, "VOO");
        em.flush();
        em.clear();
    }

    private void asset(long cik, boolean isFund, String ticker) {
        Asset a = new Asset();
        a.setCik(cik);
        a.setIsFund(isFund);
        em.persist(a);
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setTitle(ticker + " title");
        l.setAsset(a);
        em.persist(l);
    }

    @Test
    void findByTickersIn_excludesFunds() {
        List<Asset> assets = repository.findByTickersIn(List.of("AAPL", "MSFT", "VOO"));

        assertEquals(2, assets.size());
        assertTrue(assets.stream().noneMatch(Asset::getIsFund));
    }

    @Test
    void findByListingsTicker() {
        Asset asset = repository.findByListings_Ticker("AAPL");

        assertNotNull(asset);
        assertEquals(320193L, asset.getCik());
        assertNull(repository.findByListings_Ticker("ZZZZ"));
    }

    @Test
    void findAllWithListings_fetchJoinsListings() {
        List<Asset> assets = repository.findAllWithListings();

        assertEquals(3, assets.size());
        for (Asset a : assets) {
            assertTrue(Hibernate.isInitialized(a.getListings()), "listings must be fetch-joined");
        }
    }

    @Test
    void findByCikAndCounts() {
        assertTrue(repository.findByCik(320193L).isPresent());
        assertTrue(repository.findByCik(1L).isEmpty());
        assertEquals(1, repository.countByIsFund(true));
        assertEquals(2, repository.countByIsFund(false));
        assertEquals(1, repository.findByIsFund(true).size());
    }

    @Test
    void cikIsUnique() {
        Asset dup = new Asset();
        dup.setCik(320193L);

        // Identity id generation inserts on persist, so the violation surfaces there already.
        assertThrows(RuntimeException.class, () -> {
            em.persist(dup);
            em.flush();
        });
    }
}

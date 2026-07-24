package com.fattorestreet.sec_api.repository;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.IndexMember;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.MarketIndex;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class IndexMemberRepositoryTest {

    @Autowired
    private IndexMemberRepository repository;
    @Autowired
    private TestEntityManager em;

    private MarketIndex fat50;
    private MarketIndex fat100;

    @BeforeEach
    void seed() {
        fat50 = index("FAT50", "Fattore 50");
        fat100 = index("FAT100", "Fattore 100");
        member(fat50, listing("AAPL", "Apple Inc.", 320193L), "8.5");
        member(fat50, listing("MSFT", "Microsoft", 789019L), "7.25");
        member(fat100, listing("ORPH", "No Asset Co", null), "1.0");
        em.flush();
        em.clear();
    }

    private MarketIndex index(String code, String name) {
        MarketIndex mi = new MarketIndex();
        mi.setCode(code);
        mi.setDisplayName(name);
        return em.persist(mi);
    }

    private Listing listing(String ticker, String title, Long cik) {
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setTitle(title);
        if (cik != null) {
            Asset a = new Asset();
            a.setCik(cik);
            l.setAsset(em.persist(a));
        }
        return em.persist(l);
    }

    private void member(MarketIndex mi, Listing l, String percent) {
        IndexMember im = new IndexMember();
        im.setMarketIndex(mi);
        im.setListing(l);
        im.setPercent(new BigDecimal(percent));
        im.setOutlier(false);
        em.persist(im);
    }

    @Test
    void findAllWithListingAndAsset_fetchJoinsAssociations() {
        List<IndexMember> members = repository.findAllWithListingAndAsset();

        assertEquals(3, members.size());
        for (IndexMember im : members) {
            assertTrue(Hibernate.isInitialized(im.getListing()), "listing must be fetch-joined");
            assertTrue(Hibernate.isInitialized(im.getMarketIndex()), "marketIndex must be fetch-joined");
        }
        // LEFT JOIN FETCH keeps members whose listing has no asset.
        assertTrue(members.stream().anyMatch(im -> im.getListing().getTicker().equals("ORPH")));
    }

    @Test
    void findByMarketIndexCode_ordersByPercentDesc() {
        List<IndexMember> members = repository.findByMarketIndex_CodeOrderByPercentDesc("FAT50");

        assertEquals(2, members.size());
        assertEquals(0, members.get(0).getPercent().compareTo(new BigDecimal("8.5")));
        assertEquals(0, members.get(1).getPercent().compareTo(new BigDecimal("7.25")));
    }

    @Test
    void findByMarketIndexCodeAndListingTicker() {
        assertTrue(repository.findByMarketIndex_CodeAndListing_Ticker("FAT50", "AAPL").isPresent());
        assertTrue(repository.findByMarketIndex_CodeAndListing_Ticker("FAT100", "AAPL").isEmpty());
    }

    @Test
    void deleteByMarketIndexCode_removesOnlyThatIndex() {
        repository.deleteByMarketIndex_Code("FAT50");
        em.flush();
        em.clear();

        assertTrue(repository.findByMarketIndex_CodeOrderByPercentDesc("FAT50").isEmpty());
        assertEquals(1, repository.findByMarketIndex_CodeOrderByPercentDesc("FAT100").size());
    }
}

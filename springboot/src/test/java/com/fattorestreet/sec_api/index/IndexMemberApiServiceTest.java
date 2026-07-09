package com.fattorestreet.sec_api.index;

import com.fattorestreet.sec_api.index.IndexMemberApiService.IndexMemberRow;
import com.fattorestreet.sec_api.model.IndexMember;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.repository.IndexMemberRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexMemberApiServiceTest {

    private static final int CURRENT_YEAR = Year.now().getValue();

    @Mock
    private IndexMemberRepository indexMemberRepository;
    @Mock
    private ListingIndexMetricsRepository metricsRepository;

    @InjectMocks
    private IndexMemberApiService service;

    private static Listing listing(long id, String ticker, String title) {
        Listing l = new Listing();
        l.setId(id);
        l.setTicker(ticker);
        l.setTitle(title);
        return l;
    }

    private static IndexMember member(long id, Listing listing, String indexName, BigDecimal percent) {
        MarketIndex index = new MarketIndex();
        index.setDisplayName(indexName);
        IndexMember im = new IndexMember();
        im.setId(id);
        im.setListing(listing);
        im.setMarketIndex(index);
        im.setPercent(percent);
        im.setOutlier(false);
        return im;
    }

    private static ListingIndexMetrics metrics(Listing listing) {
        ListingIndexMetrics m = new ListingIndexMetrics();
        m.setListing(listing);
        m.setYear(CURRENT_YEAR);
        m.setMarketCap(new BigDecimal("3000"));
        m.setVolume(new BigDecimal("55"));
        m.setVolumeUsd(new BigDecimal("9000"));
        m.setFreeFloat(new BigDecimal("0.98"));
        m.setFreeFloatMarketCap(new BigDecimal("2940"));
        m.setCountryIncorp("US");
        m.setCountryHq("US");
        m.setStateIncorp("DE");
        m.setStateHq("CA");
        m.setSecurityType("Common Stock");
        m.setYearIpo(1980);
        return m;
    }

    @Test
    void listAll_joinsCurrentYearMetricsByListing() {
        Listing aapl = listing(1L, "AAPL", "Apple Inc.");
        when(indexMemberRepository.findAllWithListingAndAsset()).thenReturn(List.of(
                member(10L, aapl, "Fattore 50", new BigDecimal("6.5"))));
        when(metricsRepository.findAllByYear(CURRENT_YEAR)).thenReturn(List.of(metrics(aapl)));

        List<IndexMemberRow> rows = service.listAll();

        assertEquals(1, rows.size());
        IndexMemberRow row = rows.get(0);
        assertEquals(10L, row.id());
        assertEquals("Fattore 50", row.index());
        assertEquals(new BigDecimal("6.5"), row.percent());
        assertEquals("AAPL", row.stock().ticker());
        assertEquals("Apple Inc.", row.stock().name());
        assertEquals(new BigDecimal("3000"), row.stock().marketCap());
        assertEquals("DE", row.stock().stateIncorp());
        assertEquals(1980, row.stock().yearIPO());
    }

    @Test
    void listAll_memberWithoutMetricsGetsZeroDefaults() {
        Listing nvda = listing(2L, "NVDA", "NVIDIA");
        when(indexMemberRepository.findAllWithListingAndAsset()).thenReturn(List.of(
                member(11L, nvda, "Fattore 100", new BigDecimal("4.0"))));
        when(metricsRepository.findAllByYear(CURRENT_YEAR)).thenReturn(List.of());

        List<IndexMemberRow> rows = service.listAll();

        assertEquals(BigDecimal.ZERO, rows.get(0).stock().marketCap());
        assertEquals("United States", rows.get(0).stock().countryIncorp());
        assertEquals("Common Stock", rows.get(0).stock().securityType());
        assertEquals(0, rows.get(0).stock().yearIPO());
        assertNull(rows.get(0).stock().stateIncorp());
    }

    @Test
    void listAll_nullMetricFieldsFallBackToDefaults() {
        Listing tsla = listing(3L, "TSLA", "Tesla");
        ListingIndexMetrics sparse = new ListingIndexMetrics();
        sparse.setListing(tsla);
        sparse.setYear(CURRENT_YEAR);
        when(indexMemberRepository.findAllWithListingAndAsset()).thenReturn(List.of(
                member(12L, tsla, "Fattore 50", BigDecimal.ONE)));
        when(metricsRepository.findAllByYear(CURRENT_YEAR)).thenReturn(List.of(sparse));

        List<IndexMemberRow> rows = service.listAll();

        assertEquals(BigDecimal.ZERO, rows.get(0).stock().marketCap());
        assertEquals("United States", rows.get(0).stock().countryHQ());
        assertEquals(0, rows.get(0).stock().yearIPO());
    }

    @Test
    void listByIndexCode_filtersMembersWithoutListing() {
        Listing msft = listing(4L, "MSFT", "Microsoft");
        IndexMember orphan = new IndexMember();
        orphan.setId(13L);
        orphan.setListing(null);
        when(indexMemberRepository.findByMarketIndex_CodeOrderByPercentDesc("FAT50"))
                .thenReturn(List.of(member(14L, msft, "Fattore 50", BigDecimal.TEN), orphan));
        when(metricsRepository.findAllByYear(CURRENT_YEAR)).thenReturn(List.of());

        List<IndexMemberRow> rows = service.listByIndexCode("FAT50");

        assertEquals(1, rows.size());
        assertEquals("MSFT", rows.get(0).stock().ticker());
    }

    @Test
    void listByIndexCode_unknownCodeReturnsEmpty() {
        when(indexMemberRepository.findByMarketIndex_CodeOrderByPercentDesc("NOPE")).thenReturn(List.of());
        when(metricsRepository.findAllByYear(CURRENT_YEAR)).thenReturn(List.of());

        assertTrue(service.listByIndexCode("NOPE").isEmpty());
    }

    @Test
    void nullNotesAndMissingIndexNameBecomeEmptyStrings() {
        Listing amzn = listing(5L, "AMZN", "Amazon");
        IndexMember im = new IndexMember();
        im.setId(15L);
        im.setListing(amzn);
        im.setMarketIndex(null);
        im.setNotes(null);
        im.setPercent(BigDecimal.ONE);
        when(indexMemberRepository.findAllWithListingAndAsset()).thenReturn(List.of(im));
        when(metricsRepository.findAllByYear(CURRENT_YEAR)).thenReturn(List.of());

        List<IndexMemberRow> rows = service.listAll();

        assertEquals("", rows.get(0).index());
        assertEquals("", rows.get(0).notes());
    }
}

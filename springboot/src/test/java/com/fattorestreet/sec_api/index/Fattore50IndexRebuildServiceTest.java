package com.fattorestreet.sec_api.index;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.IndexMember;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.repository.IndexMemberRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Fattore50IndexRebuildServiceTest {

    private static final int TEST_YEAR = 2026;

    @Mock
    private ListingIndexMetricsRepository metricsRepository;
    @Mock
    private MarketIndexRepository marketIndexRepository;
    @Mock
    private IndexMemberRepository indexMemberRepository;

    private Fattore50IndexRebuildService service;

    @BeforeEach
    void setUp() {
        service = new Fattore50IndexRebuildService(metricsRepository, marketIndexRepository, indexMemberRepository, 3);
    }

    @Test
    void rebuild_excludesFundsAndZeroFloat_takesTopByFreeFloatMarketCap() {
        List<ListingIndexMetrics> rows = new ArrayList<>();
        rows.add(metrics(listing("FUND", equityAsset(true)), bd("900")));
        rows.add(metrics(listing("ZERO", equityAsset(false)), BigDecimal.ZERO));
        rows.add(metrics(listing("NO_ASSET", null), bd("50")));
        rows.add(metrics(listing("AAA", equityAsset(false)), bd("100")));
        rows.add(metrics(listing("BBB", equityAsset(false)), bd("300")));
        rows.add(metrics(listing("CCC", equityAsset(false)), bd("200")));

        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(rows);
        MarketIndex mi = new MarketIndex();
        mi.setId(9L);
        mi.setCode(Fattore50IndexRebuildService.INDEX_CODE);
        when(marketIndexRepository.findByCode(Fattore50IndexRebuildService.INDEX_CODE)).thenReturn(Optional.of(mi));

        Fattore50IndexRebuildService.RebuildResult result = service.rebuild(TEST_YEAR);

        assertEquals(TEST_YEAR, result.year());
        assertEquals(3, result.memberCount());
        assertFalse(result.partial());
        assertEquals(List.of("BBB", "CCC", "AAA"), result.tickers());

        verify(indexMemberRepository).deleteByMarketIndex_Code(Fattore50IndexRebuildService.INDEX_CODE);
        ArgumentCaptor<IndexMember> cap = ArgumentCaptor.forClass(IndexMember.class);
        verify(indexMemberRepository, org.mockito.Mockito.times(3)).save(cap.capture());
        List<IndexMember> saved = cap.getAllValues();
        assertEquals("BBB", saved.get(0).getListing().getTicker());
        assertEquals("CCC", saved.get(1).getListing().getTicker());
        assertEquals("AAA", saved.get(2).getListing().getTicker());
        BigDecimal sum = saved.stream().map(IndexMember::getPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100.00000").compareTo(sum));
    }

    @Test
    void rebuild_emptyEligible_createsIndexClearsMembers() {
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of());
        MarketIndex created = new MarketIndex();
        created.setCode(Fattore50IndexRebuildService.INDEX_CODE);
        when(marketIndexRepository.findByCode(Fattore50IndexRebuildService.INDEX_CODE)).thenReturn(Optional.empty());
        when(marketIndexRepository.save(any(MarketIndex.class))).thenAnswer(inv -> inv.getArgument(0));

        Fattore50IndexRebuildService.RebuildResult result = service.rebuild(TEST_YEAR);

        assertEquals(0, result.memberCount());
        assertTrue(result.partial());
        verify(marketIndexRepository).save(any(MarketIndex.class));
        verify(indexMemberRepository).deleteByMarketIndex_Code(Fattore50IndexRebuildService.INDEX_CODE);
        verify(indexMemberRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void rebuild_partialWhenFewerEligibleThanTopN() {
        service = new Fattore50IndexRebuildService(metricsRepository, marketIndexRepository, indexMemberRepository, 5);
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(
                metrics(listing("A", equityAsset(false)), bd("10")),
                metrics(listing("B", equityAsset(false)), bd("20"))
        ));
        MarketIndex mi = new MarketIndex();
        when(marketIndexRepository.findByCode(Fattore50IndexRebuildService.INDEX_CODE)).thenReturn(Optional.of(mi));

        Fattore50IndexRebuildService.RebuildResult result = service.rebuild(TEST_YEAR);

        assertTrue(result.partial());
        assertEquals(2, result.memberCount());
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static Asset equityAsset(boolean fund) {
        Asset a = new Asset();
        a.setId(1L);
        a.setCik(1L);
        a.setIsFund(fund);
        return a;
    }

    private static Listing listing(String ticker, Asset asset) {
        Listing l = new Listing();
        l.setId((long) ticker.hashCode());
        l.setTicker(ticker);
        l.setTitle(ticker + " Co");
        l.setAsset(asset);
        return l;
    }

    private static ListingIndexMetrics metrics(Listing listing, BigDecimal ffMc) {
        ListingIndexMetrics m = new ListingIndexMetrics();
        m.setListing(listing);
        m.setYear(TEST_YEAR);
        m.setFreeFloatMarketCap(ffMc);
        return m;
    }
}

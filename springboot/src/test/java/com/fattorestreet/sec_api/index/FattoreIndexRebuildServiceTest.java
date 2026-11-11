package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.IndexMember;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.repository.IndexMemberRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FattoreIndexRebuildServiceTest {

    private static final int TEST_YEAR = 2026;
    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 11, 10);

    @Mock
    private ListingIndexMetricsRepository metricsRepository;
    @Mock
    private MarketIndexRepository marketIndexRepository;
    @Mock
    private IndexMemberRepository indexMemberRepository;

    private FattoreIndexRebuildService serviceFat50;

    @BeforeEach
    void setUp() {
        serviceFat50 = new FattoreIndexRebuildService(
                metricsRepository, marketIndexRepository, indexMemberRepository,
                FattoreIndexCodes.FAT50, "Fattore 50", 3);
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
        mi.setCode(FattoreIndexCodes.FAT50);
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.of(mi));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(FattoreIndexCodes.FAT50, result.indexCode());
        assertEquals(TEST_YEAR, result.year());
        assertEquals(3, result.companyCount());
        assertEquals(3, result.listingCount());
        assertFalse(result.partial());
        assertEquals(List.of("BBB", "CCC", "AAA"), result.tickers());

        verify(indexMemberRepository).deleteByMarketIndex_Code(FattoreIndexCodes.FAT50);
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
    void rebuild_fat100_deletesByFat100Code() {
        FattoreIndexRebuildService service100 = new FattoreIndexRebuildService(
                metricsRepository, marketIndexRepository, indexMemberRepository,
                FattoreIndexCodes.FAT100, "Fattore 100", 1);
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(
                metrics(listing("Z", equityAsset(false)), bd("1"))
        ));
        MarketIndex mi = new MarketIndex();
        mi.setCode(FattoreIndexCodes.FAT100);
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT100)).thenReturn(Optional.of(mi));

        FattoreIndexRebuildService.RebuildResult result = service100.rebuild(TEST_YEAR);

        assertEquals(FattoreIndexCodes.FAT100, result.indexCode());
        verify(indexMemberRepository).deleteByMarketIndex_Code(FattoreIndexCodes.FAT100);
    }

    @Test
    void rebuild_emptyEligible_createsIndexClearsMembers() {
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of());
        MarketIndex created = new MarketIndex();
        created.setCode(FattoreIndexCodes.FAT50);
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.empty());
        when(marketIndexRepository.save(any(MarketIndex.class))).thenAnswer(inv -> inv.getArgument(0));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(0, result.companyCount());
        assertTrue(result.partial());
        verify(marketIndexRepository).save(any(MarketIndex.class));
        verify(indexMemberRepository).deleteByMarketIndex_Code(FattoreIndexCodes.FAT50);
        verify(indexMemberRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void rebuild_unknownIncorpWithUsHq_stillEligible() {
        ListingIndexMetrics avgo = metrics(listing("AVGO", equityAsset(false)), bd("500"));
        avgo.setCountryIncorp("Unknown");
        avgo.setCountryHq("United States");

        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(
                avgo,
                metrics(listing("AAPL", equityAsset(false)), bd("300"))
        ));
        MarketIndex mi = new MarketIndex();
        mi.setCode(FattoreIndexCodes.FAT50);
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.of(mi));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertTrue(result.tickers().contains("AVGO"));
        assertEquals(2, result.companyCount());
    }

    @Test
    void rebuild_unknownIncorpWithForeignHq_excluded() {
        ListingIndexMetrics foreign = metrics(listing("FOREIGN", equityAsset(false)), bd("500"));
        foreign.setCountryIncorp("Unknown");
        foreign.setCountryHq("Singapore");

        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(foreign));
        MarketIndex mi = new MarketIndex();
        mi.setCode(FattoreIndexCodes.FAT50);
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.of(mi));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(0, result.companyCount());
        assertTrue(result.partial());
    }

    @Test
    void rebuild_partialWhenFewerEligibleThanTopN() {
        FattoreIndexRebuildService svc = new FattoreIndexRebuildService(
                metricsRepository, marketIndexRepository, indexMemberRepository,
                FattoreIndexCodes.FAT50, "Fattore 50", 5);
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(
                metrics(listing("A", equityAsset(false)), bd("10")),
                metrics(listing("B", equityAsset(false)), bd("20"))
        ));
        MarketIndex mi = new MarketIndex();
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.of(mi));

        FattoreIndexRebuildService.RebuildResult result = svc.rebuild(TEST_YEAR);

        assertTrue(result.partial());
        assertEquals(2, result.companyCount());
    }

    @Test
    void rebuild_skipsRowsPricedMoreThanAWeekBeforeTheNewest() {
        LocalDate newest = LocalDate.of(2026, 11, 10);
        ListingIndexMetrics fresh = metrics(listing("BNY", equityAsset(false)), bd("100"));
        fresh.setPriceDate(newest);
        ListingIndexMetrics edge = metrics(listing("EDGE", equityAsset(false)), bd("50"));
        edge.setPriceDate(newest.minusDays(FattoreIndexRebuildService.MAX_PRICE_AGE_DAYS));
        ListingIndexMetrics renamed = metrics(listing("BK", equityAsset(false)), bd("90"));
        renamed.setPriceDate(newest.minusDays(FattoreIndexRebuildService.MAX_PRICE_AGE_DAYS + 1));
        ListingIndexMetrics undated = metrics(listing("OLD", equityAsset(false)), bd("80"));
        undated.setPriceDate(null);

        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR))
                .thenReturn(List.of(fresh, edge, renamed, undated));
        MarketIndex mi = new MarketIndex();
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.of(mi));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(List.of("BNY", "EDGE"), result.tickers());
        assertTrue(result.partial());
    }

    @Test
    void withFreshPrices_treatsUndatedRowsAsStale() {
        ListingIndexMetrics a = metrics(listing("A", equityAsset(false)), bd("1"));
        a.setPriceDate(null);
        ListingIndexMetrics b = metrics(listing("B", equityAsset(false)), bd("2"));
        b.setPriceDate(null);

        assertEquals(List.of(), FattoreIndexRebuildService.withFreshPrices(List.of(a, b)));
    }

    @Test
    void rebuild_throwsAndKeepsMembersWhenMostCurrentMembersWentStale() {
        // A single-ticker refresh dated AAA today; BBB and CCC still carry last month's date.
        ListingIndexMetrics aaa = metrics(listing("AAA", equityAsset(false)), bd("100"));
        ListingIndexMetrics bbb = metrics(listing("BBB", equityAsset(false)), bd("90"));
        bbb.setPriceDate(PRICE_DATE.minusDays(30));
        ListingIndexMetrics ccc = metrics(listing("CCC", equityAsset(false)), bd("80"));
        ccc.setPriceDate(PRICE_DATE.minusDays(30));
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(aaa, bbb, ccc));
        when(indexMemberRepository.findTickersByIndexCode(FattoreIndexCodes.FAT50))
                .thenReturn(List.of("AAA", "BBB", "CCC"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> serviceFat50.rebuild(TEST_YEAR));

        assertTrue(e.getMessage().contains("2 of 3"));
        verify(indexMemberRepository, never()).deleteByMarketIndex_Code(any());
        verify(indexMemberRepository, never()).save(any());
    }

    @Test
    void rebuild_proceedsWhenOnlyAFewCurrentMembersDropOut() {
        ListingIndexMetrics aaa = metrics(listing("AAA", equityAsset(false)), bd("100"));
        ListingIndexMetrics bbb = metrics(listing("BBB", equityAsset(false)), bd("90"));
        ListingIndexMetrics gone = metrics(listing("GONE", equityAsset(false)), bd("80"));
        gone.setPriceDate(PRICE_DATE.minusDays(30));
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(aaa, bbb, gone));
        when(indexMemberRepository.findTickersByIndexCode(FattoreIndexCodes.FAT50))
                .thenReturn(List.of("AAA", "BBB", "GONE"));
        MarketIndex mi = new MarketIndex();
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.of(mi));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(List.of("AAA", "BBB"), result.tickers());
    }

    @Test
    void rebuild_usHqIsEligibleWhereverIncorporated() {
        ListingIndexMetrics lin = metrics(listing("LIN", equityAsset(false)), bd("200"));
        lin.setCountryIncorp("Ireland");
        ListingIndexMetrics lulu = metrics(listing("LULU", equityAsset(false)), bd("150"));
        lulu.setCountryHq("British Columbia, Canada");
        ListingIndexMetrics foreign = metrics(listing("SPOT", equityAsset(false)), bd("300"));
        foreign.setCountryIncorp("Luxembourg");
        foreign.setCountryHq("Sweden");
        givenRows(lin, lulu, foreign, metrics(listing("AAPL", equityAsset(false)), bd("100")));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(List.of("LIN", "AAPL"), result.tickers());
    }

    @Test
    void rebuild_unknownHqFallsBackToIncorporation() {
        ListingIndexMetrics usIncorp = metrics(listing("USI", equityAsset(false)), bd("200"));
        usIncorp.setCountryHq("Unknown");
        ListingIndexMetrics foreignIncorp = metrics(listing("BMU", equityAsset(false)), bd("300"));
        foreignIncorp.setCountryHq("Unknown");
        foreignIncorp.setCountryIncorp("Bermuda");
        givenRows(usIncorp, foreignIncorp);

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(List.of("USI"), result.tickers());
    }

    @Test
    void rebuild_excludesPartnershipsAndPreferredStock() {
        ListingIndexMetrics mlp = metrics(listing("EPD", equityAsset(false)), bd("500"));
        mlp.setSecurityType("MLP");
        ListingIndexMetrics pref = metrics(listing("AGNCN", equityAsset(false)), bd("400"));
        pref.setSecurityType("Preferred Stock");
        ListingIndexMetrics legacy = metrics(listing("OLD", equityAsset(false)), bd("50"));
        legacy.setSecurityType(null);
        givenRows(mlp, pref, legacy, metrics(listing("AAPL", equityAsset(false)), bd("100")));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(List.of("AAPL", "OLD"), result.tickers());
    }

    @Test
    void rebuild_ranksByTotalMarketCapButWeightsByFreeFloat() {
        // Closely held: largest company by total cap, smallest by float. Ranking by float would drop it.
        ListingIndexMetrics held = metrics(listing("HELD", equityAsset(false)), bd("10"));
        held.setMarketCap(bd("1000"));
        ListingIndexMetrics a = metrics(listing("AAA", equityAsset(false)), bd("300"));
        ListingIndexMetrics b = metrics(listing("BBB", equityAsset(false)), bd("200"));
        ListingIndexMetrics c = metrics(listing("CCC", equityAsset(false)), bd("100"));
        givenRows(a, b, c, held);

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(List.of("HELD", "AAA", "BBB"), result.tickers());
        List<IndexMember> saved = savedMembers(3);
        assertEquals(0, bd("1.96078").compareTo(saved.get(0).getPercent()));
        assertEquals(0, bd("58.82353").compareTo(saved.get(1).getPercent()));
        assertEquals(0, bd("39.21569").compareTo(saved.get(2).getPercent()));
    }

    @Test
    void rebuild_multiClassCompanyRanksOnCombinedCapAndKeepsEveryClass() {
        // GOOGL and GOOG are each smaller than BIG, but together the company is the largest.
        Asset alphabet = equityAsset(false);
        ListingIndexMetrics googl = metrics(listing("GOOGL", alphabet), bd("150"));
        ListingIndexMetrics goog = metrics(listing("GOOG", alphabet), bd("140"));
        ListingIndexMetrics big = metrics(listing("BIG", equityAsset(false)), bd("200"));
        ListingIndexMetrics mid = metrics(listing("MID", equityAsset(false)), bd("100"));
        ListingIndexMetrics out = metrics(listing("OUT", equityAsset(false)), bd("90"));
        givenRows(big, goog, mid, googl, out);

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertEquals(List.of("GOOGL", "GOOG", "BIG", "MID"), result.tickers());
        assertEquals(3, result.companyCount());
        assertEquals(4, result.listingCount());
        assertFalse(result.partial());
        List<IndexMember> saved = savedMembers(4);
        BigDecimal sum = saved.stream().map(IndexMember::getPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100.00000").compareTo(sum));
        // Each class keeps its own float weight: 150 / 590.
        assertEquals(0, bd("25.42373").compareTo(saved.get(0).getPercent()));
    }

    @Test
    void rebuild_partialCountsCompaniesNotListings() {
        Asset dual = equityAsset(false);
        givenRows(
                metrics(listing("A1", dual), bd("50")),
                metrics(listing("A2", dual), bd("40")),
                metrics(listing("B", equityAsset(false)), bd("30")));

        FattoreIndexRebuildService.RebuildResult result = serviceFat50.rebuild(TEST_YEAR);

        assertTrue(result.partial());
        assertEquals(2, result.companyCount());
        assertEquals(3, result.listingCount());
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static long nextCik = 1;

    /** Each call is a distinct company; pass the same asset to two listings for a multi-class company. */
    private static Asset equityAsset(boolean fund) {
        Asset a = new Asset();
        long cik = nextCik++;
        a.setId(cik);
        a.setCik(cik);
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

    /** A fully floating US common stock: market cap equals free-float cap unless a test overrides it. */
    private static ListingIndexMetrics metrics(Listing listing, BigDecimal ffMc) {
        ListingIndexMetrics m = new ListingIndexMetrics();
        m.setListing(listing);
        m.setYear(TEST_YEAR);
        m.setMarketCap(ffMc);
        m.setFreeFloatMarketCap(ffMc);
        m.setPriceDate(PRICE_DATE);
        m.setCountryIncorp("United States");
        m.setCountryHq("United States");
        m.setSecurityType("Common Stock");
        return m;
    }

    private void givenRows(ListingIndexMetrics... rows) {
        when(metricsRepository.findAllWithListingAndAssetByYear(TEST_YEAR)).thenReturn(List.of(rows));
        when(marketIndexRepository.findByCode(FattoreIndexCodes.FAT50)).thenReturn(Optional.of(new MarketIndex()));
    }

    private List<IndexMember> savedMembers(int count) {
        ArgumentCaptor<IndexMember> cap = ArgumentCaptor.forClass(IndexMember.class);
        verify(indexMemberRepository, org.mockito.Mockito.times(count)).save(cap.capture());
        return cap.getAllValues();
    }
}

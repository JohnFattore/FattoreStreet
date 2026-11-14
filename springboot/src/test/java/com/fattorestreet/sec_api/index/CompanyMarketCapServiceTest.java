package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.index.CompanyMarketCapService.CompanyMarketCap;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyMarketCapServiceTest {

    @Mock
    private ListingIndexMetricsRepository metricsRepository;

    @InjectMocks
    private CompanyMarketCapService service;

    private final Asset asset = new Asset();

    private static Listing listing(long id) {
        Listing l = new Listing();
        l.setId(id);
        return l;
    }

    private static ListingIndexMetrics row(Listing listing, int year, String marketCap, LocalDate priceDate) {
        ListingIndexMetrics m = new ListingIndexMetrics();
        m.setListing(listing);
        m.setYear(year);
        m.setMarketCap(marketCap == null ? null : new BigDecimal(marketCap));
        m.setPriceDate(priceDate);
        return m;
    }

    @Test
    void neverSized_isEmpty() {
        when(metricsRepository.findByListing_Asset(asset)).thenReturn(List.of());
        assertTrue(service.latestFor(asset).isEmpty());
    }

    @Test
    void rowsWithoutCapOrDate_areIgnored() {
        Listing a = listing(1);
        when(metricsRepository.findByListing_Asset(asset)).thenReturn(List.of(
                row(a, 2026, null, LocalDate.of(2026, 11, 13)),
                row(a, 2025, "100", null)));
        assertTrue(service.latestFor(asset).isEmpty());
    }

    @Test
    void usesEachListingsNewestRow() {
        Listing a = listing(1);
        when(metricsRepository.findByListing_Asset(asset)).thenReturn(List.of(
                row(a, 2025, "100", LocalDate.of(2025, 12, 31)),
                row(a, 2026, "150", LocalDate.of(2026, 11, 13))));
        assertEquals(Optional.of(new CompanyMarketCap(new BigDecimal("150"), LocalDate.of(2026, 11, 13))),
                service.latestFor(asset));
    }

    @Test
    void sumsShareClasses_andDropsAClassThatStoppedTrading() {
        Listing classA = listing(1);
        Listing classC = listing(2);
        Listing retired = listing(3);
        when(metricsRepository.findByListing_Asset(asset)).thenReturn(List.of(
                row(classA, 2026, "1000", LocalDate.of(2026, 11, 13)),
                row(classC, 2026, "900", LocalDate.of(2026, 11, 12)),
                row(retired, 2026, "50", LocalDate.of(2026, 3, 2))));
        CompanyMarketCap result = service.latestFor(asset).orElseThrow();
        assertEquals(0, new BigDecimal("1900").compareTo(result.marketCap()));
        assertEquals(LocalDate.of(2026, 11, 13), result.asOf());
    }
}

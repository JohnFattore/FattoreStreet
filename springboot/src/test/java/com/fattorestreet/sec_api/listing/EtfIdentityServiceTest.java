package com.fattorestreet.sec_api.listing;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.ListingRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtfIdentityServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private WebService webService;

    @Test
    void enrichFundListingIdentities_resolvesAndMarksUnresolved() {
        Asset fundAsset = new Asset();
        fundAsset.setIsFund(true);

        Listing spy = new Listing();
        spy.setTicker("SPY");
        spy.setAsset(fundAsset);

        Listing unknown = new Listing();
        unknown.setTicker("ZZZZ");
        unknown.setAsset(fundAsset);

        when(listingRepository.findByAsset_IsFundTrue()).thenReturn(List.of(spy, unknown));
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "0": {
                    "ticker": "SPY",
                    "seriesId": "S000001",
                    "classId": "C000001",
                    "seriesName": "SPDR Series Trust",
                    "className": "SPY Class"
                  }
                }
                """);
        when(listingRepository.save(org.mockito.ArgumentMatchers.any(Listing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EtfIdentityService service = new EtfIdentityService(listingRepository, webService, new ObjectMapper());
        Map<String, Object> result = service.enrichFundListingIdentities(false);

        assertEquals(2, result.get("fundListingsTotal"));
        assertEquals(1, result.get("resolved"));
        assertEquals(1, result.get("unresolved"));

        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository, times(2)).save(listingCaptor.capture());
        List<Listing> savedListings = listingCaptor.getAllValues();

        Listing savedSpy = savedListings.stream().filter(l -> "SPY".equals(l.getTicker())).findFirst().orElseThrow();
        assertEquals("S000001", savedSpy.getSecSeriesId());
        assertEquals("C000001", savedSpy.getSecClassContractId());
        assertEquals("RESOLVED", savedSpy.getSecIdentityStatus());

        Listing savedUnknown = savedListings.stream().filter(l -> "ZZZZ".equals(l.getTicker())).findFirst().orElseThrow();
        assertEquals("UNRESOLVED", savedUnknown.getSecIdentityStatus());
        assertTrue(((List<?>) result.get("unresolvedTickersSample")).contains("ZZZZ"));
    }

    @Test
    void enrichFundListingIdentities_supportsTabularSecMfShape() {
        Asset fundAsset = new Asset();
        fundAsset.setIsFund(true);

        Listing voo = new Listing();
        voo.setTicker("VOO");
        voo.setAsset(fundAsset);

        when(listingRepository.findByAsset_IsFundTrue()).thenReturn(List.of(voo));
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "fields": ["cik", "seriesId", "classId", "symbol", "series_name", "class_name"],
                  "data": [
                    ["0000102909", "S000001", "C000001", "VOO", "Vanguard 500 Index Fund", "ETF Shares"]
                  ]
                }
                """);
        when(listingRepository.save(org.mockito.ArgumentMatchers.any(Listing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EtfIdentityService service = new EtfIdentityService(listingRepository, webService, new ObjectMapper());
        Map<String, Object> result = service.enrichFundListingIdentities(false);

        assertEquals(1, result.get("fundListingsTotal"));
        assertEquals(1, result.get("resolved"));
        assertEquals(0, result.get("unresolved"));

        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository, times(1)).save(listingCaptor.capture());
        Listing saved = listingCaptor.getValue();
        assertEquals("S000001", saved.getSecSeriesId());
        assertEquals("C000001", saved.getSecClassContractId());
        assertEquals("RESOLVED", saved.getSecIdentityStatus());
    }

    @Test
    void enrichFundListingIdentities_supportsFieldsDataObjectRows() {
        Asset fundAsset = new Asset();
        fundAsset.setIsFund(true);

        Listing voo = new Listing();
        voo.setTicker("VOO");
        voo.setAsset(fundAsset);

        when(listingRepository.findByAsset_IsFundTrue()).thenReturn(List.of(voo));
        when(webService.fetchSecMutualFundTickers()).thenReturn("""
                {
                  "fields": ["cik", "class_ticker", "series_name", "class_name"],
                  "data": [
                    {
                      "cik": "0000102909",
                      "class_ticker": "VOO",
                      "series_name": "Vanguard 500 Index Fund",
                      "class_name": "ETF Shares"
                    }
                  ]
                }
                """);
        when(listingRepository.save(org.mockito.ArgumentMatchers.any(Listing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EtfIdentityService service = new EtfIdentityService(listingRepository, webService, new ObjectMapper());
        Map<String, Object> result = service.enrichFundListingIdentities(false);

        assertEquals(1, result.get("fundListingsTotal"));
        assertEquals(1, result.get("resolved"));
        assertEquals(0, result.get("unresolved"));

        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository, times(1)).save(listingCaptor.capture());
        Listing saved = listingCaptor.getValue();
        assertEquals("RESOLVED", saved.getSecIdentityStatus());
    }
}

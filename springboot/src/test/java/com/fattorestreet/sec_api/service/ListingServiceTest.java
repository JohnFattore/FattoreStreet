package com.fattorestreet.sec_api.service;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingService listingService;

    @Test
    void createOrUpdateListing_existingTicker_updatesExisting() {
        Listing existing = new Listing();
        existing.setId(1L);
        existing.setTicker("AAPL");
        existing.setTitle("Apple Inc");

        Listing incoming = new Listing();
        incoming.setTicker("AAPL");
        incoming.setTitle("Apple Inc.");
        incoming.setAsset(new Asset());

        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(existing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        Listing result = listingService.createOrUpdateListing(incoming);

        assertEquals(1L, result.getId());
        verify(listingRepository).save(existing);
    }

    @Test
    void createOrUpdateListing_newTicker_creates() {
        Listing incoming = new Listing();
        incoming.setTicker("MSFT");
        incoming.setTitle("Microsoft Corporation");
        incoming.setAsset(new Asset());

        when(listingRepository.findByTicker("MSFT")).thenReturn(Optional.empty());
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        Listing result = listingService.createOrUpdateListing(incoming);

        assertEquals("MSFT", result.getTicker());
        verify(listingRepository).save(incoming);
    }
}

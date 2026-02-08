package com.example.sec_api.service;

import org.springframework.stereotype.Service;
import com.example.sec_api.model.Listing;
import com.example.sec_api.repository.ListingRepository;

@Service
public class ListingService {

    private final ListingRepository listingRepository;

    // constructor
    public ListingService(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    public Listing saveListing(Listing listing) {
        return listingRepository.save(listing);
    }

    public Listing createOrUpdateListing(Listing listing) {
        // Asset asset = assetRepository.findByCik(cik)
        // .orElseThrow(() -> new RuntimeException("Asset not found with CIK: " + cik));
        return listingRepository.findByTicker(listing.getTicker())
                .map(existing -> {
                    // update fields if needed
                    return listingRepository.save(existing);
                })
                .orElseGet(() -> {
                    return listingRepository.save(listing);
                });
    }
}
package com.fattorestreet.sec_api.service;

import org.springframework.stereotype.Service;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.ListingRepository;

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
                    existing.setTitle(listing.getTitle());
                    existing.setAsset(listing.getAsset());
                    existing.setSecSeriesId(listing.getSecSeriesId());
                    existing.setSecClassContractId(listing.getSecClassContractId());
                    existing.setSecClassTicker(listing.getSecClassTicker());
                    existing.setSecSeriesName(listing.getSecSeriesName());
                    existing.setSecClassName(listing.getSecClassName());
                    existing.setSecIdentityStatus(listing.getSecIdentityStatus());
                    return listingRepository.save(existing);
                })
                .orElseGet(() -> {
                    return listingRepository.save(listing);
                });
    }
}
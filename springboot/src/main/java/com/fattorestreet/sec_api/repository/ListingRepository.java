package com.fattorestreet.sec_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    // Optional: custom queries here
    Optional<Listing> findByTicker(String ticker);
    List<Listing> findByAsset(Asset asset);
    List<Listing> findByAsset_IsFundTrue();

}

package com.example.sec_api.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.sec_api.model.Listing;
import com.example.sec_api.model.Asset;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    // Optional: custom queries here
    Optional<Listing> findByTicker(String ticker);
    List<Listing> findByAsset(Asset asset);

}
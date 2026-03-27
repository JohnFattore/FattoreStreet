package com.fattorestreet.sec_api.repository;

import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ListingIndexMetricsRepository extends JpaRepository<ListingIndexMetrics, Long> {

    Optional<ListingIndexMetrics> findByListing(Listing listing);

    Optional<ListingIndexMetrics> findByListing_Ticker(String ticker);

    @Query("SELECT DISTINCT m FROM ListingIndexMetrics m "
            + "JOIN FETCH m.listing l "
            + "LEFT JOIN FETCH l.asset")
    List<ListingIndexMetrics> findAllWithListingAndAsset();
}

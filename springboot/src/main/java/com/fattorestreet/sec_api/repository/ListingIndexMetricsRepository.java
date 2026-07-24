package com.fattorestreet.sec_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;

public interface ListingIndexMetricsRepository extends JpaRepository<ListingIndexMetrics, Long> {

    Optional<ListingIndexMetrics> findByListingAndYear(Listing listing, int year);

    @Query("SELECT DISTINCT m FROM ListingIndexMetrics m "
            + "JOIN FETCH m.listing l "
            + "LEFT JOIN FETCH l.asset "
            + "WHERE m.year = :year")
    List<ListingIndexMetrics> findAllWithListingAndAssetByYear(@Param("year") int year);

    List<ListingIndexMetrics> findAllByYear(int year);
}

package com.fattorestreet.sec_api.repository;

import java.util.*;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fattorestreet.sec_api.model.Asset;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByCik(Long cik);

    Asset findByListings_Ticker(String ticker);

    List<Asset> findByIsFund(Boolean isFund);

    @Query("SELECT DISTINCT a FROM Asset a JOIN a.listings l WHERE l.ticker IN :tickers AND a.isFund = false")
    List<Asset> findByTickersIn(@Param("tickers") Collection<String> tickers);

    /**
     * All assets with listings eagerly loaded (for callers that iterate tickers outside a long session).
     */
    @Query("SELECT DISTINCT a FROM Asset a LEFT JOIN FETCH a.listings")
    List<Asset> findAllWithListings();

    long countByIsFund(Boolean isFund);
}

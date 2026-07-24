package com.fattorestreet.sec_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fattorestreet.sec_api.model.MarketIndex;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, Long> {

    Optional<MarketIndex> findByCode(String code);

    Optional<MarketIndex> findByDisplayName(String displayName);
}

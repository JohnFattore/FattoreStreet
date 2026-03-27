package com.fattorestreet.sec_api.repository;

import com.fattorestreet.sec_api.model.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, Long> {

    Optional<MarketIndex> findByCode(String code);

    Optional<MarketIndex> findByDisplayName(String displayName);
}

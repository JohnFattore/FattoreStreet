package com.fattorestreet.sec_api.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Quarter;

public interface QuarterRepository extends JpaRepository<Quarter, Long> {
    Optional<Quarter> findByAssetAndPeriodStartAndPeriodEnd(Asset asset, LocalDate periodStart, LocalDate periodEnd);

    Optional<Quarter> findByAssetAndYearAndQuarter(Asset asset, Integer year, Integer quarter);

    List<Quarter> findByAsset(Asset asset);

    List<Quarter> findByYearAndQuarter(Integer year, Integer quarter);
}

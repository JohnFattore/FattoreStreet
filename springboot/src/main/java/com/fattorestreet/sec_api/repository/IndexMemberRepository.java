package com.fattorestreet.sec_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fattorestreet.sec_api.model.IndexMember;

public interface IndexMemberRepository extends JpaRepository<IndexMember, Long> {

    @Query("SELECT DISTINCT im FROM IndexMember im "
            + "JOIN FETCH im.listing l "
            + "JOIN FETCH im.marketIndex "
            + "LEFT JOIN FETCH l.asset")
    List<IndexMember> findAllWithListingAndAsset();

    List<IndexMember> findByMarketIndex_CodeOrderByPercentDesc(String code);

    @Query("SELECT DISTINCT im.listing.ticker FROM IndexMember im WHERE im.marketIndex.code = :code")
    List<String> findTickersByIndexCode(@Param("code") String code);

    void deleteByMarketIndex_Code(String code);

    Optional<IndexMember> findByMarketIndex_CodeAndListing_Ticker(String code, String ticker);
}

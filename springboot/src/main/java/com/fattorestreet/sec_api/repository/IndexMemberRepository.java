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

    /**
     * Members of one index, heaviest weight first, with {@code listing}, {@code listing.asset} and
     * {@code marketIndex} fetch-joined into the same query. Callers map every member's listing, so a
     * plain derived query would lazy-load each listing separately (one query per member).
     */
    @Query("SELECT im FROM IndexMember im "
            + "JOIN FETCH im.listing l "
            + "JOIN FETCH im.marketIndex mi "
            + "LEFT JOIN FETCH l.asset "
            + "WHERE mi.code = :code "
            + "ORDER BY im.percent DESC")
    List<IndexMember> findByIndexCodeWithListingAndAsset(@Param("code") String code);

    @Query("SELECT DISTINCT im.listing.ticker FROM IndexMember im WHERE im.marketIndex.code = :code")
    List<String> findTickersByIndexCode(@Param("code") String code);

    /**
     * Tickers of one index, heaviest weight first. A projection rather than entities, because
     * callers outside a transaction cannot walk the lazy {@code listing} association -- and
     * ordering matters wherever a caller takes only the first N names.
     */
    @Query("SELECT im.listing.ticker FROM IndexMember im WHERE im.marketIndex.code = :code "
            + "ORDER BY im.percent DESC")
    List<String> findTickersByIndexCodeOrderByPercentDesc(@Param("code") String code);

    void deleteByMarketIndex_Code(String code);

    Optional<IndexMember> findByMarketIndex_CodeAndListing_Ticker(String code, String ticker);
}

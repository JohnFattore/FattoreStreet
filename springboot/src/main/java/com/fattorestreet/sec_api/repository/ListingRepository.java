package com.fattorestreet.sec_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    // Optional: custom queries here
    Optional<Listing> findByTicker(String ticker);
    List<Listing> findByAsset(Asset asset);
    List<Listing> findByAsset_IsFundTrue();

    /** One row of a name/ticker search; see {@link #searchByNameOrTicker}. */
    interface SearchRow {
        String getTicker();
        String getTitle();
        String getSecSeriesName();
        Boolean getIsFund();
    }

    /**
     * Sets the word-similarity cutoff the {@code <%} operator uses for the rest of the current
     * transaction. The pg_trgm default (0.6) rejects one-letter typos in short names, and the
     * operator form is what lets the trigram indexes serve the search, so the cutoff is lowered
     * per transaction rather than filtering on {@code word_similarity(...)}, which would scan.
     * Postgres-only: must run inside the same transaction as the search.
     */
    @Query(value = "SELECT set_config('pg_trgm.word_similarity_threshold', :threshold, true)", nativeQuery = true)
    String setWordSimilarityThreshold(@Param("threshold") String threshold);

    /**
     * Listings whose ticker equals or starts with {@code tickerQuery}, or whose company or fund
     * name word-matches {@code nameQuery}, best match first: exact ticker, then name prefix,
     * then ticker prefix, then name substring, then trigram similarity, then the shorter name.
     *
     * @param nameQuery    lowercased, whitespace-collapsed query, for similarity
     * @param tickerQuery  uppercased query with "." mapped to "-" (SEC ticker form)
     * @param tickerPrefix LIKE pattern: escaped {@code tickerQuery} + "%"
     * @param namePrefix   LIKE pattern: escaped {@code nameQuery} + "%"
     * @param nameContains LIKE pattern: "%" + escaped {@code nameQuery} + "%"
     */
    @Query(value = """
            WITH candidates AS (
                SELECT id FROM listings WHERE ticker = :tickerQuery OR ticker LIKE :tickerPrefix
                UNION
                SELECT id FROM listings WHERE :nameQuery <% lower(title)
                UNION
                SELECT id FROM listings WHERE :nameQuery <% lower(sec_series_name)
            )
            SELECT l.ticker AS "ticker",
                   l.title AS "title",
                   l.sec_series_name AS "secSeriesName",
                   coalesce(a.is_fund, false) AS "isFund"
            FROM candidates c
            JOIN listings l ON l.id = c.id
            LEFT JOIN assets a ON a.id = l.asset_id
            WHERE :includeFunds OR a.is_fund IS NOT TRUE
            ORDER BY
                CASE
                    WHEN l.ticker = :tickerQuery THEN 0
                    WHEN lower(coalesce(l.sec_series_name, l.title)) LIKE :namePrefix THEN 1
                    WHEN l.ticker LIKE :tickerPrefix THEN 2
                    WHEN lower(coalesce(l.sec_series_name, l.title)) LIKE :nameContains THEN 3
                    ELSE 4
                END,
                greatest(word_similarity(:nameQuery, lower(l.title)),
                         coalesce(word_similarity(:nameQuery, lower(l.sec_series_name)), 0)) DESC,
                length(coalesce(l.sec_series_name, l.title)),
                length(l.ticker),
                l.ticker
            LIMIT :limit
            """, nativeQuery = true)
    List<SearchRow> searchByNameOrTicker(
            @Param("nameQuery") String nameQuery,
            @Param("tickerQuery") String tickerQuery,
            @Param("tickerPrefix") String tickerPrefix,
            @Param("namePrefix") String namePrefix,
            @Param("nameContains") String nameContains,
            @Param("includeFunds") boolean includeFunds,
            @Param("limit") int limit);
}

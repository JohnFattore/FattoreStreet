package com.fattorestreet.sec_api.listing;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fattorestreet.sec_api.repository.ListingRepository;
import com.fattorestreet.sec_api.repository.ListingRepository.SearchRow;

/** Company and fund lookup by name or ticker, for the site-wide search box. */
@Service
public class ListingSearchService {

    /** Low enough that a one-letter typo ("micrsoft") still matches, high enough to drop noise. */
    static final String WORD_SIMILARITY_THRESHOLD = "0.4";

    private final ListingRepository listingRepository;

    public ListingSearchService(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    /**
     * A match: the SEC ticker and a display name. {@code name} is null when the universe holds no
     * real name for the listing (most fund tickers, whose stored title is the ticker itself).
     */
    public record SearchResult(String ticker, String name, boolean isFund) {
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(String query, int limit, boolean includeFunds) {
        String nameQuery = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
        if (nameQuery.isEmpty()) {
            return List.of();
        }
        // Users type exchange-style share classes (BRK.B); the universe stores SEC form (BRK-B)
        String tickerQuery = nameQuery.toUpperCase(Locale.US).replace('.', '-');
        String escapedName = escapeLike(nameQuery);

        listingRepository.setWordSimilarityThreshold(WORD_SIMILARITY_THRESHOLD);
        List<SearchRow> rows = listingRepository.searchByNameOrTicker(
                nameQuery,
                tickerQuery,
                escapeLike(tickerQuery) + "%",
                escapedName + "%",
                "%" + escapedName + "%",
                includeFunds,
                limit);
        return rows.stream()
                .map(row -> new SearchResult(row.getTicker(), displayName(row), Boolean.TRUE.equals(row.getIsFund())))
                .toList();
    }

    private static String displayName(SearchRow row) {
        String name = row.getSecSeriesName() != null && !row.getSecSeriesName().isBlank()
                ? row.getSecSeriesName()
                : row.getTitle();
        if (name == null || name.isBlank() || name.equalsIgnoreCase(row.getTicker())) {
            return null;
        }
        return name;
    }

    /** Escapes LIKE wildcards so a typed "_" or "%" matches literally (Postgres' default escape is "\"). */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

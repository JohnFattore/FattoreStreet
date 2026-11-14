package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;

/**
 * A company's latest market cap, read from the per-listing figures the index refresh stores (SEC share
 * count times IEX close), so every page shows the same size the indexes rank on. A multi-class company is
 * the sum of its listed classes, each sized from its own count.
 */
@Service
public class CompanyMarketCapService {

    private final ListingIndexMetricsRepository metricsRepository;

    public CompanyMarketCapService(ListingIndexMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    /** Market cap and the trade date of the newest close behind it. */
    public record CompanyMarketCap(BigDecimal marketCap, LocalDate asOf) {
    }

    /**
     * Sums each listing's most recent sized row. A class priced more than
     * {@link FattoreIndexRebuildService#MAX_PRICE_AGE_DAYS} before the newest one has stopped trading
     * (a retired class or old ticker), so it is left out, as the index rebuild does. Empty when the
     * company has never been sized.
     */
    public Optional<CompanyMarketCap> latestFor(Asset asset) {
        Collection<ListingIndexMetrics> latestPerListing = metricsRepository.findByListing_Asset(asset).stream()
                .filter(m -> m.getMarketCap() != null && m.getPriceDate() != null)
                .collect(Collectors.toMap(
                        m -> m.getListing().getId(),
                        m -> m,
                        BinaryOperator.maxBy(Comparator.comparing(ListingIndexMetrics::getPriceDate))))
                .values();
        Optional<LocalDate> newest = latestPerListing.stream()
                .map(ListingIndexMetrics::getPriceDate)
                .max(Comparator.naturalOrder());
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        LocalDate cutoff = newest.get().minusDays(FattoreIndexRebuildService.MAX_PRICE_AGE_DAYS);
        BigDecimal total = latestPerListing.stream()
                .filter(m -> !m.getPriceDate().isBefore(cutoff))
                .map(ListingIndexMetrics::getMarketCap)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(new CompanyMarketCap(total, newest.get()));
    }
}

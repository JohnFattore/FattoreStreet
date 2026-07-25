package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fattorestreet.sec_api.model.IndexMember;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.repository.IndexMemberRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.util.MarketTime;

@Service
public class IndexMemberApiService {

    private final IndexMemberRepository indexMemberRepository;
    private final ListingIndexMetricsRepository metricsRepository;

    public IndexMemberApiService(
            IndexMemberRepository indexMemberRepository,
            ListingIndexMetricsRepository metricsRepository) {
        this.indexMemberRepository = indexMemberRepository;
        this.metricsRepository = metricsRepository;
    }

    @Transactional(readOnly = true)
    public List<IndexMemberRow> listAll() {
        List<IndexMember> members = indexMemberRepository.findAllWithListingAndAsset();
        int currentYear = Year.now(MarketTime.MARKET).getValue();
        Map<Long, ListingIndexMetrics> byListingId = metricsRepository.findAllByYear(currentYear).stream()
                .filter(m -> m.getListing() != null && m.getListing().getId() != null)
                .collect(Collectors.toMap(m -> m.getListing().getId(), m -> m, (a, b) -> a));
        return members.stream()
                .map(im -> toRow(im, byListingId.get(im.getListing().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IndexMemberRow> listByIndexCode(String code) {
        List<IndexMember> members = indexMemberRepository.findByMarketIndex_CodeOrderByPercentDesc(code);
        int currentYear = Year.now(MarketTime.MARKET).getValue();
        Map<Long, ListingIndexMetrics> byListingId = metricsRepository.findAllByYear(currentYear).stream()
                .filter(m -> m.getListing() != null && m.getListing().getId() != null)
                .collect(Collectors.toMap(m -> m.getListing().getId(), m -> m, (a, b) -> a));
        return members.stream()
                .filter(im -> im.getListing() != null && im.getListing().getId() != null)
                .map(im -> toRow(im, byListingId.get(im.getListing().getId())))
                .toList();
    }

    private IndexMemberRow toRow(IndexMember im, ListingIndexMetrics m) {
        Listing listing = im.getListing();
        StockRow stock = buildStock(listing, m);
        String indexLabel = im.getMarketIndex() != null && im.getMarketIndex().getDisplayName() != null
                ? im.getMarketIndex().getDisplayName()
                : "";
        return new IndexMemberRow(
                im.getId(),
                im.getPercent(),
                indexLabel,
                im.isOutlier(),
                im.getNotes() != null ? im.getNotes() : "",
                stock
        );
    }

    private StockRow buildStock(Listing listing, ListingIndexMetrics m) {
        BigDecimal zero = BigDecimal.ZERO;
        if (m == null) {
            return new StockRow(
                    listing.getTicker(),
                    listing.getTitle(),
                    zero,
                    zero,
                    zero,
                    zero,
                    zero,
                    "United States",
                    "United States",
                    null,
                    null,
                    "Common Stock",
                    0
            );
        }
        return new StockRow(
                listing.getTicker(),
                listing.getTitle(),
                nz(m.getMarketCap()),
                nz(m.getVolume()),
                nz(m.getVolumeUsd()),
                nz(m.getFreeFloat()),
                nz(m.getFreeFloatMarketCap()),
                m.getCountryIncorp() != null ? m.getCountryIncorp() : "United States",
                m.getCountryHq() != null ? m.getCountryHq() : "United States",
                m.getStateIncorp(),
                m.getStateHq(),
                m.getSecurityType() != null ? m.getSecurityType() : "Common Stock",
                m.getYearIpo() != null ? m.getYearIpo() : 0
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Mirrors Django {@code StockSerializer} field names (camelCase).
     */
    public record StockRow(
            String ticker,
            String name,
            BigDecimal marketCap,
            BigDecimal volume,
            @JsonProperty("volumeUSD") BigDecimal volumeUsd,
            BigDecimal freeFloat,
            @JsonProperty("freeFloatMarketCap") BigDecimal freeFloatMarketCap,
            String countryIncorp,
            String countryHQ,
            String stateIncorp,
            @JsonProperty("stateHQ") String stateHq,
            String securityType,
            Integer yearIPO
    ) {
    }

    public record IndexMemberRow(
            Long id,
            BigDecimal percent,
            @JsonProperty("index") String index,
            boolean outlier,
            String notes,
            StockRow stock
    ) {
    }
}

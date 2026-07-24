package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.IndexMember;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.model.ListingIndexMetrics;
import com.fattorestreet.sec_api.model.MarketIndex;
import com.fattorestreet.sec_api.repository.IndexMemberRepository;
import com.fattorestreet.sec_api.repository.ListingIndexMetricsRepository;
import com.fattorestreet.sec_api.repository.MarketIndexRepository;

/**
 * Rebuilds a Russell-style cap-ranked index: top listings by
 * {@link ListingIndexMetrics#getFreeFloatMarketCap()}, cap-weighted.
 * One instance per index (e.g. Fattore 50 vs Fattore 100); see {@link com.fattorestreet.sec_api.config.FattoreIndexRebuildConfig}.
 */
public class FattoreIndexRebuildService {

    private static final int PERCENT_SCALE = 5;

    private final ListingIndexMetricsRepository metricsRepository;
    private final MarketIndexRepository marketIndexRepository;
    private final IndexMemberRepository indexMemberRepository;
    private final String indexCode;
    private final String displayName;
    private final int topN;

    public FattoreIndexRebuildService(
            ListingIndexMetricsRepository metricsRepository,
            MarketIndexRepository marketIndexRepository,
            IndexMemberRepository indexMemberRepository,
            String indexCode,
            String displayName,
            int topN) {
        this.metricsRepository = metricsRepository;
        this.marketIndexRepository = marketIndexRepository;
        this.indexMemberRepository = indexMemberRepository;
        this.indexCode = indexCode;
        this.displayName = displayName;
        this.topN = topN;
    }

    public String getIndexCode() {
        return indexCode;
    }

    public record RebuildResult(
            String indexCode,
            int year,
            int memberCount,
            boolean partial,
            BigDecimal totalFreeFloatMarketCap,
            List<String> tickers
    ) {
    }

    @Transactional
    public RebuildResult rebuild(int year) {
        List<ListingIndexMetrics> rows = metricsRepository.findAllWithListingAndAssetByYear(year);
        List<ListingIndexMetrics> eligible = rows.stream()
                .filter(this::isEligible)
                .sorted(Comparator
                        .comparing(ListingIndexMetrics::getFreeFloatMarketCap, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(m -> m.getListing().getTicker(), Comparator.nullsLast(String::compareTo)))
                .toList();

        int take = Math.min(topN, eligible.size());
        List<ListingIndexMetrics> top = eligible.subList(0, take);

        if (top.isEmpty()) {
            upsertMarketIndex();
            indexMemberRepository.deleteByMarketIndex_Code(indexCode);
            return new RebuildResult(indexCode, year, 0, true, BigDecimal.ZERO, List.of());
        }

        BigDecimal totalFf = top.stream()
                .map(ListingIndexMetrics::getFreeFloatMarketCap)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> percents = computeCapWeights(top, totalFf);

        MarketIndex marketIndex = upsertMarketIndex();
        indexMemberRepository.deleteByMarketIndex_Code(indexCode);

        List<String> tickers = new ArrayList<>(take);
        for (int i = 0; i < top.size(); i++) {
            ListingIndexMetrics m = top.get(i);
            Listing listing = m.getListing();
            tickers.add(listing.getTicker());
            IndexMember im = new IndexMember();
            im.setListing(listing);
            im.setMarketIndex(marketIndex);
            im.setPercent(percents.get(i));
            im.setOutlier(false);
            im.setNotes("");
            indexMemberRepository.save(im);
        }

        boolean partial = eligible.size() < topN;
        return new RebuildResult(indexCode, year, take, partial, totalFf, List.copyOf(tickers));
    }

    private boolean isEligible(ListingIndexMetrics m) {
        Listing listing = m.getListing();
        if (listing == null) {
            return false;
        }
        Asset asset = listing.getAsset();
        if (asset == null || Boolean.TRUE.equals(asset.getIsFund())) {
            return false;
        }
        // SEC leaves incorporation blank for some companies (e.g. AVGO after redomiciliation).
        // Accept US-headquartered companies when incorporation is unknown.
        String incorp = m.getCountryIncorp();
        if (!"United States".equals(incorp)) {
            if ("Unknown".equals(incorp) && "United States".equals(m.getCountryHq())) {
                // fall through — treat as eligible
            } else {
                return false;
            }
        }
        BigDecimal ff = m.getFreeFloatMarketCap();
        return ff != null && ff.compareTo(BigDecimal.ZERO) > 0;
    }

    private MarketIndex upsertMarketIndex() {
        return marketIndexRepository.findByCode(indexCode).orElseGet(() -> {
            MarketIndex mi = new MarketIndex();
            mi.setCode(indexCode);
            mi.setDisplayName(displayName);
            return marketIndexRepository.save(mi);
        });
    }

    /**
     * Cap weights in percent; last member absorbs rounding so the sum is exactly 100.
     */
    private static List<BigDecimal> computeCapWeights(List<ListingIndexMetrics> top, BigDecimal totalFf) {
        int n = top.size();
        List<BigDecimal> out = new ArrayList<>(n);
        if (n == 1) {
            out.add(new BigDecimal("100.00000"));
            return out;
        }
        BigDecimal hundred = new BigDecimal("100");
        BigDecimal acc = BigDecimal.ZERO;
        for (int i = 0; i < n - 1; i++) {
            BigDecimal ff = top.get(i).getFreeFloatMarketCap();
            BigDecimal p = ff.multiply(hundred).divide(totalFf, PERCENT_SCALE, RoundingMode.HALF_UP);
            out.add(p);
            acc = acc.add(p);
        }
        BigDecimal last = hundred.subtract(acc).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        out.add(last);
        return out;
    }
}

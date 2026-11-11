package com.fattorestreet.sec_api.index;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 * Rebuilds a Russell-style cap-ranked index. Companies (assets, keyed by CIK) are ranked by total
 * market cap, the sum of {@link ListingIndexMetrics#getMarketCap()} over their eligible listed
 * classes; every eligible listing of the top N companies is a member, weighted by its own
 * {@link ListingIndexMetrics#getFreeFloatMarketCap()}. A multi-class company therefore adds more
 * than one member row, so an index holds at least N listings.
 * One instance per index (e.g. Fattore 50 vs Fattore 100); see {@link com.fattorestreet.sec_api.config.FattoreIndexRebuildConfig}.
 */
public class FattoreIndexRebuildService {

    private static final int PERCENT_SCALE = 5;
    private static final String UNITED_STATES = "United States";
    private static final String UNKNOWN_COUNTRY = "Unknown";
    private static final String COMMON_STOCK = "Common Stock";

    /**
     * A row priced more than this many days before the newest row is stale: its ticker has stopped
     * printing on IEX (delisted, acquired, renamed) or has left the refresh scope. A week covers a
     * long holiday weekend plus one failed nightly refresh.
     */
    static final int MAX_PRICE_AGE_DAYS = 7;

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

    /**
     * {@code companyCount} is the number of companies in the index (at most topN) and
     * {@code listingCount} the number of member rows, one per listed class. {@code partial} means
     * fewer than topN companies were eligible.
     */
    public record RebuildResult(
            String indexCode,
            int year,
            int companyCount,
            int listingCount,
            boolean partial,
            BigDecimal totalFreeFloatMarketCap,
            List<String> tickers
    ) {
    }

    @Transactional
    public RebuildResult rebuild(int year) {
        List<ListingIndexMetrics> rows = withFreshPrices(metricsRepository.findAllWithListingAndAssetByYear(year));
        guardAgainstShrinking(rows);
        List<List<ListingIndexMetrics>> companies = rankCompanies(rows.stream().filter(this::isEligible).toList());
        List<List<ListingIndexMetrics>> topCompanies = companies.subList(0, Math.min(topN, companies.size()));
        List<ListingIndexMetrics> top = topCompanies.stream().flatMap(List::stream).toList();
        boolean partial = companies.size() < topN;

        if (top.isEmpty()) {
            upsertMarketIndex();
            indexMemberRepository.deleteByMarketIndex_Code(indexCode);
            return new RebuildResult(indexCode, year, 0, 0, true, BigDecimal.ZERO, List.of());
        }

        BigDecimal totalFf = top.stream()
                .map(ListingIndexMetrics::getFreeFloatMarketCap)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> percents = computeCapWeights(top, totalFf);

        MarketIndex marketIndex = upsertMarketIndex();
        indexMemberRepository.deleteByMarketIndex_Code(indexCode);

        List<String> tickers = new ArrayList<>(top.size());
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

        return new RebuildResult(
                indexCode, year, topCompanies.size(), top.size(), partial, totalFf, List.copyOf(tickers));
    }

    /**
     * Groups eligible listings by company (CIK) and orders companies by total market cap, largest
     * first, ties broken by the company's first ticker so the order is stable. Within a company,
     * listings are ordered by free-float cap, largest first, then ticker.
     */
    static List<List<ListingIndexMetrics>> rankCompanies(List<ListingIndexMetrics> eligible) {
        Comparator<ListingIndexMetrics> byFloatCap = Comparator
                .comparing(ListingIndexMetrics::getFreeFloatMarketCap, Comparator.reverseOrder())
                .thenComparing(m -> m.getListing().getTicker(), Comparator.nullsLast(String::compareTo));
        Map<Long, List<ListingIndexMetrics>> byCik = new LinkedHashMap<>();
        for (ListingIndexMetrics m : eligible) {
            byCik.computeIfAbsent(m.getListing().getAsset().getCik(), k -> new ArrayList<>()).add(m);
        }
        List<List<ListingIndexMetrics>> companies = new ArrayList<>(byCik.size());
        for (List<ListingIndexMetrics> listings : byCik.values()) {
            listings.sort(byFloatCap);
            companies.add(List.copyOf(listings));
        }
        companies.sort(Comparator
                .comparing(FattoreIndexRebuildService::totalMarketCap, Comparator.reverseOrder())
                .thenComparing(c -> c.get(0).getListing().getTicker(), Comparator.nullsLast(String::compareTo)));
        return companies;
    }

    private static BigDecimal totalMarketCap(List<ListingIndexMetrics> company) {
        return company.stream()
                .map(ListingIndexMetrics::getMarketCap)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Throws, before anything is deleted, when more than half of the index's current members have
     * no fresh row. Daily turnover moves a few names at the tail; losing half the index means the
     * freshness cutoff is reacting to a partial refresh (a single-ticker run, a run that died
     * midway), not to the market. Measured against current members rather than all rows, because
     * rows left over from wider refresh scopes are stale by design and would trip a row-based
     * guard every night. The index-load runner turns the exception into a failed task, and the
     * transaction rollback keeps yesterday's members.
     */
    private void guardAgainstShrinking(List<ListingIndexMetrics> freshRows) {
        List<String> currentMembers = indexMemberRepository.findTickersByIndexCode(indexCode);
        if (currentMembers.isEmpty()) {
            return;
        }
        Set<String> freshTickers = freshRows.stream()
                .map(m -> m.getListing() != null ? m.getListing().getTicker() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long lost = currentMembers.stream().filter(t -> !freshTickers.contains(t)).count();
        if (lost * 2 > currentMembers.size()) {
            throw new IllegalStateException(String.format(
                    "%s rebuild skipped: %d of %d current members have no row priced within %d days of the "
                            + "newest; keeping existing members",
                    indexCode, lost, currentMembers.size(), MAX_PRICE_AGE_DAYS));
        }
    }

    /**
     * Drops rows whose price is more than {@link #MAX_PRICE_AGE_DAYS} older than the newest row's.
     * Measured against the data rather than the clock, so a failed price load ages nothing out. A
     * row with no price date is stale: migration V5 dated every existing row, so only a listing
     * the refresh has never priced lacks one.
     */
    static List<ListingIndexMetrics> withFreshPrices(List<ListingIndexMetrics> rows) {
        LocalDate newest = rows.stream()
                .map(ListingIndexMetrics::getPriceDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (newest == null) {
            return List.of();
        }
        LocalDate cutoff = newest.minusDays(MAX_PRICE_AGE_DAYS);
        return rows.stream()
                .filter(m -> m.getPriceDate() != null && !m.getPriceDate().isBefore(cutoff))
                .toList();
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
        // Null predates the column default; the entity and the public API both read it as common stock.
        if (m.getSecurityType() != null && !COMMON_STOCK.equals(m.getSecurityType())) {
            return false;
        }
        if (!isUsCompany(m)) {
            return false;
        }
        BigDecimal ff = m.getFreeFloatMarketCap();
        return ff != null && ff.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Headquarters decides: a US-headquartered company is eligible wherever it is incorporated
     * (Ireland, Bermuda, Switzerland...). When SEC gives no business address, US incorporation
     * stands in for it.
     */
    private static boolean isUsCompany(ListingIndexMetrics m) {
        String hq = m.getCountryHq();
        if (hq == null || UNKNOWN_COUNTRY.equals(hq)) {
            return UNITED_STATES.equals(m.getCountryIncorp());
        }
        return UNITED_STATES.equals(hq);
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

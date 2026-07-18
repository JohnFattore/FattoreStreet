package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Corroborates equity splits against the raw (unadjusted) IEX close series in daily_prices.
 * A split with price multiplier {@code ratio} (old/new) shows up as an overnight
 * {@code prevClose/close ≈ 1/ratio} on the first trade date at the new price basis — exactly
 * the effective-date convention {@code PriceAdjustmentService.applyAdjustments} expects.
 * All comparisons are done in log space so forward and reverse splits are treated symmetrically.
 */
@Component
public class SplitPriceCorroborator {

    /** Multipliers (new/old shares) scanned for price-first detection of missed splits. */
    private static final double[] SCAN_MULTIPLIERS = buildScanMultipliers();
    /** Band for corroborating an XBRL-asserted multiplier ≥2x: the split move dominates market noise. */
    private static final double WIDE_TOLERANCE_LOG = Math.log(1.15);
    /** Band for extended ratios (3:2, 4:3) whose moves overlap large natural gaps; must stay tight. */
    private static final double TIGHT_TOLERANCE_LOG = Math.log(1.06);
    /** Band for snapping an unexplained overnight move to a scan multiplier. */
    private static final double SCAN_TOLERANCE_LOG = Math.log(1.10);
    /** Band for the median-level persistence check that filters one-day glitches and V-shaped crashes. */
    private static final double PERSISTENCE_TOLERANCE_LOG = Math.log(1.25);
    private static final int PERSISTENCE_WINDOW_DAYS = 5;

    private final DailyPriceRepository dailyPriceRepository;

    public SplitPriceCorroborator(DailyPriceRepository dailyPriceRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
    }

    /** Raw close series in ascending trade-date order; rows without a positive close are dropped. */
    public record PriceSeries(List<LocalDate> dates, double[] closes) {
        public boolean isEmpty() {
            return dates.isEmpty();
        }

        public LocalDate firstDate() {
            return dates.get(0);
        }

        public LocalDate lastDate() {
            return dates.get(dates.size() - 1);
        }
    }

    /**
     * @param breakDate first trade date at the new price basis
     * @param observedMultiplier prevClose/close on the break date (estimate of new/old shares)
     * @param snappedMultiplier the candidate multiplier the move was matched to
     * @param logDistance |ln(observed) − ln(snapped)|
     */
    public record Corroboration(
            LocalDate breakDate,
            double observedMultiplier,
            double snappedMultiplier,
            double logDistance) {
    }

    public PriceSeries load(String ticker) {
        List<DailyPrice> rows = dailyPriceRepository.findByTickerOrderByTradeDateAsc(ticker);
        List<LocalDate> dates = new ArrayList<>(rows.size());
        double[] closes = new double[rows.size()];
        int n = 0;
        for (DailyPrice row : rows) {
            if (row.getClosePrice() == null || row.getClosePrice() <= 0 || row.getTradeDate() == null) {
                continue;
            }
            dates.add(row.getTradeDate());
            closes[n++] = row.getClosePrice();
        }
        return new PriceSeries(dates, Arrays.copyOf(closes, n));
    }

    /**
     * Finds the trading day inside the window whose overnight move best matches {@code multiplier}
     * (new/old shares), or empty when no move falls within the tolerance band.
     */
    public Optional<Corroboration> corroborate(
            PriceSeries series, double multiplier, LocalDate windowStart, LocalDate windowEnd) {
        if (series.isEmpty() || multiplier <= 0) {
            return Optional.empty();
        }
        double targetLog = Math.log(multiplier);
        double tolerance = toleranceFor(multiplier);
        Corroboration best = null;
        for (int i = 1; i < series.dates().size(); i++) {
            LocalDate date = series.dates().get(i);
            if (date.isBefore(windowStart) || date.isAfter(windowEnd)) {
                continue;
            }
            double observed = series.closes()[i - 1] / series.closes()[i];
            double distance = Math.abs(Math.log(observed) - targetLog);
            if (distance <= tolerance && (best == null || distance < best.logDistance())) {
                best = new Corroboration(date, observed, multiplier, distance);
            }
        }
        return Optional.ofNullable(best);
    }

    /** True when the price series spans the whole window, so an absent break is evidence of absence. */
    public boolean windowFullyCovered(PriceSeries series, LocalDate windowStart, LocalDate windowEnd) {
        return !series.isEmpty()
                && !series.firstDate().isAfter(windowStart)
                && !series.lastDate().isBefore(windowEnd);
    }

    /**
     * Scans the whole series for overnight moves that snap to a plausible split multiplier and
     * whose surrounding median price levels confirm the move persisted (not a glitch or a
     * crash-and-recover day).
     */
    public List<Corroboration> scanForSplitLikeBreaks(PriceSeries series, LocalDate minDate) {
        List<Corroboration> breaks = new ArrayList<>();
        for (int i = 1; i < series.dates().size(); i++) {
            LocalDate date = series.dates().get(i);
            if (minDate != null && date.isBefore(minDate)) {
                continue;
            }
            double observed = series.closes()[i - 1] / series.closes()[i];
            double observedLog = Math.log(observed);
            double bestMultiplier = 0;
            double bestDistance = Double.MAX_VALUE;
            for (double multiplier : SCAN_MULTIPLIERS) {
                double distance = Math.abs(observedLog - Math.log(multiplier));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMultiplier = multiplier;
                }
            }
            if (bestDistance > SCAN_TOLERANCE_LOG) {
                continue;
            }
            if (!persistsAroundBreak(series, i, bestMultiplier)) {
                continue;
            }
            breaks.add(new Corroboration(date, observed, bestMultiplier, bestDistance));
        }
        return breaks;
    }

    /** Median close after the break vs before it must sit near 1/multiplier. */
    private boolean persistsAroundBreak(PriceSeries series, int breakIndex, double multiplier) {
        double before = median(series.closes(),
                Math.max(0, breakIndex - PERSISTENCE_WINDOW_DAYS), breakIndex);
        double after = median(series.closes(),
                breakIndex, Math.min(series.closes().length, breakIndex + PERSISTENCE_WINDOW_DAYS));
        if (before <= 0 || after <= 0) {
            return false;
        }
        double levelShift = Math.log(after / before) - Math.log(1.0 / multiplier);
        return Math.abs(levelShift) <= PERSISTENCE_TOLERANCE_LOG;
    }

    private static double toleranceFor(double multiplier) {
        boolean standard = multiplier >= 2.0 || multiplier <= 0.5;
        return standard ? WIDE_TOLERANCE_LOG : TIGHT_TOLERANCE_LOG;
    }

    private static double median(double[] values, int fromInclusive, int toExclusive) {
        if (fromInclusive >= toExclusive) {
            return -1;
        }
        double[] window = Arrays.copyOfRange(values, fromInclusive, toExclusive);
        Arrays.sort(window);
        int mid = window.length / 2;
        return window.length % 2 == 1 ? window[mid] : (window[mid - 1] + window[mid]) / 2.0;
    }

    private static double[] buildScanMultipliers() {
        double[] forward = {2, 3, 4, 5, 7, 10, 20, 50};
        double[] all = new double[forward.length * 2];
        for (int i = 0; i < forward.length; i++) {
            all[i] = forward[i];
            all[forward.length + i] = 1.0 / forward[i];
        }
        return all;
    }
}

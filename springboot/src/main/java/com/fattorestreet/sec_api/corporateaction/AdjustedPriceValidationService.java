package com.fattorestreet.sec_api.corporateaction;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Compares our stored {@code adjustedClose} series against the yfinance adjusted-close
 * reference served by Django ({@code /portfolio/api/asset-prices/}). Unlike
 * {@link CorporateActionValidationService}, which diffs corporate-action events, this
 * validates the end product users see: the adjusted price series itself.
 *
 * <p>The per-date ratio {@code sec/yf}, normalized at the latest common date, is a step
 * function: each step marks the exact date of a missing, extra, or misdated corporate
 * action, and its magnitude is the implied event size.
 *
 * <p>Diagnostics only, and <strong>read-only</strong>: this service never writes to the database.
 * yfinance reference data is dev-and-diagnostics-only per the data-licensing rule, so nothing it
 * produces may be persisted, returned from an API, or rendered in the UI. Only derived comparison
 * statistics leave this class.
 *
 * <p>Two callers exist. {@link PriceAdjustmentService} folds a summary into its own diagnostics
 * output when explicitly asked to; and the weekly {@link ValidatePricesRunner} one-shot task calls
 * {@link #validateBatch} over the members of one index and emails the result. The nightly
 * hist-load job never calls either path -- it hardcodes {@code validateWithYfinance=false}.
 */
@Service
public class AdjustedPriceValidationService {

    private static final Logger log = LoggerFactory.getLogger(AdjustedPriceValidationService.class);

    /** Level deviations of |ratio - 1| above this count as out-of-tolerance dates. */
    private static final double DEVIATION_THRESHOLD = 0.005;
    /** Day-over-day ratio steps above this are reported as breaks (localized bad events). */
    private static final double BREAK_THRESHOLD = 0.01;
    /** A stored action within this many days of a break is reported as its likely cause. */
    private static final long BREAK_ACTION_WINDOW_DAYS = 7;
    private static final int MAX_BREAKS = 20;
    private static final int MAX_SUMMARY_WORST_TICKERS = 10;
    private static final int MAX_SUMMARY_BREAKS = 20;

    private final DailyPriceRepository dailyPriceRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String djangoPortfolioBaseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public AdjustedPriceValidationService(
            DailyPriceRepository dailyPriceRepository,
            CorporateActionRepository corporateActionRepository,
            ObjectMapper objectMapper,
            @Value("${DJANGO_PORTFOLIO_BASE_URL:http://localhost:8000/portfolio}") String djangoPortfolioBaseUrl) {
        this(dailyPriceRepository, corporateActionRepository, objectMapper, djangoPortfolioBaseUrl,
                HttpClient.newBuilder().build());
    }

    AdjustedPriceValidationService(
            DailyPriceRepository dailyPriceRepository,
            CorporateActionRepository corporateActionRepository,
            ObjectMapper objectMapper,
            String djangoPortfolioBaseUrl,
            HttpClient httpClient) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.djangoPortfolioBaseUrl = trimTrailingSlash(djangoPortfolioBaseUrl);
    }

    public PriceValidationReport validateTicker(String ticker, LocalDate minDateInclusive) {
        TreeMap<LocalDate, Double> secSeries = loadSecAdjustedCloses(ticker, minDateInclusive);
        if (secSeries.isEmpty()) {
            return PriceValidationReport.empty(ticker, "no_adjusted_prices");
        }
        if (secSeries.size() < 2) {
            return PriceValidationReport.empty(ticker, "insufficient_overlap");
        }
        TreeMap<LocalDate, Double> yfSeries = loadYfinanceAdjustedCloses(ticker, minDateInclusive);
        if (yfSeries.isEmpty()) {
            return PriceValidationReport.empty(ticker, "no_reference_data");
        }

        TreeMap<LocalDate, Double> ratios = new TreeMap<>();
        for (Map.Entry<LocalDate, Double> entry : secSeries.entrySet()) {
            Double yf = yfSeries.get(entry.getKey());
            if (yf != null && yf > 0 && entry.getValue() > 0) {
                ratios.put(entry.getKey(), entry.getValue() / yf);
            }
        }
        if (ratios.size() < 2) {
            return PriceValidationReport.empty(ticker, "insufficient_overlap");
        }

        // Normalize at the latest common date so a constant level offset reads as zero
        // deviation; what remains is exactly the effect of event differences.
        double anchor = ratios.lastEntry().getValue();
        double sumAbsDeviation = 0.0;
        double maxAbsDeviation = 0.0;
        int datesOverThreshold = 0;
        for (Map.Entry<LocalDate, Double> entry : ratios.entrySet()) {
            double deviation = Math.abs(entry.getValue() / anchor - 1.0);
            sumAbsDeviation += deviation;
            maxAbsDeviation = Math.max(maxAbsDeviation, deviation);
            if (deviation > DEVIATION_THRESHOLD) {
                datesOverThreshold++;
            }
        }

        List<Map<String, Object>> breaks = detectBreaks(ticker, ratios);
        return new PriceValidationReport(
                ticker,
                "ok",
                ratios.size(),
                ratios.firstKey(),
                ratios.lastKey(),
                round6(sumAbsDeviation / ratios.size()),
                round6(maxAbsDeviation),
                datesOverThreshold,
                breaks);
    }

    /**
     * Validates every ticker in {@code tickers} and aggregates the results.
     *
     * <p>A ticker whose reference fetch throws is counted as skipped and the batch continues: for a
     * weekly report over ~1000 names, a partial report is far more useful than none. Only the
     * caller's own guards (an empty scope, a failed delivery) are fatal.
     */
    public BatchSummary validateBatch(List<String> tickers, LocalDate minDateInclusive) {
        List<PriceValidationReport> reports = new ArrayList<>();
        int unreachable = 0;
        for (String ticker : tickers) {
            try {
                reports.add(validateTicker(ticker, minDateInclusive));
            } catch (RuntimeException e) {
                unreachable++;
                log.warn("[{}] adjusted-price validation failed, skipping ticker: {}", ticker, e.getMessage());
            }
        }
        BatchSummary summary = summarize(reports);
        return unreachable == 0
                ? summary
                : new BatchSummary(
                        summary.tickersChecked(),
                        summary.tickersSkipped() + unreachable,
                        summary.tickersOutOfTolerance(),
                        summary.totalBreaks(),
                        summary.worstTickers(),
                        summary.breaks());
    }

    /** Aggregates per-ticker reports into the counts and capped samples a report body needs. */
    public BatchSummary summarize(List<PriceValidationReport> reports) {
        int tickersValidated = 0;
        int tickersWithoutReference = 0;
        int tickersOverThreshold = 0;
        int totalBreaks = 0;
        List<PriceValidationReport> okReports = new ArrayList<>();
        for (PriceValidationReport report : reports) {
            if (!"ok".equals(report.status())) {
                tickersWithoutReference++;
                continue;
            }
            tickersValidated++;
            okReports.add(report);
            totalBreaks += report.breaks().size();
            if (report.maxAbsDeviation() > DEVIATION_THRESHOLD) {
                tickersOverThreshold++;
            }
        }
        okReports.sort(Comparator.comparingDouble(PriceValidationReport::maxAbsDeviation).reversed());

        List<Map<String, Object>> worstTickers = new ArrayList<>();
        List<Map<String, Object>> sampleBreaks = new ArrayList<>();
        for (PriceValidationReport report : okReports) {
            if (worstTickers.size() < MAX_SUMMARY_WORST_TICKERS) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ticker", report.ticker());
                row.put("maxAbsDeviation", report.maxAbsDeviation());
                row.put("datesOverThreshold", report.datesOverThreshold());
                row.put("breaks", report.breaks().size());
                worstTickers.add(row);
            }
            for (Map<String, Object> breakRow : report.breaks()) {
                if (sampleBreaks.size() >= MAX_SUMMARY_BREAKS) {
                    break;
                }
                sampleBreaks.add(breakRow);
            }
        }
        return new BatchSummary(
                tickersValidated,
                tickersWithoutReference,
                tickersOverThreshold,
                totalBreaks,
                List.copyOf(worstTickers),
                List.copyOf(sampleBreaks));
    }

    public Map<String, Object> summarizeBatch(List<PriceValidationReport> reports) {
        return summarize(reports).toMap();
    }

    private List<Map<String, Object>> detectBreaks(String ticker, TreeMap<LocalDate, Double> ratios) {
        List<CorporateAction> actions = corporateActionRepository.findByTicker(ticker);
        List<Map<String, Object>> breaks = new ArrayList<>();
        Map.Entry<LocalDate, Double> previous = null;
        for (Map.Entry<LocalDate, Double> entry : ratios.entrySet()) {
            if (previous != null && breaks.size() < MAX_BREAKS) {
                double step = entry.getValue() / previous.getValue() - 1.0;
                if (Math.abs(step) > BREAK_THRESHOLD) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ticker", ticker);
                    row.put("date", entry.getKey().toString());
                    row.put("step", round6(step));
                    row.put("nearestAction", describeNearestAction(actions, entry.getKey()));
                    breaks.add(row);
                }
            }
            previous = entry;
        }
        return breaks;
    }

    private Map<String, Object> describeNearestAction(List<CorporateAction> actions, LocalDate breakDate) {
        CorporateAction nearest = null;
        long nearestGap = Long.MAX_VALUE;
        for (CorporateAction action : actions) {
            if (action.getEffectiveDate() == null) {
                continue;
            }
            long gap = Math.abs(ChronoUnit.DAYS.between(breakDate, action.getEffectiveDate()));
            if (gap <= BREAK_ACTION_WINDOW_DAYS && gap < nearestGap) {
                nearestGap = gap;
                nearest = action;
            }
        }
        if (nearest == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("actionType", nearest.getActionType().name());
        out.put("effectiveDate", nearest.getEffectiveDate().toString());
        out.put("ratio", nearest.getRatio());
        out.put("daysFromBreak", nearestGap);
        return out;
    }

    private TreeMap<LocalDate, Double> loadSecAdjustedCloses(String ticker, LocalDate minDateInclusive) {
        TreeMap<LocalDate, Double> out = new TreeMap<>();
        for (DailyPrice price : dailyPriceRepository.findByTickerOrderByTradeDateAsc(ticker)) {
            if (price.getTradeDate() == null || price.getAdjustedClose() == null) {
                continue;
            }
            if (price.getTradeDate().isBefore(minDateInclusive)) {
                continue;
            }
            out.put(price.getTradeDate(), price.getAdjustedClose());
        }
        return out;
    }

    private TreeMap<LocalDate, Double> loadYfinanceAdjustedCloses(String ticker, LocalDate minDateInclusive) {
        TreeMap<LocalDate, Double> out = new TreeMap<>();
        try {
            String encodedTicker = java.net.URLEncoder.encode(ticker, java.nio.charset.StandardCharsets.UTF_8);
            JsonNode rows = fetchJsonArray(String.format(
                    Locale.US,
                    "%s/api/asset-prices/?ticker=%s",
                    djangoPortfolioBaseUrl,
                    encodedTicker));
            rows.forEach(row -> {
                LocalDate date = parseIsoDate(row.path("date").asString(null));
                double value = row.path("value").asDouble(0.0);
                if (date != null && !date.isBefore(minDateInclusive) && value > 0) {
                    out.put(date, value);
                }
            });
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[{}] django yfinance price reference fetch failed: {}", ticker, e.getMessage());
        }
        return out;
    }

    private JsonNode fetchJsonArray(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.isArray()) {
            throw new IOException("Expected JSON array for " + url);
        }
        return root;
    }

    private LocalDate parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8000/portfolio";
        }
        String out = url.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    /**
     * Aggregate of one batch. {@code worstTickers} and {@code breaks} are already capped at
     * {@link #MAX_SUMMARY_WORST_TICKERS} and {@link #MAX_SUMMARY_BREAKS}; {@code totalBreaks} is the
     * uncapped count, so a renderer can say how many rows it is not showing.
     */
    public record BatchSummary(
            int tickersChecked,
            int tickersSkipped,
            int tickersOutOfTolerance,
            int totalBreaks,
            List<Map<String, Object>> worstTickers,
            List<Map<String, Object>> breaks) {

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tickersValidated", tickersChecked);
            out.put("tickersWithoutReference", tickersSkipped);
            out.put("tickersOverThreshold", tickersOutOfTolerance);
            out.put("totalBreaks", totalBreaks);
            out.put("worstTickers", worstTickers);
            out.put("sampleBreaks", breaks);
            return out;
        }
    }

    public record PriceValidationReport(
            String ticker,
            String status,
            int commonDates,
            LocalDate firstCommonDate,
            LocalDate lastCommonDate,
            double meanAbsDeviation,
            double maxAbsDeviation,
            int datesOverThreshold,
            List<Map<String, Object>> breaks) {

        public static PriceValidationReport empty(String ticker, String status) {
            return new PriceValidationReport(ticker, status, 0, null, null, 0.0, 0.0, 0, List.of());
        }

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ticker", ticker);
            out.put("status", status);
            out.put("commonDates", commonDates);
            out.put("firstCommonDate", firstCommonDate != null ? firstCommonDate.toString() : null);
            out.put("lastCommonDate", lastCommonDate != null ? lastCommonDate.toString() : null);
            out.put("meanAbsDeviation", meanAbsDeviation);
            out.put("maxAbsDeviation", maxAbsDeviation);
            out.put("datesOverThreshold", datesOverThreshold);
            out.put("breaks", breaks);
            return out;
        }
    }
}

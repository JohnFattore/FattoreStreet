package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.filing.FilingSummaryService;
import com.fattorestreet.sec_api.fundamentals.EdgarService;
import com.fattorestreet.sec_api.listing.AssetService;
import com.fattorestreet.sec_api.listing.EtfIdentityService;
import com.fattorestreet.sec_api.listing.ListingService;
import com.fattorestreet.sec_api.marketdata.IexHistService;
import com.fattorestreet.sec_api.corporateaction.PriceAdjustmentService;
import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.index.FattoreIndexCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Validated
@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final WebService webService;
    private final AssetService assetService;
    private final ListingService listingService;
    private final EdgarService edgarService;
    private final PriceAdjustmentService priceAdjustmentService;
    private final IexHistService iexHistService;
    private final EtfIdentityService etfIdentityService;
    private final FilingSummaryService filingSummaryService;
    private final com.fattorestreet.sec_api.repository.AssetRepository assetRepository;
    private final com.fattorestreet.sec_api.index.IndexMetricsRefreshService indexMetricsRefreshService;
    private final com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore50IndexRebuildService;
    private final com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore100IndexRebuildService;
    private final com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore1000IndexRebuildService;
    private final Map<String, com.fattorestreet.sec_api.index.FattoreIndexRebuildService> capRankedRebuildByCode;
    private final List<com.fattorestreet.sec_api.index.FattoreIndexRebuildService> capRankedRebuildAllOrdered;
    private final ObjectMapper mapper = new ObjectMapper();

    public AdminController(
            WebService webService,
            AssetService assetService,
            ListingService listingService,
            EdgarService edgarService,
            PriceAdjustmentService priceAdjustmentService,
            IexHistService iexHistService,
            EtfIdentityService etfIdentityService,
            FilingSummaryService filingSummaryService,
            com.fattorestreet.sec_api.repository.AssetRepository assetRepository,
            com.fattorestreet.sec_api.index.IndexMetricsRefreshService indexMetricsRefreshService,
            @Qualifier("fattore50IndexRebuildService")
            com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore50IndexRebuildService,
            @Qualifier("fattore100IndexRebuildService")
            com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore100IndexRebuildService,
            @Qualifier("fattore1000IndexRebuildService")
            com.fattorestreet.sec_api.index.FattoreIndexRebuildService fattore1000IndexRebuildService
    ) {
        this.webService = webService;
        this.assetService = assetService;
        this.listingService = listingService;
        this.edgarService = edgarService;
        this.priceAdjustmentService = priceAdjustmentService;
        this.iexHistService = iexHistService;
        this.etfIdentityService = etfIdentityService;
        this.filingSummaryService = filingSummaryService;
        this.assetRepository = assetRepository;
        this.indexMetricsRefreshService = indexMetricsRefreshService;
        this.fattore50IndexRebuildService = fattore50IndexRebuildService;
        this.fattore100IndexRebuildService = fattore100IndexRebuildService;
        this.fattore1000IndexRebuildService = fattore1000IndexRebuildService;
        this.capRankedRebuildByCode = Map.of(
                FattoreIndexCodes.FAT50.toUpperCase(Locale.ROOT), fattore50IndexRebuildService,
                FattoreIndexCodes.FAT100.toUpperCase(Locale.ROOT), fattore100IndexRebuildService,
                FattoreIndexCodes.FAT1000.toUpperCase(Locale.ROOT), fattore1000IndexRebuildService);
        // Lexicographic by market index code: FAT100, FAT1000, FAT50
        this.capRankedRebuildAllOrdered = List.of(
                fattore100IndexRebuildService, fattore1000IndexRebuildService, fattore50IndexRebuildService);
    }

    @GetMapping("/admin/asset-load")
    public ResponseEntity<?> assetLoad(
            @RequestParam(defaultValue = "false") boolean overwriteExisting
    ) {
        long startTime = System.currentTimeMillis();
        try {
            Map<Integer, Map<String, String>> secTickers = webService.fetchSecTickers();
            Map<String, SecTickerRow> tickerRows = new LinkedHashMap<>();
            for (Map<String, String> secTicker : secTickers.values()) {
                String ticker = secTicker.get("ticker");
                Long cik = parseCik(secTicker.get("cik_str"));
                if (ticker == null || ticker.isBlank() || cik == null) {
                    continue;
                }
                tickerRows.put(ticker, new SecTickerRow(ticker, cik, secTicker.get("title"), false));
            }

            List<SecTickerRow> secFundRows = parseSecMutualFundTickers(webService.fetchSecMutualFundTickers());
            for (SecTickerRow secFundRow : secFundRows) {
                SecTickerRow existing = tickerRows.get(secFundRow.ticker());
                if (existing == null) {
                    tickerRows.put(secFundRow.ticker(), secFundRow);
                    continue;
                }
                String mergedTitle = (existing.title() != null && !existing.title().isBlank())
                        ? existing.title()
                        : secFundRow.title();
                tickerRows.put(
                        secFundRow.ticker(),
                        new SecTickerRow(
                                secFundRow.ticker(),
                                secFundRow.cik(),
                                mergedTitle,
                                existing.isFund() || secFundRow.isFund()
                        )
                );
            }

            int equityCount = 0;
            int fundCount = 0;
            for (SecTickerRow secTickerRow : tickerRows.values()) {
                Asset asset = new Asset();
                String ticker = secTickerRow.ticker();
                boolean isFund = secTickerRow.isFund();
                asset.setCik(secTickerRow.cik());
                asset.setIsFund(isFund);
                asset = assetService.createOrUpdateAsset(asset);
                Listing listing = new Listing();
                listing.setTicker(ticker);
                listing.setTitle(
                        secTickerRow.title() != null && !secTickerRow.title().isBlank()
                                ? secTickerRow.title()
                                : ticker
                );
                listing.setAsset(asset);
                listingService.createOrUpdateListing(listing);
                if (isFund) {
                    fundCount++;
                } else {
                    equityCount++;
                }
            }
            Map<String, Object> result = etfIdentityService.enrichFundListingIdentities(overwriteExisting);
            String duration = formatDuration(System.currentTimeMillis() - startTime);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("equitiesLoaded", equityCount);
            response.put("fundsLoaded", fundCount);
            response.put("tickersLoaded", equityCount + fundCount);
            response.put("etfIdentityOverwriteExisting", overwriteExisting);
            response.put("etfIdentityEnrichment", result);
            response.put("duration", duration);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during admin asset load and ETF enrichment", e);
            return ResponseEntity.internalServerError().body("Error during admin asset load: " + e.getMessage());
        }
    }

    @GetMapping("/admin/test")
    public ResponseEntity<?> test() throws Exception {
        String json = webService.fetchFinancials(320193L);
        JsonNode root = mapper.readTree(json);
        JsonNode facts = root.get("facts")
                .get("us-gaap")
                .get("RevenueFromContractWithCustomerExcludingAssessedTax")
                .get("units")
                .get("USD");
        return ResponseEntity.ok(facts.toString());
    }

    @GetMapping("/admin/load-hist")
    public ResponseEntity<?> loadHist(
            @RequestParam(defaultValue = "252") int days
    ) {
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result = iexHistService.loadHistData(days);
            String duration = formatDuration(System.currentTimeMillis() - startTime);
            return ResponseEntity.ok(Map.of(
                    "message", "IEX HIST load complete in " + duration,
                    "processed", result.get("processed"),
                    "skipped", result.get("skipped"),
                    "notAvailable", result.get("notAvailable"),
                    "errors", result.get("errors")
            ));
        } catch (Exception e) {
            log.error("Error loading IEX HIST data", e);
            return ResponseEntity.internalServerError().body("Error loading IEX HIST data: " + e.getMessage());
        }
    }

    @GetMapping("/admin/adjust-prices")
    public ResponseEntity<?> adjustPrices(
            @RequestParam(required = false) String ticker,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean etfOnly,
            @RequestParam(defaultValue = "false") boolean equityOnly,
            @RequestParam(defaultValue = "false") boolean validateWithYfinance
    ) {
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result;
            if (ticker != null && !ticker.isBlank()) {
                result = priceAdjustmentService.adjustTicker(
                        ticker,
                        force,
                        etfOnly,
                        equityOnly,
                        validateWithYfinance
                );
            } else {
                result = priceAdjustmentService.adjustAllTickers(
                        force,
                        etfOnly,
                        equityOnly,
                        validateWithYfinance
                );
            }
            String duration = formatDuration(System.currentTimeMillis() - startTime);
            Map<String, Object> response = new LinkedHashMap<>(result);
            response.put("duration", duration);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error adjusting prices", e);
            return ResponseEntity.internalServerError().body("Error adjusting prices: " + e.getMessage());
        }
    }

    @GetMapping("/admin/summarize-filings")
    public ResponseEntity<?> summarizeFilings(
            @RequestParam(required = false) String ticker
    ) {
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result;
            if (ticker != null && !ticker.isBlank()) {
                Asset asset = assetRepository.findByListings_Ticker(ticker.toUpperCase());
                if (asset == null) {
                    return ResponseEntity.notFound().build();
                }
                result = filingSummaryService.summarizeTicker(ticker.toUpperCase(), asset);
            } else {
                result = filingSummaryService.summarizeAll();
            }
            String duration = formatDuration(System.currentTimeMillis() - startTime);
            Map<String, Object> response = new LinkedHashMap<>(result);
            response.put("duration", duration);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error summarizing filings", e);
            return ResponseEntity.internalServerError().body("Error summarizing filings: " + e.getMessage());
        }
    }

    @GetMapping("/admin/sync-frames")
    public ResponseEntity<?> syncFrames() {
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> report = edgarService.syncFramesFull();
            String duration = formatDuration(System.currentTimeMillis() - startTime);
            String message = "Synced " + report.get("equitiesProcessed") + " equities (all frames since 2009)"
                    + ". Skipped " + report.get("fundsSkipped") + " ETFs/funds. Completed in " + duration + ".";
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Error syncing frames", e);
            return ResponseEntity.internalServerError().body("Error syncing frames: " + e.getMessage());
        }
    }

    @PostMapping("/admin/indexes/refresh-stocks")
    public ResponseEntity<?> refreshIndexStocks(
            @RequestParam(required = false) Integer year) {
        try {
            int resolvedYear = (year != null) ? year : java.time.Year.now().getValue();
            long startTime = System.currentTimeMillis();
            com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult r =
                    indexMetricsRefreshService.refreshAllListings(resolvedYear);
            String duration = formatDuration(System.currentTimeMillis() - startTime);
            Map<String, Long> skipReasonCounts = r.skippedTickers().stream()
                    .map(s -> {
                        int i = s.indexOf(':');
                        return i >= 0 ? s.substring(i + 1) : "unknown";
                    })
                    .collect(Collectors.groupingBy(x -> x, Collectors.counting()));
            return ResponseEntity.ok(Map.of(
                    "message", "Index metrics refresh complete in " + duration,
                    "year", resolvedYear,
                    "processed", r.processed(),
                    "skipped", r.skipped(),
                    "skippedTickers", r.skippedTickers(),
                    "skipReasonCounts", skipReasonCounts
            ));
        } catch (Exception e) {
            log.error("Error refreshing index metrics", e);
            return ResponseEntity.internalServerError().body("Error refreshing index metrics: " + e.getMessage());
        }
    }

    /**
     * Rebuild cap-ranked indexes ({@code FAT50}, {@code FAT100}, {@code FAT1000}). Optional query param {@code code}
     * selects one index (case-insensitive); omit or leave blank to rebuild all configured indexes.
     */
    @PostMapping("/admin/indexes/rebuild")
    public ResponseEntity<?> rebuildCapRankedIndexes(
            @RequestParam(defaultValue = "false") boolean refreshMetrics,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String code) {
        List<com.fattorestreet.sec_api.index.FattoreIndexRebuildService> services;
        if (code == null || code.isBlank()) {
            services = capRankedRebuildAllOrdered;
        } else {
            String normalized = code.trim().toUpperCase(Locale.ROOT);
            com.fattorestreet.sec_api.index.FattoreIndexRebuildService one = capRankedRebuildByCode.get(normalized);
            if (one == null) {
                return ResponseEntity.badRequest()
                        .body("Unknown index code: " + code.trim() + ". Supported: "
                                + String.join(", ", capRankedRebuildByCode.keySet()));
            }
            services = List.of(one);
        }
        try {
            int resolvedYear = (year != null) ? year : java.time.Year.now().getValue();
            long startTime = System.currentTimeMillis();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("year", resolvedYear);
            if (refreshMetrics) {
                com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult rr =
                        indexMetricsRefreshService.refreshAllListings(resolvedYear);
                payload.put("refresh", Map.of(
                        "processed", rr.processed(),
                        "skipped", rr.skipped(),
                        "skippedTickers", rr.skippedTickers()));
            }
            List<Map<String, Object>> rebuilds = new ArrayList<>();
            for (com.fattorestreet.sec_api.index.FattoreIndexRebuildService service : services) {
                rebuilds.add(toRebuildPayload(service.rebuild(resolvedYear)));
            }
            payload.put("rebuilds", rebuilds);
            payload.put("duration", formatDuration(System.currentTimeMillis() - startTime));
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            log.error("Error rebuilding cap-ranked index(es)", e);
            return ResponseEntity.internalServerError()
                    .body("Error rebuilding cap-ranked index(es): " + e.getMessage());
        }
    }

    @PostMapping("/admin/indexes/rebuild-fattore-50")
    public ResponseEntity<?> rebuildFattore50(
            @RequestParam(defaultValue = "false") boolean refreshMetrics,
            @RequestParam(required = false) Integer year) {
        return rebuildCapRankedIndexLegacy(refreshMetrics, year, fattore50IndexRebuildService, "Fattore 50");
    }

    @PostMapping("/admin/indexes/rebuild-fattore-100")
    public ResponseEntity<?> rebuildFattore100(
            @RequestParam(defaultValue = "false") boolean refreshMetrics,
            @RequestParam(required = false) Integer year) {
        return rebuildCapRankedIndexLegacy(refreshMetrics, year, fattore100IndexRebuildService, "Fattore 100");
    }

    @PostMapping("/admin/indexes/rebuild-fattore-1000")
    public ResponseEntity<?> rebuildFattore1000(
            @RequestParam(defaultValue = "false") boolean refreshMetrics,
            @RequestParam(required = false) Integer year) {
        return rebuildCapRankedIndexLegacy(refreshMetrics, year, fattore1000IndexRebuildService, "Fattore 1000");
    }

    private static Map<String, Object> toRebuildPayload(
            com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult rb) {
        return Map.of(
                "indexCode", rb.indexCode(),
                "year", rb.year(),
                "memberCount", rb.memberCount(),
                "partial", rb.partial(),
                "totalFreeFloatMarketCap", rb.totalFreeFloatMarketCap(),
                "tickers", rb.tickers());
    }

    /** Legacy response shape with singular {@code rebuild} for backward compatibility. */
    private ResponseEntity<?> rebuildCapRankedIndexLegacy(
            boolean refreshMetrics,
            Integer year,
            com.fattorestreet.sec_api.index.FattoreIndexRebuildService service,
            String indexLabel) {
        try {
            int resolvedYear = (year != null) ? year : java.time.Year.now().getValue();
            long startTime = System.currentTimeMillis();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("year", resolvedYear);
            if (refreshMetrics) {
                com.fattorestreet.sec_api.index.IndexMetricsRefreshService.RefreshResult rr =
                        indexMetricsRefreshService.refreshAllListings(resolvedYear);
                payload.put("refresh", Map.of(
                        "processed", rr.processed(),
                        "skipped", rr.skipped(),
                        "skippedTickers", rr.skippedTickers()));
            }
            com.fattorestreet.sec_api.index.FattoreIndexRebuildService.RebuildResult rb =
                    service.rebuild(resolvedYear);
            payload.put("rebuild", toRebuildPayload(rb));
            payload.put("duration", formatDuration(System.currentTimeMillis() - startTime));
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            log.error("Error rebuilding {} index", indexLabel, e);
            return ResponseEntity.internalServerError()
                    .body("Error rebuilding " + indexLabel + " index: " + e.getMessage());
        }
    }

    private String formatDuration(long elapsedMs) {
        long minutes = elapsedMs / 60000;
        long seconds = (elapsedMs % 60000) / 1000;
        return minutes > 0
                ? minutes + "m " + seconds + "s"
                : seconds + "." + (elapsedMs % 1000) / 100 + "s";
    }

    private Long parseCik(String cikValue) {
        if (cikValue == null || cikValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cikValue.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<SecTickerRow> parseSecMutualFundTickers(String rawJson) {
        List<SecTickerRow> rows = new ArrayList<>();
        if (rawJson == null || rawJson.isBlank()) {
            return rows;
        }
        try {
            JsonNode root = mapper.readTree(rawJson);
            if (root == null) {
                return rows;
            }
            for (JsonNode row : extractSecMutualFundRows(root)) {
                String ticker = firstText(row, "ticker", "symbol", "class_ticker", "classTicker");
                Long cik = parseCik(firstText(row, "cik", "cik_str"));
                if (ticker == null || ticker.isBlank() || cik == null) {
                    continue;
                }
                String title = firstText(row, "title", "series_title", "class_title");
                if (title == null || title.isBlank()) {
                    String seriesName = firstText(row, "seriesName", "series_name");
                    String className = firstText(row, "className", "class_name");
                    if (seriesName != null && className != null) {
                        title = seriesName + " - " + className;
                    } else if (seriesName != null) {
                        title = seriesName;
                    } else if (className != null) {
                        title = className;
                    }
                }
                rows.add(new SecTickerRow(ticker, cik, title, true));
            }
        } catch (Exception e) {
            log.warn("Unable to parse SEC mutual fund ticker index: {}", e.getMessage());
        }
        return rows;
    }

    private List<JsonNode> extractSecMutualFundRows(JsonNode root) {
        List<JsonNode> rows = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(rows::add);
            return rows;
        }
        if (!root.isObject()) {
            return rows;
        }

        JsonNode fieldsNode = root.get("fields");
        JsonNode dataNode = root.get("data");
        if (fieldsNode != null && fieldsNode.isArray() && dataNode != null && dataNode.isArray()) {
            List<String> fields = new ArrayList<>();
            for (JsonNode fieldNode : fieldsNode) {
                String field = fieldNode.asText(null);
                fields.add(field == null ? "" : field);
            }
            for (JsonNode dataRow : dataNode) {
                if (dataRow.isObject()) {
                    rows.add(dataRow);
                    continue;
                }
                if (!dataRow.isArray()) {
                    continue;
                }
                com.fasterxml.jackson.databind.node.ObjectNode mappedRow = mapper.createObjectNode();
                int max = Math.min(fields.size(), dataRow.size());
                for (int i = 0; i < max; i++) {
                    String fieldName = fields.get(i);
                    if (fieldName == null || fieldName.isBlank()) {
                        continue;
                    }
                    mappedRow.set(fieldName, dataRow.get(i));
                }
                rows.add(mappedRow);
            }
            return rows;
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = root.fields();
        while (iterator.hasNext()) {
            rows.add(iterator.next().getValue());
        }
        return rows;
    }

    private String firstText(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = value.asText();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private record SecTickerRow(String ticker, Long cik, String title, boolean isFund) {
    }
}

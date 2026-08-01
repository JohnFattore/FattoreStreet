package com.fattorestreet.sec_api.listing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.Listing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Loads the SEC ticker universe -- the company_tickers index plus the mutual-fund/ETF ticker
 * index -- and upserts it into {@code Asset} and {@code Listing}.
 *
 * <p>The two SEC indexes overlap: a ticker present in both keeps the equity title (which is the
 * better one) but is marked as a fund, because the fund index is the only source that identifies
 * it as such.
 *
 * <p>ETF <em>identity</em> enrichment (series/class metadata) is a separate concern and lives in
 * {@link EtfIdentityService}; callers run it after this load.
 */
@Service
public class SecTickerLoadService {

    private static final Logger log = LoggerFactory.getLogger(SecTickerLoadService.class);

    private final WebService webService;
    private final AssetService assetService;
    private final ListingService listingService;
    private final ObjectMapper mapper;

    public SecTickerLoadService(
            WebService webService,
            AssetService assetService,
            ListingService listingService,
            ObjectMapper mapper) {
        this.webService = webService;
        this.assetService = assetService;
        this.listingService = listingService;
        this.mapper = mapper;
    }

    /**
     * Fetches both SEC ticker indexes and upserts every resolvable row.
     *
     * <p>Rows without a ticker or a parseable CIK are skipped -- they cannot key an {@code Asset}.
     * Propagates whatever the SEC fetch throws; callers decide whether that is fatal.
     */
    public SecTickerLoadResult load() {
        Map<String, SecTickerRow> tickerRows = new LinkedHashMap<>();
        Map<Integer, Map<String, String>> secTickers = webService.fetchSecTickers();
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
        return new SecTickerLoadResult(equityCount, fundCount);
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
                String field = fieldNode.asString(null);
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
                ObjectNode mappedRow = mapper.createObjectNode();
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

        Iterator<Map.Entry<String, JsonNode>> iterator = root.properties().iterator();
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
            String text = value.asString();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    /** One SEC ticker row, merged across the equity and fund indexes. */
    private record SecTickerRow(String ticker, Long cik, String title, boolean isFund) {
    }

    /** Counts from one load pass. */
    public record SecTickerLoadResult(int equitiesLoaded, int fundsLoaded) {

        public int tickersLoaded() {
            return equitiesLoaded + fundsLoaded;
        }
    }
}

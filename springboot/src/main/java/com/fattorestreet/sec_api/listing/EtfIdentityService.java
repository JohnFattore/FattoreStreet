package com.fattorestreet.sec_api.listing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.ListingRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EtfIdentityService {

    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_UNRESOLVED = "UNRESOLVED";
    private static final String STATUS_SKIPPED = "SKIPPED";

    private final ListingRepository listingRepository;
    private final WebService webService;
    private final ObjectMapper objectMapper;

    public EtfIdentityService(ListingRepository listingRepository,
                              WebService webService,
                              ObjectMapper objectMapper) {
        this.listingRepository = listingRepository;
        this.webService = webService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> enrichFundListingIdentities(boolean overwriteExisting) {
        List<Listing> fundListings = listingRepository.findByAsset_IsFundTrue();
        String rawJson = webService.fetchSecMutualFundTickers();
        Map<String, EtfIdentity> identityByTicker = parseIdentityMap(rawJson);

        int updated = 0;
        int resolved = 0;
        int unresolved = 0;
        int skipped = 0;
        List<String> unresolvedTickers = new ArrayList<>();

        for (Listing listing : fundListings) {
            String ticker = listing.getTicker();
            if (ticker == null || ticker.isBlank()) {
                skipped++;
                continue;
            }

            boolean alreadyResolved = STATUS_RESOLVED.equalsIgnoreCase(listing.getSecIdentityStatus())
                    && listing.getSecSeriesId() != null
                    && listing.getSecClassContractId() != null;
            if (alreadyResolved && !overwriteExisting) {
                listing.setSecIdentityStatus(STATUS_SKIPPED);
                listingRepository.save(listing);
                skipped++;
                continue;
            }

            EtfIdentity identity = identityByTicker.get(ticker.toUpperCase(Locale.US));
            if (identity == null) {
                listing.setSecIdentityStatus(STATUS_UNRESOLVED);
                listingRepository.save(listing);
                unresolved++;
                if (unresolvedTickers.size() < 100) {
                    unresolvedTickers.add(ticker);
                }
                continue;
            }

            listing.setSecSeriesId(identity.seriesId());
            listing.setSecClassContractId(identity.classContractId());
            listing.setSecClassTicker(identity.classTicker());
            listing.setSecSeriesName(identity.seriesName());
            listing.setSecClassName(identity.className());
            listing.setSecIdentityStatus(STATUS_RESOLVED);
            listingRepository.save(listing);
            updated++;
            resolved++;
        }

        return Map.of(
                "fundListingsTotal", fundListings.size(),
                "secMfTickerRows", identityByTicker.size(),
                "updated", updated,
                "resolved", resolved,
                "unresolved", unresolved,
                "skipped", skipped,
                "unresolvedTickersSample", unresolvedTickers
        );
    }

    private Map<String, EtfIdentity> parseIdentityMap(String rawJson) {
        Map<String, EtfIdentity> identityByTicker = new HashMap<>();
        if (rawJson == null || rawJson.isBlank()) {
            return identityByTicker;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            for (JsonNode row : extractSecMutualFundRows(root)) {
                String ticker = firstText(row, "ticker", "symbol", "class_ticker", "classTicker");
                if (ticker == null || ticker.isBlank()) {
                    continue;
                }
                String normalizedTicker = ticker.toUpperCase(Locale.US);
                EtfIdentity identity = new EtfIdentity(
                        firstText(row, "seriesId", "series_id", "series"),
                        firstText(row, "classId", "class_id", "classContractId", "class_contract_id"),
                        firstText(row, "class_ticker", "classTicker", "ticker", "symbol"),
                        firstText(row, "seriesName", "series_name", "series_title"),
                        firstText(row, "className", "class_name", "class_title")
                );
                identityByTicker.put(normalizedTicker, identity);
            }
        } catch (Exception ignored) {
            // Return empty map and let unresolved status handle downstream behavior safely.
        }
        return identityByTicker;
    }

    private List<JsonNode> extractSecMutualFundRows(JsonNode root) {
        List<JsonNode> rows = new ArrayList<>();
        if (root == null) {
            return rows;
        }
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
                tools.jackson.databind.node.ObjectNode mappedRow = objectMapper.createObjectNode();
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

        root.properties().forEach(entry -> rows.add(entry.getValue()));
        return rows;
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
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

    private record EtfIdentity(
            String seriesId,
            String classContractId,
            String classTicker,
            String seriesName,
            String className) {
    }
}

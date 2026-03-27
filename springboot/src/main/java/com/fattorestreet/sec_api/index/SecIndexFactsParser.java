package com.fattorestreet.sec_api.index;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Extracts index-relevant figures from SEC companyfacts JSON (IEX-free; SEC EDGAR API).
 */
public final class SecIndexFactsParser {

    private SecIndexFactsParser() {
    }

    public record SecShareFacts(
            Long sharesOutstanding,
            Long publicFloatShares,
            String countryCodeOrName
    ) {
        static SecShareFacts empty() {
            return new SecShareFacts(null, null, null);
        }
    }

    /**
     * Latest DEI common shares outstanding and public float (if reported), plus a coarse country hint.
     */
    public static SecShareFacts parseShareFacts(JsonNode root) {
        if (root == null) {
            return SecShareFacts.empty();
        }
        Long shares = latestDeiNumericUnitsVal(root, "EntityCommonStockSharesOutstanding");
        Long floatShares = latestDeiNumericUnitsVal(root, "EntityPublicFloat");
        String country = latestDeiCountry(root);
        return new SecShareFacts(shares, floatShares, country);
    }

    private static Long latestDeiNumericUnitsVal(JsonNode root, String tag) {
        JsonNode units = navigatePath(root, "facts", "dei", tag, "units");
        if (units == null || !units.isObject()) {
            return null;
        }
        JsonNode sharesArr = units.get("shares");
        if (sharesArr == null || !sharesArr.isArray()) {
            return null;
        }
        return latestLongByEndDate(sharesArr);
    }

    private static Long latestLongByEndDate(JsonNode arr) {
        JsonNode best = null;
        LocalDate bestDate = null;
        for (JsonNode n : arr) {
            if (!n.has("val") || !n.has("end")) {
                continue;
            }
            LocalDate d = LocalDate.parse(n.get("end").asText());
            if (bestDate == null || d.isAfter(bestDate)) {
                bestDate = d;
                best = n;
            }
        }
        if (best == null) {
            return null;
        }
        return best.get("val").asLong();
    }

    private static String latestDeiCountry(JsonNode root) {
        for (String tag : new String[] {
                "EntityIncorporationStateCountryCode",
                "EntityAddressCountry",
                "EntityRegistrantCountry"
        }) {
            JsonNode units = navigatePath(root, "facts", "dei", tag, "units");
            if (units == null || !units.isObject()) {
                continue;
            }
            JsonNode arr = firstArrayUnit(units);
            if (arr == null) {
                continue;
            }
            JsonNode best = null;
            LocalDate bestDate = null;
            for (JsonNode n : arr) {
                if (!n.has("val") || !n.has("end")) {
                    continue;
                }
                LocalDate d = LocalDate.parse(n.get("end").asText());
                if (bestDate == null || d.isAfter(bestDate)) {
                    bestDate = d;
                    best = n;
                }
            }
            if (best != null) {
                return best.get("val").asText();
            }
        }
        return null;
    }

    private static JsonNode firstArrayUnit(JsonNode units) {
        JsonNode shares = units.get("shares");
        if (shares != null && shares.isArray()) {
            return shares;
        }
        JsonNode pure = units.get("pure");
        if (pure != null && pure.isArray()) {
            return pure;
        }
        var fields = units.fields();
        while (fields.hasNext()) {
            var e = fields.next();
            if (e.getValue().isArray()) {
                return e.getValue();
            }
        }
        return null;
    }

    private static JsonNode navigatePath(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null || !cur.has(p)) {
                return null;
            }
            cur = cur.get(p);
        }
        return cur;
    }

    public static BigDecimal computeFreeFloat(Long sharesOutstanding, Long publicFloatShares) {
        if (sharesOutstanding == null || sharesOutstanding <= 0) {
            return null;
        }
        if (publicFloatShares == null || publicFloatShares <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal ff = BigDecimal.valueOf(publicFloatShares)
                .divide(BigDecimal.valueOf(sharesOutstanding), 10, RoundingMode.HALF_UP);
        if (ff.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return ff;
    }

    public static String normalizeCountry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "United States";
        }
        String u = raw.trim();
        if ("US".equalsIgnoreCase(u) || "USA".equalsIgnoreCase(u) || "United States".equalsIgnoreCase(u)) {
            return "United States";
        }
        return u;
    }
}

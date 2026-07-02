package com.fattorestreet.sec_api.index;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Extracts index-relevant figures from SEC companyfacts JSON (IEX-free; SEC EDGAR API).
 */
public final class SecIndexFactsParser {

    private SecIndexFactsParser() {
    }

    /**
     * US state and territory codes used in SEC submissions (2-letter).
     */
    private static final Set<String> US_STATE_AND_TERRITORY_CODES = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA",
            "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT",
            "VA", "WA", "WV", "WI", "WY", "DC",
            "AS", "GU", "MP", "PR", "VI", "UM"
    );

    /**
     * Common ISO 3166-1 alpha-2 codes when SEC uses a 2-letter country code in stateOrCountry (non-US).
     */
    private static final Map<String, String> ISO_ALPHA2_COUNTRY = Map.ofEntries(
            Map.entry("GB", "United Kingdom"),
            Map.entry("UK", "United Kingdom"),
            Map.entry("CA", "Canada"),
            Map.entry("JP", "Japan"),
            Map.entry("CN", "China"),
            Map.entry("DE", "Germany"),
            Map.entry("FR", "France"),
            Map.entry("CH", "Switzerland"),
            Map.entry("NL", "Netherlands"),
            Map.entry("IE", "Ireland"),
            Map.entry("IL", "Israel"),
            Map.entry("BR", "Brazil"),
            Map.entry("AU", "Australia"),
            Map.entry("IN", "India"),
            Map.entry("KR", "South Korea"),
            Map.entry("TW", "Taiwan"),
            Map.entry("SG", "Singapore"),
            Map.entry("HK", "Hong Kong"),
            Map.entry("MX", "Mexico"),
            Map.entry("ZA", "South Africa"),
            Map.entry("SE", "Sweden"),
            Map.entry("NO", "Norway"),
            Map.entry("DK", "Denmark"),
            Map.entry("FI", "Finland"),
            Map.entry("IT", "Italy"),
            Map.entry("ES", "Spain"),
            Map.entry("BE", "Belgium"),
            Map.entry("AT", "Austria"),
            Map.entry("PL", "Poland"),
            Map.entry("RU", "Russia"),
            Map.entry("NZ", "New Zealand")
    );

    public record SecShareFacts(
            Long sharesOutstanding,
            Long publicFloatUsd,
            LocalDate publicFloatDate
    ) {
        static SecShareFacts empty() {
            return new SecShareFacts(null, null, null);
        }
    }

    /**
     * Incorporation and HQ location from SEC submissions (company JSON).
     * Country fields hold jurisdiction/country names; state fields hold US state/territory codes or SEC opaque codes.
     */
    public record SubmissionsLocation(
            String countryIncorp,
            String countryHq,
            String stateIncorp,
            String stateHq
    ) {
        static SubmissionsLocation empty() {
            return new SubmissionsLocation(null, null, null, null);
        }
    }

    /**
     * Ordered fallback chain for shares outstanding.  Each entry is {taxonomy, tag}.
     * Preferred sources (point-in-time outstanding) come first; weighted-average
     * figures are last resort since they approximate but still give a usable market-cap.
     */
    private static final String[][] SHARES_OUTSTANDING_TAGS = {
            {"dei",     "EntityCommonStockSharesOutstanding"},
            {"us-gaap", "CommonStockSharesOutstanding"},
            {"us-gaap", "SharesOutstanding"},
            {"us-gaap", "CommonStockSharesIssued"},
            {"us-gaap", "WeightedAverageNumberOfShareOutstandingBasicAndDiluted"},
            {"us-gaap", "WeightedAverageNumberOfSharesOutstandingBasic"},
            {"us-gaap", "WeightedAverageNumberOfDilutedSharesOutstanding"},
    };

    /**
     * Latest DEI common shares outstanding and public float (if reported), plus a coarse country hint.
     * Tries multiple SEC XBRL tags in priority order to maximize coverage.
     */
    public static SecShareFacts parseShareFacts(JsonNode root) {
        if (root == null) {
            return SecShareFacts.empty();
        }
        Long shares = null;
        for (String[] entry : SHARES_OUTSTANDING_TAGS) {
            shares = latestFactVal(root, entry[0], entry[1]);
            if (shares != null && shares > 0) {
                break;
            }
        }
        DatedValue publicFloat = latestDeiNumericUnitsDated(root, "EntityPublicFloat", "USD");
        Long publicFloatUsd = publicFloat != null ? publicFloat.val() : null;
        LocalDate publicFloatDate = publicFloat != null ? publicFloat.endDate() : null;
        return new SecShareFacts(shares, publicFloatUsd, publicFloatDate);
    }

    /**
     * Extracts incorporation and HQ location from SEC submissions JSON.
     * Splits US state codes into {@code state*} and {@code United States} in {@code country*}.
     */
    public static SubmissionsLocation parseSubmissionsLocation(JsonNode root) {
        if (root == null) {
            return SubmissionsLocation.empty();
        }
        String incorpRaw = textOrNull(root, "stateOfIncorporationDescription");
        if (incorpRaw == null) {
            incorpRaw = textOrNull(root, "stateOfIncorporation");
        }
        LocationParts incorp = classifyJurisdiction(incorpRaw);

        JsonNode biz = navigatePath(root, "addresses", "business");
        LocationParts hq = parseBusinessHq(biz);

        return new SubmissionsLocation(
                incorp.country(),
                hq.country(),
                incorp.state(),
                hq.state()
        );
    }

    private static LocationParts parseBusinessHq(JsonNode biz) {
        if (biz == null) {
            return LocationParts.empty();
        }
        String countryField = textOrNull(biz, "country");
        String countryCodeField = textOrNull(biz, "countryCode");
        if (countryField != null && !countryField.isBlank()) {
            String c = normalizeCountry(countryField.trim());
            String stateFromBiz = extractUsStateIfDomestic(c, biz);
            return new LocationParts(c, stateFromBiz);
        }
        if (countryCodeField != null && !countryCodeField.isBlank()) {
            String c = mapCountryCodeToName(countryCodeField.trim());
            String stateFromBiz = extractUsStateIfDomestic(c, biz);
            return new LocationParts(c, stateFromBiz);
        }
        String raw = textOrNull(biz, "stateOrCountryDescription");
        if (raw == null) {
            raw = textOrNull(biz, "stateOrCountry");
        }
        return classifyJurisdiction(raw);
    }

    /**
     * When country is United States, prefer business stateOrCountry* for US subdivision.
     */
    private static String extractUsStateIfDomestic(String country, JsonNode biz) {
        if (!"United States".equals(country)) {
            return null;
        }
        String s = textOrNull(biz, "stateOrCountryDescription");
        if (s == null) {
            s = textOrNull(biz, "stateOrCountry");
        }
        if (s == null) {
            return null;
        }
        String upper = s.trim().toUpperCase(Locale.ROOT);
        if (upper.length() == 2 && US_STATE_AND_TERRITORY_CODES.contains(upper)) {
            return upper;
        }
        return null;
    }

    private static String mapCountryCodeToName(String code) {
        String upper = code.toUpperCase(Locale.ROOT);
        if ("US".equals(upper) || "USA".equals(upper)) {
            return "United States";
        }
        return ISO_ALPHA2_COUNTRY.getOrDefault(upper, normalizeCountry(code));
    }

    private record LocationParts(String country, String state) {
        static LocationParts empty() {
            return new LocationParts(null, null);
        }
    }

    /**
     * Classifies SEC "state or country" / incorporation strings into country + optional US state or opaque code.
     */
    static LocationParts classifyJurisdiction(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocationParts.empty();
        }
        String t = raw.trim();
        String upper = t.toUpperCase(Locale.ROOT);

        if (upper.length() == 2 && upper.chars().allMatch(Character::isLetter)) {
            if (US_STATE_AND_TERRITORY_CODES.contains(upper)) {
                return new LocationParts("United States", upper);
            }
            String isoCountry = ISO_ALPHA2_COUNTRY.get(upper);
            if (isoCountry != null) {
                return new LocationParts(isoCountry, null);
            }
            return new LocationParts(normalizeCountry(t), null);
        }

        if (upper.length() == 2 && upper.chars().allMatch(ch -> Character.isLetterOrDigit(ch))) {
            // SEC jurisdiction codes (e.g. M0, P7, X0) — keep code in state, unknown country
            return new LocationParts("Unknown", t);
        }

        return new LocationParts(normalizeCountry(t), null);
    }

    private static Long latestFactVal(JsonNode root, String taxonomy, String tag) {
        JsonNode units = navigatePath(root, "facts", taxonomy, tag, "units");
        if (units == null || !units.isObject()) {
            return null;
        }
        JsonNode arr = firstArrayUnit(units);
        if (arr == null) {
            return null;
        }
        DatedValue dv = latestDatedByEndDate(arr);
        return dv != null ? dv.val() : null;
    }

    private record DatedValue(long val, LocalDate endDate) {}

    private static DatedValue latestDeiNumericUnitsDated(JsonNode root, String tag, String unitKey) {
        JsonNode units = navigatePath(root, "facts", "dei", tag, "units");
        if (units == null || !units.isObject()) {
            return null;
        }
        JsonNode arr = units.get(unitKey);
        if (arr == null || !arr.isArray()) {
            return null;
        }
        return latestDatedByEndDate(arr);
    }

    private static DatedValue latestDatedByEndDate(JsonNode arr) {
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
        return new DatedValue(best.get("val").asLong(), bestDate);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.asText().isBlank()) {
            return null;
        }
        return child.asText();
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
        var fields = units.properties().iterator();
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

    public static String normalizeCountry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String u = raw.trim();
        if ("US".equalsIgnoreCase(u) || "USA".equalsIgnoreCase(u) || "United States".equalsIgnoreCase(u)) {
            return "United States";
        }
        return u;
    }
}

package com.fattorestreet.sec_api.util;

import java.util.Locale;

public final class SecTextUtils {

    private SecTextUtils() {
    }

    public static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static boolean containsToken(String normalizedText, String token) {
        return notBlank(normalizedText) && notBlank(token) && normalizedText.contains(token);
    }

    public static String normalizeDateExtractionText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String normalized = rawText;
        normalized = normalized.replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n");
        normalized = normalized.replaceAll("(?i)</\\s*(tr|p|div|li|table)\\s*>", "\n");
        normalized = normalized.replaceAll("(?i)<\\s*(tr|p|div|li|table|td|th)\\b[^>]*>", " ");
        normalized = normalized.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        normalized = normalized.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        normalized = normalized.replaceAll("(?is)<[^>]+>", " ");
        normalized = normalized
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&ndash;", "-")
                .replace("&mdash;", "-")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        normalized = normalized.replaceAll("[\\t\\x0B\\f\\r ]+", " ");
        normalized = normalized.replaceAll(" *\\n *", "\n");
        normalized = normalized.replaceAll("\\n{2,}", "\n");
        return normalized.trim();
    }
}

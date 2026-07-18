package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Shared date grammar and text normalization for SEC filing scans, used by both
 * {@code CorporateActionFilingDateService} and {@link DividendDeclarationTupleExtractor}.
 */
public final class FilingTextDates {

    public static final String MONTH_NAME_DATE_PATTERN =
            "(?:Jan(?:uary)?\\.?|Feb(?:ruary)?\\.?|Mar(?:ch)?\\.?|Apr(?:il)?\\.?|May\\.?|Jun(?:e)?\\.?|Jul(?:y)?\\.?|Aug(?:ust)?\\.?|Sep(?:t(?:ember)?)?\\.?|Oct(?:ober)?\\.?|Nov(?:ember)?\\.?|Dec(?:ember)?\\.?)\\s+\\d{1,2},?\\s+\\d{4}";
    public static final String NUMERIC_DATE_PATTERN = "\\d{1,2}/\\d{1,2}/\\d{4}";
    public static final String DATE_PATTERN = "(?:" + MONTH_NAME_DATE_PATTERN + "|" + NUMERIC_DATE_PATTERN + ")";

    private static final DateTimeFormatter MMM_D_YYYY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d, uuuu")
            .toFormatter(Locale.US);
    private static final DateTimeFormatter MMM_D_YYYY_NO_COMMA = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d uuuu")
            .toFormatter(Locale.US);
    private static final DateTimeFormatter MMMM_D_YYYY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d, uuuu")
            .toFormatter(Locale.US);
    private static final DateTimeFormatter MMMM_D_YYYY_NO_COMMA = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d uuuu")
            .toFormatter(Locale.US);
    private static final DateTimeFormatter M_D_YYYY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("M/d/uuuu")
            .toFormatter(Locale.US);

    private FilingTextDates() {
    }

    public static Optional<LocalDate> parseUsDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw
                .replace("Sept.", "Sep.")
                .replace("Sept", "Sep")
                .replaceAll("\\s+", " ")
                .trim();
        normalized = normalized.replace(".", "");
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{
                MMM_D_YYYY, MMMM_D_YYYY, MMM_D_YYYY_NO_COMMA, MMMM_D_YYYY_NO_COMMA, M_D_YYYY}) {
            try {
                return Optional.of(LocalDate.parse(normalized, formatter));
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }
        return Optional.empty();
    }

    /** Strips HTML tags/entities and collapses whitespace so regexes see plain prose. */
    public static String toSearchableText(String htmlOrText) {
        if (htmlOrText == null || htmlOrText.isBlank()) {
            return "";
        }
        String withoutScripts = htmlOrText
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ");
        String noTags = withoutScripts.replaceAll("(?is)<[^>]+>", " ");
        String unescaped = noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&#160;", " ")
                .replace("&#8217;", "'")
                .replace("&#8211;", "-");
        return unescaped.replaceAll("\\s+", " ").trim();
    }
}

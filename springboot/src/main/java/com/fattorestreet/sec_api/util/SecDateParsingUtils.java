package com.fattorestreet.sec_api.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecDateParsingUtils {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern YEAR_SLASH_DATE_PATTERN = Pattern.compile("(\\d{4}/\\d{1,2}/\\d{1,2})");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})");
    private static final Pattern SLASH_SHORT_YEAR_DATE_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2})");
    private static final Pattern DASH_DATE_PATTERN = Pattern.compile("(\\d{1,2}-\\d{1,2}-\\d{4})");
    private static final Pattern US_DATE_PATTERN = Pattern.compile(
            "(?i)(Jan\\.?|January|Feb\\.?|February|Mar\\.?|March|Apr\\.?|April|May|Jun\\.?|June|Jul\\.?|July|Aug\\.?|August|Sep\\.?|Sept\\.?|September|Oct\\.?|October|Nov\\.?|November|Dec\\.?|December)\\s+\\d{1,2},?\\s+\\d{4}");

    private SecDateParsingUtils() {
    }

    public static LocalDate parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static LocalDate parseUsDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        String normalizedDateText = dateText.replaceAll("(?i)\\b(Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\.", "$1");
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US),
                DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US),
                DateTimeFormatter.ofPattern("MMMM d uuuu", Locale.US),
                DateTimeFormatter.ofPattern("MMM d uuuu", Locale.US)
        );
        for (DateTimeFormatter format : formats) {
            try {
                return LocalDate.parse(normalizedDateText, format);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        return null;
    }

    public static LocalDate parseSlashDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static LocalDate parseSlashShortYearDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("M/d/uu", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static LocalDate parseYearSlashDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("uuuu/M/d", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static LocalDate parseDashDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("M-d-uuuu", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static LocalDate parseAnyDate(String dateText) {
        LocalDate iso = parseIsoDate(dateText);
        if (iso != null) {
            return iso;
        }
        LocalDate yearSlash = parseYearSlashDate(dateText);
        if (yearSlash != null) {
            return yearSlash;
        }
        LocalDate us = parseUsDate(dateText);
        if (us != null) {
            return us;
        }
        LocalDate slash = parseSlashDate(dateText);
        if (slash != null) {
            return slash;
        }
        LocalDate slashShortYear = parseSlashShortYearDate(dateText);
        if (slashShortYear != null) {
            return slashShortYear;
        }
        return parseDashDate(dateText);
    }

    public static List<LocalDate> extractAllDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(text);
        while (isoMatcher.find()) {
            LocalDate parsed = parseIsoDate(isoMatcher.group(1));
            if (parsed != null) {
                dates.add(parsed);
            }
        }
        Matcher yearSlashMatcher = YEAR_SLASH_DATE_PATTERN.matcher(text);
        while (yearSlashMatcher.find()) {
            LocalDate parsed = parseYearSlashDate(yearSlashMatcher.group(1));
            if (parsed != null) {
                dates.add(parsed);
            }
        }
        Matcher usMatcher = US_DATE_PATTERN.matcher(text);
        while (usMatcher.find()) {
            LocalDate parsed = parseUsDate(usMatcher.group());
            if (parsed != null) {
                dates.add(parsed);
            }
        }
        Matcher slashMatcher = SLASH_DATE_PATTERN.matcher(text);
        while (slashMatcher.find()) {
            LocalDate parsed = parseSlashDate(slashMatcher.group(1));
            if (parsed != null) {
                dates.add(parsed);
            }
        }
        Matcher slashShortMatcher = SLASH_SHORT_YEAR_DATE_PATTERN.matcher(text);
        while (slashShortMatcher.find()) {
            LocalDate parsed = parseSlashShortYearDate(slashShortMatcher.group(1));
            if (parsed != null) {
                dates.add(parsed);
            }
        }
        Matcher dashMatcher = DASH_DATE_PATTERN.matcher(text);
        while (dashMatcher.find()) {
            LocalDate parsed = parseDashDate(dashMatcher.group(1));
            if (parsed != null) {
                dates.add(parsed);
            }
        }
        return dates;
    }

    public static LocalDate previousBusinessDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate result = date.minusDays(1);
        while (result.getDayOfWeek() == DayOfWeek.SATURDAY || result.getDayOfWeek() == DayOfWeek.SUNDAY) {
            result = result.minusDays(1);
        }
        return result;
    }
}

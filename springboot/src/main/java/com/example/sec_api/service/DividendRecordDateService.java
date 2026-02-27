package com.example.sec_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DividendRecordDateService {

    private static final Logger log = LoggerFactory.getLogger(DividendRecordDateService.class);
    private static final LocalDate T_PLUS_ONE_CUTOFF = LocalDate.of(2024, 5, 28);
    private static final int MAX_8K_TO_SCAN = 250;
    private static final int MAX_EXHIBIT_DOCS_TO_SCAN = 6;

    private static final String DATE_PATTERN =
            "(?:Jan(?:uary)?\\.?|Feb(?:ruary)?\\.?|Mar(?:ch)?\\.?|Apr(?:il)?\\.?|May\\.?|Jun(?:e)?\\.?|Jul(?:y)?\\.?|Aug(?:ust)?\\.?|Sep(?:t(?:ember)?)?\\.?|Oct(?:ober)?\\.?|Nov(?:ember)?\\.?|Dec(?:ember)?\\.?)\\s+\\d{1,2},\\s+\\d{4}";
    private static final Pattern RECORD_DATE_NEAR_DIVIDEND = Pattern.compile(
            "(?is)dividend.{0,900}?record\\s+date.{0,220}?(" + DATE_PATTERN + ")");
    private static final Pattern SHAREHOLDER_OF_RECORD = Pattern.compile(
            "(?is)shareholders?\\s+of\\s+record.{0,200}?(" + DATE_PATTERN + ")");
    private static final Pattern HOLDERS_OF_RECORD = Pattern.compile(
            "(?is)holders?\\s+of\\s+record.{0,220}?(" + DATE_PATTERN + ")");
    private static final Pattern RECORD_AT_CLOSE_OF_BUSINESS = Pattern.compile(
            "(?is)record\\s+at\\s+the\\s+close\\s+of\\s+business.{0,160}?(" + DATE_PATTERN + ")");
    private static final Pattern RECORD_DATE_OF = Pattern.compile(
            "(?is)record\\s+date\\s+of.{0,100}?(" + DATE_PATTERN + ")");
    private static final Pattern RECORD_DATE_WILL_BE = Pattern.compile(
            "(?is)record\\s+date\\s+will\\s+be.{0,100}?(" + DATE_PATTERN + ")");
    private static final Pattern HREF_PATTERN = Pattern.compile("(?is)href\\s*=\\s*['\"]([^'\"]+)['\"]");

    private static final DateTimeFormatter MMM_D_YYYY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d, uuuu")
            .toFormatter(Locale.US);
    private static final DateTimeFormatter MMMM_D_YYYY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d, uuuu")
            .toFormatter(Locale.US);

    private final WebService webService;
    private final ObjectMapper mapper;

    public DividendRecordDateService(WebService webService, ObjectMapper mapper) {
        this.webService = webService;
        this.mapper = mapper;
    }

    public List<RecordDateCandidate> fetchDividendRecordDates(Long cik) {
        JsonNode root;
        try {
            root = mapper.readTree(webService.fetchSubmissions(cik));
        } catch (Exception e) {
            log.warn("Failed to fetch SEC submissions for CIK {}: {}", cik, e.getMessage());
            return Collections.emptyList();
        }

        JsonNode recent = root.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        JsonNode accessions = recent.path("accessionNumber");
        JsonNode primaryDocs = recent.path("primaryDocument");
        JsonNode filingDates = recent.path("filingDate");

        if (!forms.isArray() || !accessions.isArray() || !primaryDocs.isArray() || !filingDates.isArray()) {
            return Collections.emptyList();
        }

        Map<String, RecordDateCandidate> candidates = new LinkedHashMap<>();
        int n = Math.min(forms.size(), Math.min(accessions.size(), Math.min(primaryDocs.size(), filingDates.size())));
        int scanned8k = 0;
        for (int i = 0; i < n && scanned8k < MAX_8K_TO_SCAN; i++) {
            if (!"8-K".equalsIgnoreCase(forms.get(i).asText())) {
                continue;
            }
            scanned8k++;

            String accession = accessions.get(i).asText();
            String primaryDocument = primaryDocs.get(i).asText();
            String filingDateRaw = filingDates.get(i).asText();
            if (accession == null || accession.isBlank() || primaryDocument == null || primaryDocument.isBlank()) {
                continue;
            }

            try {
                String text = webService.fetchFilingDocument(cik, accession, primaryDocument);
                Optional<LocalDate> extracted = extractRecordDate(text);
                if (extracted.isEmpty()) {
                    extracted = extractRecordDateFromExhibits(cik, accession, text);
                }
                if (extracted.isEmpty()) {
                    continue;
                }
                LocalDate filingDate = LocalDate.parse(filingDateRaw);
                RecordDateCandidate candidate = new RecordDateCandidate(extracted.get(), filingDate, accession);
                String key = candidate.recordDate + "|" + accession;
                candidates.putIfAbsent(key, candidate);
            } catch (Exception ignored) {
                // Skip malformed or inaccessible filings and continue scanning.
            }
        }

        List<RecordDateCandidate> out = new ArrayList<>(candidates.values());
        out.sort(Comparator
                .comparing(RecordDateCandidate::recordDate)
                .thenComparing(RecordDateCandidate::filingDate));
        return out;
    }

    public LocalDate computeExDividendDate(LocalDate recordDate) {
        if (recordDate == null) {
            return null;
        }

        LocalDate normalizedRecordDate = nextBusinessDay(recordDate);
        if (normalizedRecordDate.isBefore(T_PLUS_ONE_CUTOFF)) {
            return previousBusinessDay(normalizedRecordDate);
        }
        return normalizedRecordDate;
    }

    private Optional<LocalDate> extractRecordDate(String text) {
        String searchable = toSearchableText(text);
        if (searchable.isBlank()) {
            return Optional.empty();
        }

        Matcher m = RECORD_DATE_NEAR_DIVIDEND.matcher(searchable);
        if (m.find()) {
            return parseUsDate(m.group(1));
        }

        Matcher m2 = SHAREHOLDER_OF_RECORD.matcher(searchable);
        if (m2.find()) {
            return parseUsDate(m2.group(1));
        }

        Matcher m3 = HOLDERS_OF_RECORD.matcher(searchable);
        if (m3.find()) {
            return parseUsDate(m3.group(1));
        }

        Matcher m4 = RECORD_AT_CLOSE_OF_BUSINESS.matcher(searchable);
        if (m4.find()) {
            return parseUsDate(m4.group(1));
        }

        Matcher m5 = RECORD_DATE_OF.matcher(searchable);
        if (m5.find()) {
            return parseUsDate(m5.group(1));
        }

        Matcher m6 = RECORD_DATE_WILL_BE.matcher(searchable);
        if (m6.find()) {
            return parseUsDate(m6.group(1));
        }

        return Optional.empty();
    }

    private Optional<LocalDate> extractRecordDateFromExhibits(Long cik, String accession, String filingHtml) {
        List<String> exhibitDocuments = extractExhibitDocumentPaths(filingHtml);
        int scanned = 0;
        for (String doc : exhibitDocuments) {
            if (scanned >= MAX_EXHIBIT_DOCS_TO_SCAN) {
                break;
            }
            scanned++;
            try {
                String exhibitText = webService.fetchFilingDocument(cik, accession, doc);
                Optional<LocalDate> extracted = extractRecordDate(exhibitText);
                if (extracted.isPresent()) {
                    return extracted;
                }
            } catch (Exception ignored) {
                // Continue scanning exhibit candidates.
            }
        }
        return Optional.empty();
    }

    private List<String> extractExhibitDocumentPaths(String filingHtml) {
        if (filingHtml == null || filingHtml.isBlank()) {
            return Collections.emptyList();
        }
        Matcher matcher = HREF_PATTERN.matcher(filingHtml);
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        while (matcher.find()) {
            String href = matcher.group(1);
            if (href == null || href.isBlank()) {
                continue;
            }
            String normalized = href.trim();
            String lower = normalized.toLowerCase(Locale.US);
            if (!lower.endsWith(".htm") && !lower.endsWith(".html") && !lower.endsWith(".txt")) {
                continue;
            }
            if (!(lower.contains("ex99") || lower.contains("99-") || lower.contains("exhibit99"))) {
                continue;
            }
            int query = normalized.indexOf('?');
            if (query >= 0) {
                normalized = normalized.substring(0, query);
            }
            if (normalized.contains("/")) {
                normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
            }
            if (!normalized.isBlank()) {
                paths.add(normalized);
            }
        }
        return paths.stream().limit(MAX_EXHIBIT_DOCS_TO_SCAN).collect(Collectors.toList());
    }

    private Optional<LocalDate> parseUsDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw
                .replace("Sept.", "Sep.")
                .replace("Sept", "Sep")
                .replaceAll("\\s+", " ")
                .trim();
        normalized = normalized.replace(".", "");
        try {
            return Optional.of(LocalDate.parse(normalized, MMM_D_YYYY));
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(LocalDate.parse(normalized, MMMM_D_YYYY));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
    }

    private String toSearchableText(String htmlOrText) {
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

    private LocalDate nextBusinessDay(LocalDate date) {
        LocalDate d = date;
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    private LocalDate previousBusinessDay(LocalDate date) {
        LocalDate d = date.minusDays(1);
        while (!isBusinessDay(d)) {
            d = d.minusDays(1);
        }
        return d;
    }

    private boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !nyseHolidays(date.getYear()).contains(date);
    }

    private Set<LocalDate> nyseHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        holidays.add(observed(LocalDate.of(year, Month.JANUARY, 1)));
        holidays.add(nthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3)); // MLK
        holidays.add(nthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3)); // Presidents
        holidays.add(goodFriday(year));
        holidays.add(lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY)); // Memorial
        holidays.add(observed(LocalDate.of(year, Month.JUNE, 19))); // Juneteenth
        holidays.add(observed(LocalDate.of(year, Month.JULY, 4)));
        holidays.add(nthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1)); // Labor
        holidays.add(nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)); // Thanksgiving
        holidays.add(observed(LocalDate.of(year, Month.DECEMBER, 25)));

        return holidays;
    }

    private LocalDate observed(LocalDate holiday) {
        if (holiday.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return holiday.minusDays(1);
        }
        if (holiday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return holiday.plusDays(1);
        }
        return holiday;
    }

    private LocalDate nthWeekdayOfMonth(int year, Month month, DayOfWeek dow, int nth) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(nth, dow));
    }

    private LocalDate lastWeekdayOfMonth(int year, Month month, DayOfWeek dow) {
        return LocalDate.of(year, month, MonthDay.of(month, 1).atYear(year).lengthOfMonth())
                .with(TemporalAdjusters.previousOrSame(dow));
    }

    // Anonymous Gregorian algorithm + offset to Friday.
    private LocalDate goodFriday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        LocalDate easter = LocalDate.of(year, month, day);
        return easter.minusDays(2);
    }

    public record RecordDateCandidate(LocalDate recordDate, LocalDate filingDate, String accessionNumber) {}
}

package com.fattorestreet.sec_api.service;

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
import java.util.stream.IntStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DividendRecordDateService {

    private static final Logger log = LoggerFactory.getLogger(DividendRecordDateService.class);
    private static final LocalDate T_PLUS_ONE_CUTOFF = LocalDate.of(2024, 5, 28);
    private static final int MAX_8K_TO_SCAN = 250;
    private static final int MAX_SPLIT_8K_TO_SCAN = 400;
    private static final int MAX_SUBMISSION_FILES_TO_SCAN = 48;
    private static final int MAX_EXHIBIT_DOCS_TO_SCAN = 6;

    private static final String MONTH_NAME_DATE_PATTERN =
            "(?:Jan(?:uary)?\\.?|Feb(?:ruary)?\\.?|Mar(?:ch)?\\.?|Apr(?:il)?\\.?|May\\.?|Jun(?:e)?\\.?|Jul(?:y)?\\.?|Aug(?:ust)?\\.?|Sep(?:t(?:ember)?)?\\.?|Oct(?:ober)?\\.?|Nov(?:ember)?\\.?|Dec(?:ember)?\\.?)\\s+\\d{1,2},?\\s+\\d{4}";
    private static final String NUMERIC_DATE_PATTERN = "\\d{1,2}/\\d{1,2}/\\d{4}";
    private static final String DATE_PATTERN = "(?:" + MONTH_NAME_DATE_PATTERN + "|" + NUMERIC_DATE_PATTERN + ")";
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
    private static final Pattern GENERIC_RECORD_DATE = Pattern.compile(
            "(?is)record\\s+date.{0,120}?(" + DATE_PATTERN + ")");
    private static final Pattern SPLIT_EFFECTIVE_DATE = Pattern.compile(
            "(?is)(?:stock\\s+split|split\\s+of\\s+its\\s+common\\s+stock).{0,900}?(?:effective|to\\s+be\\s+effective|will\\s+be\\s+effective).{0,160}?(" + DATE_PATTERN + ")");
    private static final Pattern SPLIT_ADJUSTED_TRADING = Pattern.compile(
            "(?is)split-adjusted\\s+basis.{0,220}?(?:start\\s+of\\s+trading|trading\\s+will\\s+begin|will\\s+begin\\s+trading|will\\s+begin|starts?|begins?).{0,140}?(" + DATE_PATTERN + ")");
    private static final Pattern SPLIT_DISTRIBUTION_DATE = Pattern.compile(
            "(?is)(?:stock\\s+split|split\\s+of\\s+its\\s+common\\s+stock).{0,900}?(?:distribution\\s+date|payable\\s+date).{0,160}?(" + DATE_PATTERN + ")");
    private static final Pattern SPLIT_GENERIC_DATE = Pattern.compile(
            "(?is)(?:stock\\s+split|split\\s+of\\s+its\\s+common\\s+stock).{0,900}?(" + DATE_PATTERN + ")");
    private static final Pattern DATE_CAPTURE_PATTERN = Pattern.compile("(?i)(" + DATE_PATTERN + ")");
    private static final Pattern SENTENCE_SPLIT_TRIGGER = Pattern.compile("(?i)\\b(split|stock split|split-adjusted)\\b");
    private static final Pattern SENTENCE_DIVIDEND_TRIGGER = Pattern.compile("(?i)\\b(dividend|record date|shareholders of record|holders of record)\\b");
    private static final Pattern HREF_PATTERN = Pattern.compile("(?is)href\\s*=\\s*['\"]([^'\"]+)['\"]");

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

        Map<LocalDate, RecordDateCandidate> candidatesByDate = new HashMap<>();
        List<EightKFiling> filings = collectEightKFilingRows(cik, root, MAX_8K_TO_SCAN);
        log.info("[CIK {}] Dividend record-date scan starting: {} candidate 8-K filings", cik, filings.size());
        int processed = 0;
        for (EightKFiling filing : filings) {
            processed++;
            if (processed == 1 || processed % 25 == 0 || processed == filings.size()) {
                log.info("[CIK {}] Dividend record-date scan progress: {}/{} filings processed, {} unique candidates",
                        cik, processed, filings.size(), candidatesByDate.size());
            }
            try {
                String text = webService.fetchFilingDocument(cik, filing.accessionNumber(), filing.primaryDocument());
                List<ExtractedRecordDate> extracted = new ArrayList<>(
                        extractRecordDateCandidates(text, "primary:" + filing.primaryDocument()));
                extracted.addAll(extractRecordDateCandidatesFromExhibits(cik, filing.accessionNumber(), text));
                if (extracted.isEmpty()) {
                    continue;
                }
                ExtractedRecordDate best = extracted.stream()
                        .max(candidateSelectionComparator())
                        .orElse(null);
                if (best == null) {
                    continue;
                }
                RecordDateCandidate candidate = new RecordDateCandidate(best.date(), filing.filingDate(), filing.accessionNumber(), best.score());
                RecordDateCandidate current = candidatesByDate.get(candidate.recordDate());
                if (current == null || isPreferredCandidate(candidate, current)) {
                    candidatesByDate.put(candidate.recordDate(), candidate);
                }
            } catch (Exception ignored) {
                // Skip malformed or inaccessible filings and continue scanning.
            }
        }

        List<RecordDateCandidate> out = new ArrayList<>(candidatesByDate.values());
        out.sort(Comparator
                .comparing(RecordDateCandidate::recordDate)
                .thenComparing(RecordDateCandidate::confidenceScore, Comparator.reverseOrder())
                .thenComparing(RecordDateCandidate::filingDate));
        log.info("[CIK {}] Dividend record-date scan finished: {} unique candidates", cik, out.size());
        return out;
    }

    public List<SplitDateCandidate> fetchSplitEffectiveDates(Long cik) {
        JsonNode root;
        try {
            root = mapper.readTree(webService.fetchSubmissions(cik));
        } catch (Exception e) {
            log.warn("Failed to fetch SEC submissions for split-date scan CIK {}: {}", cik, e.getMessage());
            return Collections.emptyList();
        }

        Map<LocalDate, SplitDateCandidate> candidatesByDate = new HashMap<>();
        Map<String, Integer> splitIntentCounts = new TreeMap<>();
        List<EightKFiling> filings = collectEightKFilingRows(cik, root, MAX_SPLIT_8K_TO_SCAN);
        log.info("[CIK {}] Split effective-date scan starting: {} candidate 8-K filings", cik, filings.size());
        int processed = 0;
        for (EightKFiling filing : filings) {
            processed++;
            if (processed == 1 || processed % 25 == 0 || processed == filings.size()) {
                log.info("[CIK {}] Split effective-date scan progress: {}/{} filings processed, {} unique candidates",
                        cik, processed, filings.size(), candidatesByDate.size());
            }
            try {
                String text = webService.fetchFilingDocument(cik, filing.accessionNumber(), filing.primaryDocument());
                List<ExtractedRecordDate> extracted = new ArrayList<>(
                        extractSplitDateCandidates(text, "primary:" + filing.primaryDocument()));
                extracted.addAll(extractSplitDateCandidatesFromExhibits(cik, filing.accessionNumber(), text));
                if (extracted.isEmpty()) {
                    continue;
                }
                for (ExtractedRecordDate candidate : extracted) {
                    splitIntentCounts.merge(candidate.patternLabel(), 1, Integer::sum);
                }
                logSplitCandidateRanking(cik, filing, extracted);
                ExtractedRecordDate best = extracted.stream()
                        .max(candidateSelectionComparator())
                        .orElse(null);
                if (best == null) {
                    continue;
                }
                log.info("[CIK {}] Split candidate selected for accession {}: date={}, score={}, intent={}, confidence={}, source={}, pattern={}",
                        cik, filing.accessionNumber(), best.date(), best.score(), best.intentRank(), best.confidenceLabel(), best.source(), best.patternLabel());
                SplitDateCandidate candidate = new SplitDateCandidate(best.date(), filing.filingDate(), filing.accessionNumber(), best.score());
                SplitDateCandidate current = candidatesByDate.get(candidate.effectiveDate());
                if (current == null || isPreferredSplitCandidate(candidate, current)) {
                    candidatesByDate.put(candidate.effectiveDate(), candidate);
                }
            } catch (Exception ignored) {
                // Ignore malformed filing documents and continue.
            }
        }

        List<SplitDateCandidate> out = new ArrayList<>(candidatesByDate.values());
        out.sort(Comparator
                .comparing(SplitDateCandidate::effectiveDate)
                .thenComparing(SplitDateCandidate::confidenceScore, Comparator.reverseOrder())
                .thenComparing(SplitDateCandidate::filingDate));
        if (!out.isEmpty()) {
            String ranked = IntStream.range(0, out.size())
                    .mapToObj(i -> {
                        SplitDateCandidate c = out.get(i);
                        return String.format("#%d %s score=%d accession=%s filingDate=%s",
                                i + 1, c.effectiveDate(), c.confidenceScore(), c.accessionNumber(), c.filingDate());
                    })
                    .collect(Collectors.joining(" | "));
            log.info("[CIK {}] Split unique-candidate ranking: {}", cik, ranked);
        }
        if (!splitIntentCounts.isEmpty()) {
            String intentSummary = splitIntentCounts.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
            log.info("[CIK {}] Split candidate intent summary: {}", cik, intentSummary);
        }
        log.info("[CIK {}] Split effective-date scan finished: {} unique candidates", cik, out.size());
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

    private List<ExtractedRecordDate> extractRecordDateCandidates(String text, String source) {
        String searchable = toSearchableText(text);
        if (searchable.isBlank()) {
            return Collections.emptyList();
        }

        List<PatternSpec> specs = List.of(
                new PatternSpec(RECORD_DATE_NEAR_DIVIDEND, 130, "RECORD_DATE_NEAR_DIVIDEND"),
                new PatternSpec(SHAREHOLDER_OF_RECORD, 120, "SHAREHOLDER_OF_RECORD"),
                new PatternSpec(HOLDERS_OF_RECORD, 115, "HOLDERS_OF_RECORD"),
                new PatternSpec(RECORD_AT_CLOSE_OF_BUSINESS, 110, "RECORD_AT_CLOSE_OF_BUSINESS"),
                new PatternSpec(RECORD_DATE_OF, 95, "RECORD_DATE_OF"),
                new PatternSpec(RECORD_DATE_WILL_BE, 90, "RECORD_DATE_WILL_BE"),
                new PatternSpec(GENERIC_RECORD_DATE, 70, "GENERIC_RECORD_DATE")
        );
        List<ExtractedRecordDate> regexCandidates = extractDatedCandidates(searchable, specs, source);
        List<ExtractedRecordDate> sentenceCandidates = extractSentenceCandidates(searchable, source, false);
        return mergeCandidates(regexCandidates, sentenceCandidates);
    }

    private List<ExtractedRecordDate> extractRecordDateCandidatesFromExhibits(Long cik, String accession, String filingHtml) {
        List<String> exhibitDocuments = extractExhibitDocumentPaths(filingHtml);
        List<ExtractedRecordDate> candidates = new ArrayList<>();
        int scanned = 0;
        for (String doc : exhibitDocuments) {
            if (scanned >= MAX_EXHIBIT_DOCS_TO_SCAN) {
                break;
            }
            scanned++;
            try {
                String exhibitText = webService.fetchFilingDocument(cik, accession, doc);
                for (ExtractedRecordDate extracted : extractRecordDateCandidates(exhibitText, "exhibit:" + doc)) {
                    candidates.add(new ExtractedRecordDate(
                            extracted.date(),
                            extracted.score() - 5,
                            extracted.matchIndex(),
                            extracted.source(),
                            extracted.patternLabel(),
                            extracted.intentRank(),
                            extracted.confidenceLabel()));
                }
            } catch (Exception ignored) {
                // Continue scanning exhibit candidates.
            }
        }
        return candidates;
    }

    private List<ExtractedRecordDate> extractSplitDateCandidates(String text, String source) {
        String searchable = toSearchableText(text);
        if (searchable.isBlank()) {
            return Collections.emptyList();
        }
        List<ExtractedRecordDate> splitAdjustedTradingCandidates = extractDatedCandidates(
                searchable,
                List.of(new PatternSpec(SPLIT_ADJUSTED_TRADING, 130, "SPLIT_ADJUSTED_TRADING")),
                source);
        if (!splitAdjustedTradingCandidates.isEmpty()) {
            List<ExtractedRecordDate> sentenceCandidates = extractSentenceCandidates(searchable, source, true);
            return mergeCandidates(splitAdjustedTradingCandidates, sentenceCandidates);
        }
        List<PatternSpec> specs = List.of(
                new PatternSpec(SPLIT_EFFECTIVE_DATE, 125, "SPLIT_EFFECTIVE_DATE"),
                new PatternSpec(SPLIT_DISTRIBUTION_DATE, 105, "SPLIT_DISTRIBUTION_DATE"),
                new PatternSpec(SPLIT_GENERIC_DATE, 80, "SPLIT_GENERIC_DATE")
        );
        List<ExtractedRecordDate> regexCandidates = extractDatedCandidates(searchable, specs, source);
        List<ExtractedRecordDate> sentenceCandidates = extractSentenceCandidates(searchable, source, true);
        return mergeCandidates(regexCandidates, sentenceCandidates);
    }

    private List<ExtractedRecordDate> extractSplitDateCandidatesFromExhibits(Long cik, String accession, String filingHtml) {
        List<String> exhibitDocuments = extractExhibitDocumentPaths(filingHtml);
        List<ExtractedRecordDate> candidates = new ArrayList<>();
        int scanned = 0;
        for (String doc : exhibitDocuments) {
            if (scanned >= MAX_EXHIBIT_DOCS_TO_SCAN) {
                break;
            }
            scanned++;
            try {
                String exhibitText = webService.fetchFilingDocument(cik, accession, doc);
                for (ExtractedRecordDate extracted : extractSplitDateCandidates(exhibitText, "exhibit:" + doc)) {
                    candidates.add(new ExtractedRecordDate(
                            extracted.date(),
                            extracted.score() - 5,
                            extracted.matchIndex(),
                            extracted.source(),
                            extracted.patternLabel(),
                            extracted.intentRank(),
                            extracted.confidenceLabel()));
                }
            } catch (Exception ignored) {
                // Continue scanning exhibit candidates.
            }
        }
        return candidates;
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
            if (!(lower.contains("ex99")
                    || lower.contains("99-")
                    || lower.contains("exhibit99")
                    || lower.contains("exhibit")
                    || lower.contains("ex-")
                    || lower.contains("exh")
                    || lower.contains("press"))) {
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
            } catch (DateTimeParseException ignoredLong) {
                try {
                    return Optional.of(LocalDate.parse(normalized, MMM_D_YYYY_NO_COMMA));
                } catch (DateTimeParseException ignoredShortNoComma) {
                    try {
                        return Optional.of(LocalDate.parse(normalized, MMMM_D_YYYY_NO_COMMA));
                    } catch (DateTimeParseException ignoredLongNoComma) {
                        try {
                            return Optional.of(LocalDate.parse(normalized, M_D_YYYY));
                        } catch (DateTimeParseException e) {
                            return Optional.empty();
                        }
                    }
                }
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

    private List<EightKFiling> collectEightKFilingRows(Long cik, JsonNode submissionsRoot, int maxEightK) {
        List<EightKFiling> out = new ArrayList<>();
        Set<String> seenAccessions = new HashSet<>();

        collectFromSubmissionRoot(submissionsRoot, out, seenAccessions, maxEightK);
        if (out.size() >= maxEightK) {
            return out;
        }

        JsonNode files = submissionsRoot.path("filings").path("files");
        if (!files.isArray()) {
            return out;
        }
        int scannedFiles = 0;
        for (JsonNode fileNode : files) {
            if (out.size() >= maxEightK || scannedFiles >= MAX_SUBMISSION_FILES_TO_SCAN) {
                break;
            }
            String name = fileNode.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            scannedFiles++;
            try {
                JsonNode archivedRoot = mapper.readTree(webService.fetchSubmissionsFile(cik, name));
                collectFromSubmissionRoot(archivedRoot, out, seenAccessions, maxEightK);
            } catch (Exception ignored) {
                // Skip inaccessible archived submission files.
            }
        }
        return out;
    }

    private void collectFromSubmissionRoot(
            JsonNode submissionRoot,
            List<EightKFiling> output,
            Set<String> seenAccessions,
            int maxEightK) {
        JsonNode recent = submissionRoot.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        JsonNode accessions = recent.path("accessionNumber");
        JsonNode primaryDocs = recent.path("primaryDocument");
        JsonNode filingDates = recent.path("filingDate");
        if (!forms.isArray() || !accessions.isArray() || !primaryDocs.isArray() || !filingDates.isArray()) {
            return;
        }
        int n = Math.min(forms.size(), Math.min(accessions.size(), Math.min(primaryDocs.size(), filingDates.size())));
        for (int i = 0; i < n && output.size() < maxEightK; i++) {
            if (!"8-K".equalsIgnoreCase(forms.get(i).asText())) {
                continue;
            }
            String accession = accessions.get(i).asText("");
            String primaryDocument = primaryDocs.get(i).asText("");
            String filingDateRaw = filingDates.get(i).asText("");
            if (accession.isBlank() || primaryDocument.isBlank() || filingDateRaw.isBlank()) {
                continue;
            }
            if (seenAccessions.contains(accession)) {
                continue;
            }
            try {
                LocalDate filingDate = LocalDate.parse(filingDateRaw);
                output.add(new EightKFiling(accession, primaryDocument, filingDate));
                seenAccessions.add(accession);
            } catch (Exception ignored) {
                // Skip rows with malformed dates.
            }
        }
    }

    private List<ExtractedRecordDate> extractDatedCandidates(String searchable, List<PatternSpec> specs, String source) {
        Map<LocalDate, ExtractedRecordDate> bestByDate = new HashMap<>();
        for (PatternSpec spec : specs) {
            Matcher matcher = spec.pattern().matcher(searchable);
            int matchIndex = 0;
            while (matcher.find()) {
                Optional<LocalDate> parsed = parseUsDate(matcher.group(1));
                if (parsed.isEmpty()) {
                    continue;
                }
                int score = Math.max(1, spec.baseScore() - (matchIndex * 2));
                ExtractedRecordDate candidate = new ExtractedRecordDate(
                        parsed.get(),
                        score,
                        matchIndex,
                        source,
                        spec.label(),
                        intentRankForPattern(spec.label()),
                        confidenceLabelForScore(score));
                ExtractedRecordDate current = bestByDate.get(candidate.date());
                if (current == null || compareCandidates(candidate, current) > 0) {
                    bestByDate.put(candidate.date(), candidate);
                }
                matchIndex++;
            }
        }
        return bestByDate.values().stream()
                .sorted(candidateSelectionComparator().reversed())
                .toList();
    }

    private void logSplitCandidateRanking(Long cik, EightKFiling filing, List<ExtractedRecordDate> extracted) {
        List<ExtractedRecordDate> ranked = extracted.stream()
                .sorted(candidateSelectionComparator().reversed())
                .toList();
        String ranking = IntStream.range(0, ranked.size())
                .mapToObj(i -> {
                    ExtractedRecordDate c = ranked.get(i);
                    return String.format("#%d %s score=%d intent=%d confidence=%s source=%s pattern=%s",
                            i + 1, c.date(), c.score(), c.intentRank(), c.confidenceLabel(), c.source(), c.patternLabel());
                })
                .collect(Collectors.joining(" | "));
        log.info("[CIK {}] Split candidate ranking for accession {} (doc={}): {}",
                cik, filing.accessionNumber(), filing.primaryDocument(), ranking);
    }

    private List<ExtractedRecordDate> mergeCandidates(List<ExtractedRecordDate> left, List<ExtractedRecordDate> right) {
        Map<LocalDate, ExtractedRecordDate> bestByDate = new HashMap<>();
        for (ExtractedRecordDate candidate : left) {
            bestByDate.put(candidate.date(), candidate);
        }
        for (ExtractedRecordDate candidate : right) {
            ExtractedRecordDate current = bestByDate.get(candidate.date());
            if (current == null || compareCandidates(candidate, current) > 0) {
                bestByDate.put(candidate.date(), candidate);
            }
        }
        return bestByDate.values().stream()
                .sorted(candidateSelectionComparator().reversed())
                .toList();
    }

    private List<ExtractedRecordDate> extractSentenceCandidates(String searchable, String source, boolean splitMode) {
        if (searchable.isBlank()) {
            return Collections.emptyList();
        }
        String[] rawSentences = searchable.split("(?<=[.!?;])\\s+");
        List<ExtractedRecordDate> out = new ArrayList<>();
        int matchIndex = 0;
        for (String sentence : rawSentences) {
            if (sentence == null || sentence.isBlank()) {
                continue;
            }
            String normalized = sentence.trim();
            boolean trigger = splitMode
                    ? SENTENCE_SPLIT_TRIGGER.matcher(normalized).find()
                    : SENTENCE_DIVIDEND_TRIGGER.matcher(normalized).find();
            if (!trigger) {
                continue;
            }
            SentenceIntent intent = classifySentenceIntent(normalized, splitMode);
            if (intent == SentenceIntent.GENERIC) {
                continue;
            }
            Matcher dateMatcher = DATE_CAPTURE_PATTERN.matcher(normalized);
            int localIndex = 0;
            while (dateMatcher.find()) {
                Optional<LocalDate> parsed = parseUsDate(dateMatcher.group(1));
                if (parsed.isEmpty()) {
                    continue;
                }
                int proximityBoost = proximityBoost(normalized, dateMatcher.start(1), splitMode, intent);
                int score = Math.max(1, intent.baseScore() + proximityBoost - (localIndex * 2));
                out.add(new ExtractedRecordDate(
                        parsed.get(),
                        score,
                        matchIndex,
                        source,
                        intent.label(),
                        intent.rank(),
                        confidenceLabelForScore(score)));
                localIndex++;
                matchIndex++;
            }
        }
        return out;
    }

    private int proximityBoost(String sentence, int dateStart, boolean splitMode, SentenceIntent intent) {
        String lower = sentence.toLowerCase(Locale.US);
        if (splitMode) {
            int anchor = firstExistingIndex(lower, "split-adjusted", "trading", "begin", "start");
            if (anchor < 0) {
                return 0;
            }
            return Math.max(0, 22 - Math.min(22, Math.abs(dateStart - anchor) / 5));
        }
        if (intent == SentenceIntent.DIVIDEND_RECORD_DATE_STRONG
                || intent == SentenceIntent.DIVIDEND_SHAREHOLDERS_OF_RECORD
                || intent == SentenceIntent.DIVIDEND_RECORD_DATE_GENERIC) {
            int anchor = firstExistingIndex(lower, "shareholders of record", "holders of record", "record date", "record");
            if (anchor < 0) {
                return 0;
            }
            if (dateStart < anchor) {
                // Dates before the "record" phrase are frequently payable dates; down-rank them.
                return -24;
            }
            return Math.max(0, 28 - Math.min(28, Math.abs(dateStart - anchor) / 4));
        }
        return 0;
    }

    private int firstExistingIndex(String text, String... needles) {
        int index = Integer.MAX_VALUE;
        for (String needle : needles) {
            int pos = text.indexOf(needle);
            if (pos >= 0 && pos < index) {
                index = pos;
            }
        }
        return index == Integer.MAX_VALUE ? -1 : index;
    }

    private SentenceIntent classifySentenceIntent(String sentence, boolean splitMode) {
        String lower = sentence.toLowerCase(Locale.US);
        if (splitMode) {
            if (lower.contains("split-adjusted basis")
                    && (lower.contains("begin") || lower.contains("start") || lower.contains("trading"))) {
                return SentenceIntent.SPLIT_ADJUSTED_TRADING_START;
            }
            if (lower.contains("split")
                    && lower.contains("trading")
                    && (lower.contains("begin") || lower.contains("start"))) {
                return SentenceIntent.SPLIT_ADJUSTED_TRADING_START;
            }
            if (lower.contains("split") && lower.contains("effective")) {
                return SentenceIntent.SPLIT_EFFECTIVE;
            }
            if (lower.contains("split") && (lower.contains("distribution date") || lower.contains("payable date"))) {
                return SentenceIntent.SPLIT_DISTRIBUTION_OR_PAYABLE;
            }
            if (lower.contains("split")) {
                return SentenceIntent.SPLIT_GENERIC;
            }
            return SentenceIntent.GENERIC;
        }
        if (lower.contains("dividend") && lower.contains("record date")) {
            return SentenceIntent.DIVIDEND_RECORD_DATE_STRONG;
        }
        if (lower.contains("shareholders of record") || lower.contains("holders of record")) {
            return SentenceIntent.DIVIDEND_SHAREHOLDERS_OF_RECORD;
        }
        if (lower.contains("record date")) {
            return SentenceIntent.DIVIDEND_RECORD_DATE_GENERIC;
        }
        return SentenceIntent.GENERIC;
    }

    private Comparator<ExtractedRecordDate> candidateSelectionComparator() {
        return Comparator
                .comparingInt(ExtractedRecordDate::score)
                .thenComparingInt(ExtractedRecordDate::intentRank)
                .thenComparingInt(e -> -e.matchIndex())
                .thenComparingInt(e -> e.source().startsWith("primary:") ? 1 : 0)
                .thenComparing(ExtractedRecordDate::source)
                .thenComparing(ExtractedRecordDate::patternLabel);
    }

    private int compareCandidates(ExtractedRecordDate left, ExtractedRecordDate right) {
        return candidateSelectionComparator().compare(left, right);
    }

    private int intentRankForPattern(String label) {
        return switch (label) {
            case "SPLIT_ADJUSTED_TRADING", "SENTENCE_SPLIT_ADJUSTED_TRADING_START" -> 4;
            case "SPLIT_EFFECTIVE_DATE", "SENTENCE_SPLIT_EFFECTIVE" -> 3;
            case "SPLIT_DISTRIBUTION_DATE", "SENTENCE_SPLIT_DISTRIBUTION_OR_PAYABLE" -> 2;
            case "RECORD_DATE_NEAR_DIVIDEND", "SENTENCE_DIVIDEND_RECORD_DATE_STRONG" -> 4;
            case "SHAREHOLDER_OF_RECORD", "HOLDERS_OF_RECORD", "SENTENCE_DIVIDEND_SHAREHOLDERS_OF_RECORD" -> 3;
            case "RECORD_AT_CLOSE_OF_BUSINESS", "RECORD_DATE_OF", "RECORD_DATE_WILL_BE", "SENTENCE_DIVIDEND_RECORD_DATE_GENERIC" -> 2;
            default -> 1;
        };
    }

    private String confidenceLabelForScore(int score) {
        if (score >= 130) return "high";
        if (score >= 100) return "medium";
        return "low";
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
        if (year >= 2022) {
            holidays.add(observed(LocalDate.of(year, Month.JUNE, 19))); // Juneteenth
        }
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

    private boolean isPreferredCandidate(RecordDateCandidate left, RecordDateCandidate right) {
        if (left.confidenceScore() != right.confidenceScore()) {
            return left.confidenceScore() > right.confidenceScore();
        }
        if (left.filingDate() != null && right.filingDate() != null && !left.filingDate().equals(right.filingDate())) {
            return left.filingDate().isBefore(right.filingDate());
        }
        return left.accessionNumber().compareTo(right.accessionNumber()) < 0;
    }

    private boolean isPreferredSplitCandidate(SplitDateCandidate left, SplitDateCandidate right) {
        if (left.confidenceScore() != right.confidenceScore()) {
            return left.confidenceScore() > right.confidenceScore();
        }
        if (left.filingDate() != null && right.filingDate() != null && !left.filingDate().equals(right.filingDate())) {
            return left.filingDate().isBefore(right.filingDate());
        }
        return left.accessionNumber().compareTo(right.accessionNumber()) < 0;
    }

    private record PatternSpec(Pattern pattern, int baseScore, String label) {}
    private record ExtractedRecordDate(
            LocalDate date,
            int score,
            int matchIndex,
            String source,
            String patternLabel,
            int intentRank,
            String confidenceLabel) {}
    private record EightKFiling(String accessionNumber, String primaryDocument, LocalDate filingDate) {}

    private enum SentenceIntent {
        GENERIC("SENTENCE_GENERIC", 0, 0),
        SPLIT_ADJUSTED_TRADING_START("SENTENCE_SPLIT_ADJUSTED_TRADING_START", 165, 4),
        SPLIT_EFFECTIVE("SENTENCE_SPLIT_EFFECTIVE", 145, 3),
        SPLIT_DISTRIBUTION_OR_PAYABLE("SENTENCE_SPLIT_DISTRIBUTION_OR_PAYABLE", 125, 2),
        SPLIT_GENERIC("SENTENCE_SPLIT_GENERIC", 95, 1),
        DIVIDEND_RECORD_DATE_STRONG("SENTENCE_DIVIDEND_RECORD_DATE_STRONG", 155, 4),
        DIVIDEND_SHAREHOLDERS_OF_RECORD("SENTENCE_DIVIDEND_SHAREHOLDERS_OF_RECORD", 140, 3),
        DIVIDEND_RECORD_DATE_GENERIC("SENTENCE_DIVIDEND_RECORD_DATE_GENERIC", 120, 2);

        private final String label;
        private final int baseScore;
        private final int rank;

        SentenceIntent(String label, int baseScore, int rank) {
            this.label = label;
            this.baseScore = baseScore;
            this.rank = rank;
        }

        String label() {
            return label;
        }

        int baseScore() {
            return baseScore;
        }

        int rank() {
            return rank;
        }
    }

    public record RecordDateCandidate(LocalDate recordDate, LocalDate filingDate, String accessionNumber, int confidenceScore) {
        public RecordDateCandidate(LocalDate recordDate, LocalDate filingDate, String accessionNumber) {
            this(recordDate, filingDate, accessionNumber, 0);
        }
    }

    public record SplitDateCandidate(LocalDate effectiveDate, LocalDate filingDate, String accessionNumber, int confidenceScore) {
    }
}

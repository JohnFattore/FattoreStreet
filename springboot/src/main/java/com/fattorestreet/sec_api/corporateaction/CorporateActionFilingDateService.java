package com.fattorestreet.sec_api.corporateaction;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.corporateaction.support.DividendDeclarationTupleExtractor;
import com.fattorestreet.sec_api.corporateaction.support.FilingTextDates;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CorporateActionFilingDateService {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionFilingDateService.class);
    private static final LocalDate T_PLUS_ONE_CUTOFF = LocalDate.of(2024, 5, 28);
    private static final int MAX_DIVIDEND_FILINGS_TO_SCAN = 250;
    private static final int MAX_SPLIT_FILINGS_TO_SCAN = 400;
    private static final int MAX_EXHIBIT_DOCS_TO_SCAN = 6;

    private static final String DATE_PATTERN = FilingTextDates.DATE_PATTERN;
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
    /** Explicit ex-dividend date in dividend press releases / 8-K body text. */
    private static final Pattern EX_DIVIDEND_DATE_LINE = Pattern.compile(
            "(?is)ex-?dividend(?:\\s+(?:date|dt))?[^\\n]{0,360}?(" + DATE_PATTERN + ")");
    private static final Pattern EX_DIVIDEND_TRADING_START = Pattern.compile(
            "(?is)(?:begin|begins|start|commence)[^\\n]{0,160}?(?:ex-?dividend|ex\\s+div)[^\\n]{0,200}?(" + DATE_PATTERN + ")");
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

    private final WebService webService;
    private final EdgarFilingDiscoveryService filingDiscoveryService;
    private final ObjectMapper mapper;
    private final DividendDeclarationTupleExtractor tupleExtractor = new DividendDeclarationTupleExtractor();

    public CorporateActionFilingDateService(
            WebService webService,
            EdgarFilingDiscoveryService filingDiscoveryService,
            ObjectMapper mapper) {
        this.webService = webService;
        this.filingDiscoveryService = filingDiscoveryService;
        this.mapper = mapper;
    }

    public List<RecordDateCandidate> fetchDividendRecordDates(Long cik) {
        return scanDividendRecordDates(cik).candidates();
    }

    public RecordDateScanResult scanDividendRecordDates(Long cik) {
        Map<LocalDate, RecordDateCandidate> candidatesByDate = new HashMap<>();
        Map<LocalDate, ExDividendDateCandidate> exDividendByDate = new HashMap<>();
        Map<String, DividendDeclarationTupleExtractor.DividendDeclaration> declarationsByKey = new HashMap<>();
        FilingSelection selection = selectCandidateFilings(cik, false, MAX_DIVIDEND_FILINGS_TO_SCAN);
        List<FilingCandidate> filings = selection.selected();
        log.info("[CIK {}] Dividend record-date scan starting: {} candidate filings", cik, filings.size());
        int processed = 0;
        int failedFilings = 0;
        for (FilingCandidate filing : filings) {
            processed++;
            if (processed == 1 || processed % 25 == 0 || processed == filings.size()) {
                log.info("[CIK {}] Dividend record-date scan progress: {}/{} filings processed ({} failed), {} unique record-date candidates, {} direct ex-date candidates",
                        cik, processed, filings.size(), failedFilings, candidatesByDate.size(), exDividendByDate.size());
            }
            try {
                String text = webService.fetchFilingDocument(cik, filing.accessionNumber(), filing.primaryDocument());
                List<ExtractedRecordDate> extracted = new ArrayList<>(
                        extractRecordDateCandidates(text, "primary:" + filing.primaryDocument()));
                extracted.addAll(extractRecordDateCandidatesFromExhibits(cik, filing.accessionNumber(), text));
                List<ExtractedRecordDate> exExtracted = new ArrayList<>(
                        extractExDividendDateCandidates(text, "primary:" + filing.primaryDocument()));
                exExtracted.addAll(extractExDividendDateCandidatesFromExhibits(cik, filing.accessionNumber(), text));
                if (extracted.isEmpty()) {
                    try {
                        String fullSubmissionText = webService.fetchFullSubmissionText(cik, filing.accessionNumber());
                        extracted.addAll(extractRecordDateCandidates(fullSubmissionText, "submission_txt"));
                    } catch (Exception ignored) {
                        // Fall back to primary/exhibit parsing only.
                    }
                }
                if (exExtracted.isEmpty()) {
                    try {
                        String fullSubmissionText = webService.fetchFullSubmissionText(cik, filing.accessionNumber());
                        exExtracted.addAll(extractExDividendDateCandidates(fullSubmissionText, "submission_txt"));
                    } catch (Exception ignored) {
                        // Ignore secondary fetch failures.
                    }
                }
                mergeExDividendCandidates(exDividendByDate, exExtracted, filing);
                mergeDeclarations(declarationsByKey, extractDeclarationsFromFiling(cik, filing, text));
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
            } catch (Exception e) {
                failedFilings++;
                log.warn("[CIK {}] Failed to process dividend filing {}: {}", cik, filing.accessionNumber(), e.getMessage());
            }
        }

        List<RecordDateCandidate> out = new ArrayList<>(candidatesByDate.values());
        out.sort(Comparator
                .comparing(RecordDateCandidate::recordDate)
                .thenComparing(RecordDateCandidate::confidenceScore, Comparator.reverseOrder())
                .thenComparing(RecordDateCandidate::filingDate));
        List<ExDividendDateCandidate> exOut = new ArrayList<>(exDividendByDate.values());
        exOut.sort(Comparator
                .comparing(ExDividendDateCandidate::exDividendDate)
                .thenComparing(ExDividendDateCandidate::confidenceScore, Comparator.reverseOrder())
                .thenComparing(ExDividendDateCandidate::filingDate, Comparator.nullsLast(Comparator.naturalOrder())));
        List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations = new ArrayList<>(declarationsByKey.values());
        declarations.sort(Comparator
                .comparing(DividendDeclarationTupleExtractor.DividendDeclaration::recordDate)
                .thenComparing(DividendDeclarationTupleExtractor.DividendDeclaration::amountPerShare));
        log.info("[CIK {}] Dividend record-date scan finished: {} record-date candidates, {} direct ex-date candidates, {} declaration tuples, {} filings failed",
                cik, out.size(), exOut.size(), declarations.size(), failedFilings);
        return new RecordDateScanResult(out, exOut, declarations, selection.discoveredByForm(), selection.selectedByForm(), selection.rejectedByForm());
    }

    /**
     * Amount-anchored declaration tuples from the primary doc and press-release exhibits.
     * Exhibit re-fetches hit the ticker-scoped SEC cache, so this adds no extra HTTP.
     */
    private List<DividendDeclarationTupleExtractor.DividendDeclaration> extractDeclarationsFromFiling(
            Long cik, FilingCandidate filing, String primaryText) {
        List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations = new ArrayList<>(
                tupleExtractor.extract(primaryText, filing.filingDate(), filing.accessionNumber()));
        int scanned = 0;
        for (String doc : extractExhibitDocumentPaths(primaryText)) {
            if (scanned >= MAX_EXHIBIT_DOCS_TO_SCAN) {
                break;
            }
            scanned++;
            try {
                String exhibitText = webService.fetchFilingDocument(cik, filing.accessionNumber(), doc);
                declarations.addAll(tupleExtractor.extract(exhibitText, filing.filingDate(), filing.accessionNumber()));
            } catch (Exception ignored) {
                // Continue scanning exhibit candidates.
            }
        }
        if (declarations.isEmpty()) {
            try {
                String fullSubmissionText = webService.fetchFullSubmissionText(cik, filing.accessionNumber());
                declarations.addAll(tupleExtractor.extract(fullSubmissionText, filing.filingDate(), filing.accessionNumber()));
            } catch (Exception ignored) {
                // Fall back to primary/exhibit parsing only.
            }
        }
        return declarations;
    }

    private void mergeDeclarations(
            Map<String, DividendDeclarationTupleExtractor.DividendDeclaration> byKey,
            List<DividendDeclarationTupleExtractor.DividendDeclaration> extracted) {
        for (DividendDeclarationTupleExtractor.DividendDeclaration declaration : extracted) {
            String key = DividendDeclarationTupleExtractor.tupleKey(declaration.amountPerShare(), declaration.recordDate());
            DividendDeclarationTupleExtractor.DividendDeclaration current = byKey.get(key);
            if (current == null
                    || declaration.confidenceScore() > current.confidenceScore()
                    || (declaration.confidenceScore() == current.confidenceScore()
                        && declaration.filingDate() != null
                        && current.filingDate() != null
                        && declaration.filingDate().isBefore(current.filingDate()))) {
                byKey.put(key, declaration);
            }
        }
    }

    private void mergeExDividendCandidates(
            Map<LocalDate, ExDividendDateCandidate> byDate,
            List<ExtractedRecordDate> extracted,
            FilingCandidate filing) {
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        for (ExtractedRecordDate row : extracted) {
            ExDividendDateCandidate candidate = new ExDividendDateCandidate(
                    row.date(), filing.filingDate(), filing.accessionNumber(), row.score());
            ExDividendDateCandidate current = byDate.get(candidate.exDividendDate());
            if (current == null || candidate.confidenceScore() > current.confidenceScore()) {
                byDate.put(candidate.exDividendDate(), candidate);
            }
        }
    }

    public List<SplitDateCandidate> fetchSplitEffectiveDates(Long cik) {
        return scanSplitEffectiveDates(cik).candidates();
    }

    public SplitDateScanResult scanSplitEffectiveDates(Long cik) {
        Map<LocalDate, SplitDateCandidate> candidatesByDate = new HashMap<>();
        Map<String, Integer> splitIntentCounts = new TreeMap<>();
        FilingSelection selection = selectCandidateFilings(cik, true, MAX_SPLIT_FILINGS_TO_SCAN);
        List<FilingCandidate> filings = selection.selected();
        log.info("[CIK {}] Split effective-date scan starting: {} candidate filings", cik, filings.size());
        int processed = 0;
        int failedFilings = 0;
        for (FilingCandidate filing : filings) {
            processed++;
            if (processed == 1 || processed % 25 == 0 || processed == filings.size()) {
                log.info("[CIK {}] Split effective-date scan progress: {}/{} filings processed ({} failed), {} unique candidates",
                        cik, processed, filings.size(), failedFilings, candidatesByDate.size());
            }
            try {
                String text = webService.fetchFilingDocument(cik, filing.accessionNumber(), filing.primaryDocument());
                List<ExtractedRecordDate> extracted = new ArrayList<>(
                        extractSplitDateCandidates(text, "primary:" + filing.primaryDocument()));
                extracted.addAll(extractSplitDateCandidatesFromExhibits(cik, filing.accessionNumber(), text));
                if (extracted.isEmpty()) {
                    try {
                        String fullSubmissionText = webService.fetchFullSubmissionText(cik, filing.accessionNumber());
                        extracted.addAll(extractSplitDateCandidates(fullSubmissionText, "submission_txt"));
                    } catch (Exception ignored) {
                        // Fall back to primary/exhibit parsing only.
                    }
                }
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
            } catch (Exception e) {
                failedFilings++;
                log.warn("[CIK {}] Failed to process split filing {}: {}", cik, filing.accessionNumber(), e.getMessage());
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
        log.info("[CIK {}] Split effective-date scan finished: {} unique candidates, {} filings failed", cik, out.size(), failedFilings);
        return new SplitDateScanResult(out, selection.discoveredByForm(), selection.selectedByForm(), selection.rejectedByForm());
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

    private List<ExtractedRecordDate> extractExDividendDateCandidates(String text, String source) {
        String searchable = toSearchableText(text);
        if (searchable.isBlank()) {
            return Collections.emptyList();
        }
        List<PatternSpec> specs = List.of(
                new PatternSpec(EX_DIVIDEND_DATE_LINE, 135, "EX_DIVIDEND_DATE_LINE"),
                new PatternSpec(EX_DIVIDEND_TRADING_START, 115, "EX_DIVIDEND_TRADING_START"));
        return extractDatedCandidates(searchable, specs, source);
    }

    private List<ExtractedRecordDate> extractExDividendDateCandidatesFromExhibits(Long cik, String accession, String filingHtml) {
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
                for (ExtractedRecordDate extracted : extractExDividendDateCandidates(exhibitText, "exhibit:" + doc)) {
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
        return FilingTextDates.parseUsDate(raw);
    }

    private String toSearchableText(String htmlOrText) {
        return FilingTextDates.toSearchableText(htmlOrText);
    }

    private FilingSelection selectCandidateFilings(Long cik, boolean splitMode, int maxToScan) {
        List<EdgarFilingDiscoveryService.FilingMeta> discovered = Collections.emptyList();
        try {
            discovered = filingDiscoveryService.discoverFilings(cik);
        } catch (Exception ignored) {
            discovered = Collections.emptyList();
        }
        Map<String, Integer> discoveredByForm = new TreeMap<>();
        Map<String, Integer> selectedByForm = new TreeMap<>();
        Map<String, Integer> rejectedByForm = new TreeMap<>();
        List<FilingCandidate> selected = new ArrayList<>();
        for (EdgarFilingDiscoveryService.FilingMeta meta : discovered) {
            String form = normalizeForm(meta.formType());
            discoveredByForm.merge(form, 1, Integer::sum);
            int score = splitMode ? splitFormScore(form) : dividendRecordFormScore(form);
            if (score <= 0) {
                rejectedByForm.merge(form, 1, Integer::sum);
                continue;
            }
            selectedByForm.merge(form, 1, Integer::sum);
            selected.add(new FilingCandidate(
                    meta.accessionNumber(),
                    meta.primaryDocument(),
                    meta.filingDate(),
                    form,
                    score));
        }
        selected.sort(Comparator
                .comparing(FilingCandidate::filingDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FilingCandidate::formScore, Comparator.reverseOrder())
                .thenComparing(FilingCandidate::accessionNumber));
        if (selected.size() > Math.max(maxToScan, 0)) {
            selected = new ArrayList<>(selected.subList(0, Math.max(maxToScan, 0)));
        }
        return new FilingSelection(selected, discoveredByForm, selectedByForm, rejectedByForm);
    }

    private int dividendRecordFormScore(String form) {
        return switch (form) {
            case "8-K", "8-K/A" -> 140;
            case "DEF 14A", "DEFA14A" -> 110;
            case "10-Q", "10-Q/A" -> 100;
            case "10-K", "10-K/A" -> 95;
            case "6-K", "20-F", "40-F" -> 90;
            default -> 0;
        };
    }

    private int splitFormScore(String form) {
        return switch (form) {
            case "8-K", "8-K/A" -> 145;
            case "10-Q", "10-Q/A" -> 105;
            case "10-K", "10-K/A" -> 100;
            case "DEF 14A", "DEFA14A" -> 95;
            case "6-K", "20-F", "40-F" -> 90;
            default -> 0;
        };
    }

    private String normalizeForm(String form) {
        if (form == null || form.isBlank()) {
            return "UNKNOWN";
        }
        return form.trim().toUpperCase(Locale.US);
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

    private void logSplitCandidateRanking(Long cik, FilingCandidate filing, List<ExtractedRecordDate> extracted) {
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
    private record FilingCandidate(
            String accessionNumber,
            String primaryDocument,
            LocalDate filingDate,
            String formType,
            int formScore) {}
    private record FilingSelection(
            List<FilingCandidate> selected,
            Map<String, Integer> discoveredByForm,
            Map<String, Integer> selectedByForm,
            Map<String, Integer> rejectedByForm) {}

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

    /** Ex-dividend date parsed directly from filing text (not derived from record date). */
    public record ExDividendDateCandidate(
            LocalDate exDividendDate,
            LocalDate filingDate,
            String accessionNumber,
            int confidenceScore) {
    }

    public record SplitDateCandidate(LocalDate effectiveDate, LocalDate filingDate, String accessionNumber, int confidenceScore) {
    }

    public record RecordDateScanResult(
            List<RecordDateCandidate> candidates,
            List<ExDividendDateCandidate> exDividendDirectCandidates,
            List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations,
            Map<String, Integer> discoveredByForm,
            Map<String, Integer> selectedByForm,
            Map<String, Integer> rejectedByForm) {
    }

    public record SplitDateScanResult(
            List<SplitDateCandidate> candidates,
            Map<String, Integer> discoveredByForm,
            Map<String, Integer> selectedByForm,
            Map<String, Integer> rejectedByForm) {
    }
}

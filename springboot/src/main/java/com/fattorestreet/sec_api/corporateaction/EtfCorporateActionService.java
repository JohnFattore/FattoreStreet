package com.fattorestreet.sec_api.corporateaction;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.corporateaction.support.EtfActionPersister;
import com.fattorestreet.sec_api.corporateaction.support.EtfAmountExtractor;
import com.fattorestreet.sec_api.corporateaction.support.EtfDateExtractor;
import com.fattorestreet.sec_api.corporateaction.support.EtfIdentityEvaluator;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EtfCorporateActionService {

    private static final Logger log = LoggerFactory.getLogger(EtfCorporateActionService.class);
    private static final int MAX_DOCUMENTS_PER_FILING = 8;
    private static final int MAX_TEXT_BYTES_PER_DOCUMENT = 1_250_000;
    private static final int MAX_SAMPLE_ROWS = 10;
    private static final int LOOKBACK_YEARS = 8;
    private static final int IDENTITY_MIN_SCORE = 2;
    private static final Pattern DIVIDEND_AMOUNT_PATTERN = Pattern.compile(
            "(?is)(?:cash\\s+)?(?:distribution|dividend)[^$\\d]{0,80}\\$\\s*([0-9]+(?:\\.[0-9]{1,6})?)");
    private static final Pattern PER_SHARE_AMOUNT_PATTERN = Pattern.compile(
            "(?is)\\$\\s*([0-9]+(?:\\.[0-9]{1,6})?)\\s+per\\s+share");
    private static final Pattern TABLE_LINE_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:dividend|distribution)[^\\n$]{0,80}\\$\\s*([0-9]+(?:\\.[0-9]{1,6})?)");
    private static final Pattern EX_DIVIDEND_DATE_SENTENCE_PATTERN = Pattern.compile(
            "(?is)(?:ex-?dividend(?:\\s+(?:date|dt))?|ex\\s+(?:date|dt))[^\\n]{0,220}");
    private static final Pattern RECORD_DATE_SENTENCE_PATTERN = Pattern.compile(
            "(?is)(?:record\\s+(?:date|dt)|shareholders?\\s+of\\s+record|holders?\\s+of\\s+record)[^\\n]{0,260}");
    private static final Pattern PAY_DATE_SENTENCE_PATTERN = Pattern.compile(
            "(?is)(?:payable\\s+(?:date|dt)|payment\\s+(?:date|dt)|pay\\s+date|pay\\s+dt)[^\\n]{0,220}");
    private static final Pattern TABLE_EX_DATE_LINE_PATTERN = Pattern.compile(
            "(?i)\\b(?:ex\\s*(?:-|\\s)?(?:dividend\\s*)?(?:date|dt)|ex-?dividend\\s+date)\\b");
    private static final Pattern TABLE_RECORD_DATE_LINE_PATTERN = Pattern.compile(
            "(?i)\\b(?:record\\s+(?:date|dt)|holders?\\s+of\\s+record|shareholders?\\s+of\\s+record)\\b");
    private static final Pattern TABLE_PAY_DATE_LINE_PATTERN = Pattern.compile(
            "(?i)\\b(?:payable\\s+(?:date|dt)|payment\\s+(?:date|dt)|pay\\s+(?:date|dt))\\b");
    private static final Pattern DISTRIBUTION_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(?:dividend|distribution|cash\\s+distribution|per\\s+share)\\b");
    private static final Pattern AMOUNT_TOKEN_PATTERN = Pattern.compile("\\$\\s*\\d+(?:\\.\\d+)?");
    private static final Pattern ANNUAL_ARTIFACT_PATTERN = Pattern.compile(
            "(?i)\\b(?:year\\s+ended|fiscal\\s+year|for\\s+the\\s+year|annual)\\b");
    private static final Pattern LABELED_DATE_PATTERN = Pattern.compile(
            "(?is)(ex(?:-|\\s)?(?:dividend)?(?:\\s+(?:date|dt))?|record\\s+(?:date|dt)|shareholders?\\s+of\\s+record|holders?\\s+of\\s+record|pay(?:able|ment)?\\s+(?:date|dt))[^\\n\\r\\d]{0,50}"
                    + "(\\d{4}-\\d{2}-\\d{2}|\\d{4}/\\d{1,2}/\\d{1,2}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}-\\d{1,2}-\\d{4}|"
                    + "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2},?\\s+\\d{4})");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern YEAR_SLASH_DATE_PATTERN = Pattern.compile("(\\d{4}/\\d{1,2}/\\d{1,2})");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})");
    private static final Pattern SLASH_SHORT_YEAR_DATE_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2})");
    private static final Pattern DASH_DATE_PATTERN = Pattern.compile("(\\d{1,2}-\\d{1,2}-\\d{4})");
    private static final Pattern US_DATE_PATTERN = Pattern.compile(
            "(?i)(Jan\\.?|January|Feb\\.?|February|Mar\\.?|March|Apr\\.?|April|May|Jun\\.?|June|Jul\\.?|July|Aug\\.?|August|Sep\\.?|Sept\\.?|September|Oct\\.?|October|Nov\\.?|November|Dec\\.?|December)\\s+\\d{1,2},?\\s+\\d{4}");

    private final ListingRepository listingRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final WebService webService;
    private final CorporateActionFilingDateService corporateActionFilingDateService;
    private final EdgarFilingDiscoveryService filingDiscoveryService;
    private final ObjectMapper objectMapper;
    private final EtfIdentityEvaluator etfIdentityEvaluator;
    private final EtfAmountExtractor etfAmountExtractor;
    private final EtfDateExtractor etfDateExtractor;
    private final EtfActionPersister etfActionPersister;

    @Autowired
    public EtfCorporateActionService(ListingRepository listingRepository,
                                     CorporateActionRepository corporateActionRepository,
                                     WebService webService,
                                     CorporateActionFilingDateService corporateActionFilingDateService,
                                     EdgarFilingDiscoveryService filingDiscoveryService,
                                     ObjectMapper objectMapper) {
        this.listingRepository = listingRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.webService = webService;
        this.corporateActionFilingDateService = corporateActionFilingDateService;
        this.filingDiscoveryService = filingDiscoveryService;
        this.objectMapper = objectMapper;
        this.etfIdentityEvaluator = new EtfIdentityEvaluator();
        this.etfAmountExtractor = new EtfAmountExtractor();
        this.etfDateExtractor = new EtfDateExtractor(corporateActionFilingDateService);
        this.etfActionPersister = new EtfActionPersister(corporateActionRepository);
    }

    EtfCorporateActionService(ListingRepository listingRepository,
                              CorporateActionRepository corporateActionRepository,
                              WebService webService,
                              CorporateActionFilingDateService corporateActionFilingDateService,
                              ObjectMapper objectMapper) {
        this(
                listingRepository,
                corporateActionRepository,
                webService,
                corporateActionFilingDateService,
                new EdgarFilingDiscoveryService(webService, objectMapper),
                objectMapper);
    }

    private static final int MIN_CONFIDENCE = 70;

    public EtfDetectionReport detectAndPersist(String ticker, Long cik) {
        EtfDetectionReport report = new EtfDetectionReport(ticker, cik);
        Optional<Listing> listingOpt = listingRepository.findByTicker(ticker);
        if (listingOpt.isEmpty()) {
            report.incrementSkip("listing_missing", null);
            report.logSummary(log);
            return report;
        }
        Listing listing = listingOpt.get();
        if (!hasResolvableIdentity(listing)) {
            log.info("[{}] ETF action detection skipped: missing series/class identity", ticker);
            report.incrementSkip("identity_missing", null);
            report.logSummary(log);
            return report;
        }

        List<FilingMeta> etfFilings = filingDiscoveryService.discoverFilings(cik).stream()
                .map(meta -> new FilingMeta(meta.accessionNumber(), meta.formType(), meta.primaryDocument(), meta.filingDate()))
                .sorted(Comparator.comparing(FilingMeta::filingDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        report.filingsConsidered = etfFilings.size();

        for (FilingMeta filingMeta : etfFilings) {
            String normalizedForm = normalizeForm(filingMeta.formType());
            report.recordFormDiscovered(normalizedForm);
            if (scoreEtfForm(normalizedForm) <= 0) {
                report.recordFormRejected(normalizedForm);
                report.incrementSkip("form_not_relevant", filingMeta.accessionNumber());
                continue;
            }
            report.recordFormEligible(normalizedForm);
            if (isStaleFiling(filingMeta.filingDate())) {
                report.incrementSkip("filing_stale", filingMeta.accessionNumber());
                continue;
            }
            List<String> candidateDocuments = resolveCandidateDocuments(cik, filingMeta);
            report.candidateDocumentsScanned += candidateDocuments.size();
            if (candidateDocuments.isEmpty()) {
                report.incrementSkip("no_candidate_documents", filingMeta.accessionNumber());
                continue;
            }

            String bestText = null;
            String bestDocument = null;
            EtfIdentityEvaluator.IdentitySignal bestIdentitySignal = null;
            for (String documentName : candidateDocuments) {
                String filingText;
                try {
                    filingText = webService.fetchFilingDocument(cik, filingMeta.accessionNumber(), documentName);
                } catch (Exception ex) {
                    continue;
                }
                report.filingsFetched++;
                if (filingText == null || filingText.isBlank()) {
                    continue;
                }
                filingText = limitText(filingText);
                EtfIdentityEvaluator.IdentitySignal signal = etfIdentityEvaluator.evaluateIdentity(
                        filingText,
                        listing,
                        ticker,
                        filingMeta.formType(),
                        documentName);
                report.recordIdentityScore(signal.score());
                if (bestIdentitySignal == null || signal.score() > bestIdentitySignal.score()) {
                    bestIdentitySignal = signal;
                    bestText = filingText;
                    bestDocument = documentName;
                }
            }

            try {
                String fullSubmissionText = webService.fetchFullSubmissionText(cik, filingMeta.accessionNumber());
                if (fullSubmissionText != null && !fullSubmissionText.isBlank()) {
                    report.filingsFetched++;
                    String normalizedSubmissionText = limitText(fullSubmissionText);
                    EtfIdentityEvaluator.IdentitySignal signal = etfIdentityEvaluator.evaluateIdentity(
                            normalizedSubmissionText,
                            listing,
                            ticker,
                            filingMeta.formType(),
                            filingMeta.accessionNumber() + ".txt");
                    report.recordIdentityScore(signal.score());
                    if (bestIdentitySignal == null || signal.score() > bestIdentitySignal.score()) {
                        bestIdentitySignal = signal;
                        bestText = normalizedSubmissionText;
                        bestDocument = filingMeta.accessionNumber() + ".txt";
                    }
                }
            } catch (Exception ignored) {
                // Full-submission fallback is optional.
            }

            if (bestText == null || bestText.isBlank()) {
                report.incrementSkip("document_fetch_failed_or_empty", filingMeta.accessionNumber());
                continue;
            }
            if (bestIdentitySignal == null || bestIdentitySignal.score() < IDENTITY_MIN_SCORE) {
                report.incrementSkip("identity_mismatch", filingMeta.accessionNumber());
                continue;
            }
            report.identityMatched++;

            EtfAmountExtractor.AmountCandidate amountCandidate = etfAmountExtractor.extractDividendAmount(bestText);
            if (amountCandidate == null || amountCandidate.amount() <= 0) {
                report.incrementSkip("amount_missing", filingMeta.accessionNumber());
                continue;
            }
            report.amountExtracted++;
            report.recordAmountSource(amountCandidate.source());

            EtfDateExtractor.EtfDateSignals dateSignals = etfDateExtractor.extractEtfDateSignals(bestText, filingMeta.filingDate());
            if (dateSignals == null) {
                report.incrementSkip("date_missing", filingMeta.accessionNumber());
                continue;
            }
            if (dateSignals.confidenceScore() < MIN_CONFIDENCE) {
                report.incrementSkip("below_confidence", filingMeta.accessionNumber());
                report.belowConfidence++;
                continue;
            }
            report.dateExtracted++;
            report.recordDateResolutionPath(dateSignals.resolutionPath());
            report.recordDateSource(dateSignals.exSource());
            report.recordDateSource(dateSignals.recordSource());
            report.recordDateSource(dateSignals.paySource());

            LocalDate effectiveDate = dateSignals.effectiveDate();
            if (effectiveDate == null) {
                report.incrementSkip("effective_date_missing", filingMeta.accessionNumber());
                continue;
            }

            EtfActionPersister.PersistResult persistResult = etfActionPersister.persistDividend(
                    ticker,
                    listing,
                    filingMeta.formType(),
                    filingMeta.accessionNumber(),
                    effectiveDate,
                    amountCandidate.amount(),
                    dateSignals);
            if (persistResult == EtfActionPersister.PersistResult.DUPLICATE) {
                report.incrementSkip("duplicate", filingMeta.accessionNumber());
                report.duplicates++;
                continue;
            }
            report.saved++;
            report.sampleCreated(
                    filingMeta.accessionNumber(),
                    filingMeta.formType(),
                    bestDocument,
                    effectiveDate,
                    amountCandidate.amount(),
                    bestIdentitySignal.score());
        }

        report.logSummary(log);
        return report;
    }

    private boolean hasResolvableIdentity(Listing listing) {
        return notBlank(listing.getSecSeriesId()) || notBlank(listing.getSecClassContractId());
    }

    private IdentitySignal evaluateIdentity(
            String filingText,
            Listing listing,
            String ticker,
            FilingMeta filingMeta,
            String documentName) {
        String normalized = normalizeText(filingText);
        int score = 0;
        List<String> matchedSignals = new ArrayList<>();

        if (containsToken(normalized, normalizeText(ticker))) {
            score += 2;
            matchedSignals.add("ticker");
        }
        if (notBlank(listing.getSecClassTicker()) && containsToken(normalized, normalizeText(listing.getSecClassTicker()))) {
            score += 3;
            matchedSignals.add("secClassTicker");
        }
        if (notBlank(listing.getSecSeriesId()) && normalized.contains(normalizeText(listing.getSecSeriesId()))) {
            score += 4;
            matchedSignals.add("secSeriesId");
        }
        if (notBlank(listing.getSecClassContractId()) && normalized.contains(normalizeText(listing.getSecClassContractId()))) {
            score += 4;
            matchedSignals.add("secClassContractId");
        }
        if (matchesNameSignal(normalized, listing.getSecSeriesName())) {
            score += 2;
            matchedSignals.add("secSeriesName");
        }
        if (matchesNameSignal(normalized, listing.getSecClassName())) {
            score += 2;
            matchedSignals.add("secClassName");
        }
        if (containsToken(normalizeText(Objects.toString(documentName, "")), normalizeText(ticker))) {
            score += 1;
            matchedSignals.add("documentTickerHint");
        }
        if (containsToken(normalizeText(Objects.toString(filingMeta.formType(), "")), "497")) {
            score += 1;
            matchedSignals.add("formHint");
        }
        return new IdentitySignal(score, matchedSignals);
    }

    private int scoreEtfForm(String form) {
        if (form == null || form.isBlank()) {
            return 0;
        }
        if (form.startsWith("497")) {
            return 140;
        }
        if (form.startsWith("485") || form.equals("N-1A") || form.equals("N-1A/A")) {
            return 130;
        }
        if (form.equals("N-CSR") || form.equals("N-CSRS")
                || form.equals("N-CSR/A") || form.equals("N-CSRS/A")) {
            return 115;
        }
        return 0;
    }

    private String normalizeForm(String form) {
        if (form == null || form.isBlank()) {
            return "UNKNOWN";
        }
        return form.trim().toUpperCase(Locale.US);
    }

    private AmountCandidate extractDividendAmount(String filingText) {
        List<AmountCandidate> candidates = new ArrayList<>();
        Matcher direct = DIVIDEND_AMOUNT_PATTERN.matcher(filingText);
        while (direct.find()) {
            Double parsed = parsePositiveDouble(direct.group(1));
            if (parsed != null && parsed < 50) {
                candidates.add(new AmountCandidate(parsed, 90, "dividend_amount_pattern"));
            }
        }

        Matcher perShare = PER_SHARE_AMOUNT_PATTERN.matcher(filingText);
        while (perShare.find()) {
            Double parsed = parsePositiveDouble(perShare.group(1));
            if (parsed != null && parsed < 50) {
                candidates.add(new AmountCandidate(parsed, 85, "per_share_pattern"));
            }
        }

        Matcher tableLine = TABLE_LINE_AMOUNT_PATTERN.matcher(filingText);
        while (tableLine.find()) {
            Double parsed = parsePositiveDouble(tableLine.group(1));
            if (parsed != null && parsed < 50) {
                candidates.add(new AmountCandidate(parsed, 75, "table_line_pattern"));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingInt(AmountCandidate::score).reversed());
        return candidates.get(0);
    }

    private EtfDateSignals extractEtfDateSignals(String filingText, LocalDate filingDate) {
        String normalizedDateText = normalizeDateExtractionText(filingText);
        List<DateCandidate> exCandidates = extractDateCandidates(
                normalizedDateText,
                EX_DIVIDEND_DATE_SENTENCE_PATTERN,
                "ex");
        List<DateCandidate> recordCandidates = extractDateCandidates(
                normalizedDateText,
                RECORD_DATE_SENTENCE_PATTERN,
                "record");
        List<DateCandidate> payCandidates = extractDateCandidates(
                normalizedDateText,
                PAY_DATE_SENTENCE_PATTERN,
                "pay");
        collectLabeledDateCandidates(normalizedDateText, exCandidates, recordCandidates, payCandidates);
        collectTableDateCandidates(normalizedDateText, exCandidates, recordCandidates, payCandidates);

        DateCandidate exBest = bestDate(exCandidates, filingDate);
        DateCandidate recordBest = bestDate(recordCandidates, filingDate);
        DateCandidate payBest = bestDate(payCandidates, filingDate);
        LocalDate exDate = exBest != null ? exBest.date : null;
        LocalDate recordDate = recordBest != null ? recordBest.date : null;
        LocalDate payDate = payBest != null ? payBest.date : null;

        LocalDate effectiveDate = null;
        int confidence = 0;
        String resolutionPath = "none";
        if (exDate != null) {
            effectiveDate = exDate;
            confidence = 95;
            resolutionPath = "ex_date";
        } else if (recordDate != null) {
            effectiveDate = corporateActionFilingDateService.computeExDividendDate(recordDate);
            if (effectiveDate != null) {
                confidence = 86;
                resolutionPath = "record_date";
            }
        } else if (payDate != null) {
            effectiveDate = previousBusinessDay(payDate);
            confidence = 62;
            resolutionPath = "pay_date_fallback";
        } else if (filingDate != null) {
            effectiveDate = previousBusinessDay(filingDate);
            confidence = 55;
            resolutionPath = "filing_date_fallback";
        }
        if (effectiveDate == null) {
            return null;
        }

        if (payDate != null) {
            confidence = Math.min(confidence + 5, 100);
        }
        return new EtfDateSignals(
                effectiveDate,
                recordDate,
                payDate,
                confidence,
                resolutionPath,
                exBest != null ? exBest.source() : null,
                recordBest != null ? recordBest.source() : null,
                payBest != null ? payBest.source() : null);
    }

    private List<DateCandidate> extractDateCandidates(String filingText, Pattern sentencePattern, String label) {
        List<DateCandidate> candidates = new ArrayList<>();
        Matcher sentenceMatcher = sentencePattern.matcher(filingText);
        while (sentenceMatcher.find()) {
            String snippet = sentenceMatcher.group();

            Matcher isoMatcher = ISO_DATE_PATTERN.matcher(snippet);
            while (isoMatcher.find()) {
                LocalDate parsed = parseIsoDate(isoMatcher.group(1));
                if (parsed != null) {
                    candidates.add(new DateCandidate(parsed, 90, label + "_sentence_iso"));
                }
            }

            Matcher usMatcher = US_DATE_PATTERN.matcher(snippet);
            while (usMatcher.find()) {
                LocalDate parsed = parseUsDate(usMatcher.group());
                if (parsed != null) {
                    candidates.add(new DateCandidate(parsed, 88, label + "_sentence_us"));
                }
            }

            Matcher slashMatcher = SLASH_DATE_PATTERN.matcher(snippet);
            while (slashMatcher.find()) {
                LocalDate parsed = parseSlashDate(slashMatcher.group(1));
                if (parsed != null) {
                    candidates.add(new DateCandidate(parsed, 82, label + "_sentence_slash"));
                }
            }

            Matcher shortYearSlashMatcher = SLASH_SHORT_YEAR_DATE_PATTERN.matcher(snippet);
            while (shortYearSlashMatcher.find()) {
                LocalDate parsed = parseSlashShortYearDate(shortYearSlashMatcher.group(1));
                if (parsed != null) {
                    candidates.add(new DateCandidate(parsed, 80, label + "_sentence_slash_short_year"));
                }
            }

            Matcher yearSlashMatcher = YEAR_SLASH_DATE_PATTERN.matcher(snippet);
            while (yearSlashMatcher.find()) {
                LocalDate parsed = parseYearSlashDate(yearSlashMatcher.group(1));
                if (parsed != null) {
                    candidates.add(new DateCandidate(parsed, 84, label + "_sentence_year_slash"));
                }
            }

            Matcher dashMatcher = DASH_DATE_PATTERN.matcher(snippet);
            while (dashMatcher.find()) {
                LocalDate parsed = parseDashDate(dashMatcher.group(1));
                if (parsed != null) {
                    candidates.add(new DateCandidate(parsed, 81, label + "_sentence_dash"));
                }
            }
        }
        return candidates;
    }

    private void collectLabeledDateCandidates(
            String filingText,
            List<DateCandidate> exCandidates,
            List<DateCandidate> recordCandidates,
            List<DateCandidate> payCandidates) {
        Matcher labeled = LABELED_DATE_PATTERN.matcher(filingText);
        while (labeled.find()) {
            String label = Objects.toString(labeled.group(1), "").toLowerCase(Locale.US);
            String rawDate = Objects.toString(labeled.group(2), "");
            LocalDate parsed = parseAnyDate(rawDate);
            if (parsed == null) {
                continue;
            }
            DateCandidate candidate = new DateCandidate(parsed, 92, "labeled_pattern");
            if (label.contains("ex")) {
                exCandidates.add(candidate);
            } else if (label.contains("record") || label.contains("holders") || label.contains("shareholder")) {
                recordCandidates.add(candidate);
            } else if (label.contains("pay")) {
                payCandidates.add(candidate);
            }
        }
    }

    private void collectTableDateCandidates(
            String filingText,
            List<DateCandidate> exCandidates,
            List<DateCandidate> recordCandidates,
            List<DateCandidate> payCandidates) {
        String[] lines = filingText.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            String window = line;
            if (i + 1 < lines.length) {
                window += " " + lines[i + 1];
            }
            if (i + 2 < lines.length) {
                window += " " + lines[i + 2];
            }
            List<LocalDate> dates = extractAllDates(window);
            if (dates.isEmpty()) {
                continue;
            }
            boolean hasDistributionSignal = DISTRIBUTION_KEYWORD_PATTERN.matcher(window).find()
                    || AMOUNT_TOKEN_PATTERN.matcher(window).find();
            boolean annualArtifact = ANNUAL_ARTIFACT_PATTERN.matcher(window).find();
            if (annualArtifact && !hasExplicitDateLabel(window)) {
                continue;
            }
            if (TABLE_EX_DATE_LINE_PATTERN.matcher(line).find()) {
                if (!hasDistributionSignal) {
                    continue;
                }
                for (LocalDate date : dates) {
                    exCandidates.add(new DateCandidate(date, 80, "table_line_ex"));
                }
            }
            if (TABLE_RECORD_DATE_LINE_PATTERN.matcher(line).find()) {
                if (!hasDistributionSignal) {
                    continue;
                }
                for (LocalDate date : dates) {
                    recordCandidates.add(new DateCandidate(date, 80, "table_line_record"));
                }
            }
            if (TABLE_PAY_DATE_LINE_PATTERN.matcher(line).find()) {
                if (!hasDistributionSignal) {
                    continue;
                }
                for (LocalDate date : dates) {
                    payCandidates.add(new DateCandidate(date, 78, "table_line_pay"));
                }
            }
        }
    }

    private boolean hasExplicitDateLabel(String text) {
        return TABLE_EX_DATE_LINE_PATTERN.matcher(text).find()
                || TABLE_RECORD_DATE_LINE_PATTERN.matcher(text).find()
                || TABLE_PAY_DATE_LINE_PATTERN.matcher(text).find();
    }

    private DateCandidate bestDate(List<DateCandidate> candidates, LocalDate filingDate) {
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((left, right) -> {
            int scoreCmp = Integer.compare(right.score(), left.score());
            if (scoreCmp != 0) {
                return scoreCmp;
            }
            if (filingDate != null) {
                long leftGap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(filingDate, left.date()));
                long rightGap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(filingDate, right.date()));
                int gapCmp = Long.compare(leftGap, rightGap);
                if (gapCmp != 0) {
                    return gapCmp;
                }
            }
            return left.date().compareTo(right.date());
        });
        return candidates.get(0);
    }

    private LocalDate parseUsDate(String dateText) {
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

    private LocalDate parseSlashDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDate parseSlashShortYearDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("M/d/uu", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDate parseYearSlashDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("uuuu/M/d", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDate parseDashDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ofPattern("M-d-uuuu", Locale.US));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDate parseAnyDate(String dateText) {
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

    private List<LocalDate> extractAllDates(String text) {
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

    private String normalizeDateExtractionText(String rawText) {
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

    private LocalDate previousBusinessDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate result = date.minusDays(1);
        while (result.getDayOfWeek() == DayOfWeek.SATURDAY || result.getDayOfWeek() == DayOfWeek.SUNDAY) {
            result = result.minusDays(1);
        }
        return result;
    }

    private Double parsePositiveDouble(String text) {
        if (text == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, FilingMeta> loadFilingMeta(Long cik) {
        Map<String, FilingMeta> filingMetaByAccession = new HashMap<>();
        String recentSubmissionsJson = webService.fetchSubmissions(cik);
        parseSubmissionJson(recentSubmissionsJson, filingMetaByAccession);

        try {
            JsonNode root = objectMapper.readTree(recentSubmissionsJson);
            JsonNode files = root.path("filings").path("files");
            if (files.isArray()) {
                for (JsonNode fileEntry : files) {
                    String fileName = fileEntry.path("name").asText(null);
                    if (fileName == null || fileName.isBlank()) {
                        continue;
                    }
                    parseSubmissionJson(webService.fetchSubmissionsFile(cik, fileName), filingMetaByAccession);
                }
            }
        } catch (Exception ignored) {
            // fallback to recent-only metadata
        }
        return filingMetaByAccession;
    }

    private void parseSubmissionJson(String json, Map<String, FilingMeta> out) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode recent = root.path("filings").path("recent");
            if (recent.isObject()) {
                JsonNode accession = recent.path("accessionNumber");
                JsonNode forms = recent.path("form");
                JsonNode primaryDocs = recent.path("primaryDocument");
                JsonNode filingDates = recent.path("filingDate");
                if (accession.isArray() && forms.isArray() && primaryDocs.isArray()) {
                    int count = accession.size();
                    for (int i = 0; i < count; i++) {
                        String accessionNumber = accession.path(i).asText(null);
                        String form = forms.path(i).asText(null);
                        String primaryDocument = primaryDocs.path(i).asText(null);
                        LocalDate filingDate = parseIsoDate(filingDates.path(i).asText(null));
                        if (!notBlank(accessionNumber) || !notBlank(primaryDocument)) {
                            continue;
                        }
                        out.putIfAbsent(accessionNumber, new FilingMeta(accessionNumber, form, primaryDocument, filingDate));
                    }
                }
            }

            JsonNode filingsArray = root.path("filings");
            if (filingsArray.isArray()) {
                for (JsonNode filing : filingsArray) {
                    String accessionNumber = filing.path("accessionNumber").asText(null);
                    String form = filing.path("form").asText(null);
                    String primaryDocument = filing.path("primaryDocument").asText(null);
                    LocalDate filingDate = parseIsoDate(filing.path("filingDate").asText(null));
                    if (!notBlank(accessionNumber) || !notBlank(primaryDocument)) {
                        continue;
                    }
                    out.putIfAbsent(accessionNumber, new FilingMeta(accessionNumber, form, primaryDocument, filingDate));
                }
            }
        } catch (Exception ignored) {
            // keep robust behavior; malformed archived bundle should not fail overall run.
        }
    }

    private LocalDate parseIsoDate(String filingDateText) {
        if (filingDateText == null || filingDateText.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(filingDateText);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean isStaleFiling(LocalDate filingDate) {
        if (filingDate == null) {
            return false;
        }
        return filingDate.isBefore(LocalDate.now().minusYears(LOOKBACK_YEARS));
    }

    private String limitText(String text) {
        if (text == null || text.length() <= MAX_TEXT_BYTES_PER_DOCUMENT) {
            return text;
        }
        return text.substring(0, MAX_TEXT_BYTES_PER_DOCUMENT);
    }

    private List<String> resolveCandidateDocuments(Long cik, FilingMeta filingMeta) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (notBlank(filingMeta.primaryDocument())) {
            ordered.add(filingMeta.primaryDocument());
        }
        try {
            String indexJson = webService.fetchFilingIndex(cik, filingMeta.accessionNumber());
            if (indexJson != null && !indexJson.isBlank()) {
                JsonNode root = objectMapper.readTree(indexJson);
                JsonNode items = root.path("directory").path("item");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String name = item.path("name").asText(null);
                        if (isLikelyDistributionDocument(name)) {
                            ordered.add(name);
                        }
                        if (ordered.size() >= MAX_DOCUMENTS_PER_FILING) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback to primary document only.
        }
        return ordered.stream().limit(MAX_DOCUMENTS_PER_FILING).toList();
    }

    private boolean isLikelyDistributionDocument(String name) {
        if (!notBlank(name)) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.US);
        boolean supportedExtension = normalized.endsWith(".htm")
                || normalized.endsWith(".html")
                || normalized.endsWith(".txt")
                || normalized.endsWith(".xml");
        if (!supportedExtension) {
            return false;
        }
        if (normalized.contains("dividend") || normalized.contains("distribution")) {
            return true;
        }
        if (normalized.contains("dist") || normalized.contains("income") || normalized.contains("capgain") || normalized.contains("capitalgain")) {
            return true;
        }
        if (normalized.contains("supplement") || normalized.contains("class")) {
            return true;
        }
        return normalized.contains("ex99")
                || normalized.contains("ex-99")
                || normalized.contains("ex101")
                || normalized.contains("ex-101")
                || normalized.contains("497");
    }

    private boolean matchesNameSignal(String normalizedText, String name) {
        if (!notBlank(name)) {
            return false;
        }
        String normalizedName = normalizeText(name);
        if (normalizedName.length() < 6) {
            return false;
        }
        if (normalizedText.contains(normalizedName)) {
            return true;
        }
        String[] words = normalizedName.split("\\s+");
        int matched = 0;
        for (String word : words) {
            if (word.length() < 4) {
                continue;
            }
            if (containsToken(normalizedText, word)) {
                matched++;
            }
        }
        return matched >= 2;
    }

    private boolean containsToken(String normalizedText, String token) {
        if (!notBlank(normalizedText) || !notBlank(token)) {
            return false;
        }
        return normalizedText.contains(token);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private record FilingMeta(String accessionNumber, String formType, String primaryDocument, LocalDate filingDate) {
    }

    private record IdentitySignal(int score, List<String> matchedSignals) {
    }

    private record AmountCandidate(Double amount, int score, String source) {
    }

    private record DateCandidate(LocalDate date, int score, String source) {
    }

    private record EtfDateSignals(
            LocalDate effectiveDate,
            LocalDate recordDate,
            LocalDate payDate,
            int confidenceScore,
            String resolutionPath,
            String exSource,
            String recordSource,
            String paySource) {
    }

    public static class EtfDetectionReport {
        private final String ticker;
        private final Long cik;
        private int filingsConsidered;
        private int filingsFetched;
        private int identityMatched;
        private int amountExtracted;
        private int dateExtracted;
        private int belowConfidence;
        private int duplicates;
        private int saved;
        private int candidateDocumentsScanned;
        private final Map<String, Integer> skipReasons = new LinkedHashMap<>();
        private final Map<String, Integer> identityScoreBuckets = new LinkedHashMap<>();
        private final Map<String, Integer> amountSourceCounts = new LinkedHashMap<>();
        private final Map<String, Integer> dateResolutionPathCounts = new LinkedHashMap<>();
        private final Map<String, Integer> dateSourceCounts = new LinkedHashMap<>();
        private final Map<String, Integer> formsDiscovered = new LinkedHashMap<>();
        private final Map<String, Integer> formsEligible = new LinkedHashMap<>();
        private final Map<String, Integer> formsRejected = new LinkedHashMap<>();
        private final List<Map<String, Object>> sampleSkips = new ArrayList<>();
        private final List<Map<String, Object>> sampleCreated = new ArrayList<>();

        public EtfDetectionReport(String ticker, Long cik) {
            this.ticker = ticker;
            this.cik = cik;
        }

        public void incrementSkip(String reason, String accession) {
            skipReasons.merge(reason, 1, Integer::sum);
            if (sampleSkips.size() < MAX_SAMPLE_ROWS) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reason", reason);
                row.put("accession", accession);
                sampleSkips.add(row);
            }
        }

        public void sampleCreated(
                String accession,
                String formType,
                String document,
                LocalDate effectiveDate,
                Double amount,
                int identityScore) {
            if (sampleCreated.size() >= MAX_SAMPLE_ROWS) {
                return;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accession", accession);
            row.put("formType", formType);
            row.put("document", document);
            row.put("effectiveDate", effectiveDate != null ? effectiveDate.toString() : null);
            row.put("amount", amount);
            row.put("identityScore", identityScore);
            sampleCreated.add(row);
        }

        public int saved() {
            return saved;
        }

        public Map<String, Integer> skipReasons() {
            return Collections.unmodifiableMap(skipReasons);
        }

        public List<Map<String, Object>> sampleSkips() {
            return Collections.unmodifiableList(sampleSkips);
        }

        public List<Map<String, Object>> sampleCreated() {
            return Collections.unmodifiableList(sampleCreated);
        }

        public int filingsConsidered() {
            return filingsConsidered;
        }

        public int filingsFetched() {
            return filingsFetched;
        }

        public int identityMatched() {
            return identityMatched;
        }

        public int amountExtracted() {
            return amountExtracted;
        }

        public int dateExtracted() {
            return dateExtracted;
        }

        public int belowConfidence() {
            return belowConfidence;
        }

        public int duplicates() {
            return duplicates;
        }

        public void recordIdentityScore(int score) {
            String bucket;
            if (score <= 1) {
                bucket = "0-1";
            } else if (score <= 3) {
                bucket = "2-3";
            } else if (score <= 5) {
                bucket = "4-5";
            } else {
                bucket = "6+";
            }
            identityScoreBuckets.merge(bucket, 1, Integer::sum);
        }

        public void recordAmountSource(String source) {
            if (source == null || source.isBlank()) {
                return;
            }
            amountSourceCounts.merge(source, 1, Integer::sum);
        }

        public void recordDateResolutionPath(String path) {
            if (path == null || path.isBlank()) {
                return;
            }
            dateResolutionPathCounts.merge(path, 1, Integer::sum);
        }

        public void recordDateSource(String source) {
            if (source == null || source.isBlank()) {
                return;
            }
            dateSourceCounts.merge(source, 1, Integer::sum);
        }

        public void recordFormDiscovered(String form) {
            if (form == null || form.isBlank()) {
                return;
            }
            formsDiscovered.merge(form, 1, Integer::sum);
        }

        public void recordFormEligible(String form) {
            if (form == null || form.isBlank()) {
                return;
            }
            formsEligible.merge(form, 1, Integer::sum);
        }

        public void recordFormRejected(String form) {
            if (form == null || form.isBlank()) {
                return;
            }
            formsRejected.merge(form, 1, Integer::sum);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ticker", ticker);
            out.put("cik", cik);
            out.put("filingsConsidered", filingsConsidered);
            out.put("filingsFetched", filingsFetched);
            out.put("identityMatched", identityMatched);
            out.put("amountExtracted", amountExtracted);
            out.put("dateExtracted", dateExtracted);
            out.put("belowConfidence", belowConfidence);
            out.put("duplicates", duplicates);
            out.put("saved", saved);
            out.put("candidateDocumentsScanned", candidateDocumentsScanned);
            out.put("identityScoreBuckets", new LinkedHashMap<>(identityScoreBuckets));
            out.put("amountSourceCounts", new LinkedHashMap<>(amountSourceCounts));
            out.put("dateResolutionPathCounts", new LinkedHashMap<>(dateResolutionPathCounts));
            out.put("dateSourceCounts", new LinkedHashMap<>(dateSourceCounts));
            out.put("formsDiscovered", new LinkedHashMap<>(formsDiscovered));
            out.put("formsEligible", new LinkedHashMap<>(formsEligible));
            out.put("formsRejected", new LinkedHashMap<>(formsRejected));
            out.put("skipReasons", new LinkedHashMap<>(skipReasons));
            out.put("sampleSkips", new ArrayList<>(sampleSkips));
            out.put("sampleCreated", new ArrayList<>(sampleCreated));
            return out;
        }

        public void logSummary(Logger logger) {
            logger.info("[{}] ETF detect summary: filingsConsidered={}, filingsFetched={}, identityMatched={}, amountExtracted={}, dateExtracted={}, belowConfidence={}, duplicates={}, saved={}, skipReasons={}",
                    ticker,
                    filingsConsidered,
                    filingsFetched,
                    identityMatched,
                    amountExtracted,
                    dateExtracted,
                    belowConfidence,
                    duplicates,
                    saved,
                    skipReasons);
            if (!sampleSkips.isEmpty()) {
                logger.debug("[{}] ETF sample skips: {}", ticker, sampleSkips);
            }
            if (!sampleCreated.isEmpty()) {
                logger.debug("[{}] ETF sample created: {}", ticker, sampleCreated);
            }
        }
    }
}

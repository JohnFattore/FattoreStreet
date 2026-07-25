package com.fattorestreet.sec_api.corporateaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.client.WebService;
import com.fattorestreet.sec_api.corporateaction.support.EtfActionPersister;
import com.fattorestreet.sec_api.corporateaction.support.EtfAmountExtractor;
import com.fattorestreet.sec_api.corporateaction.support.EtfDateExtractor;
import com.fattorestreet.sec_api.corporateaction.support.EtfIdentityEvaluator;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import com.fattorestreet.sec_api.repository.ListingRepository;
import com.fattorestreet.sec_api.util.MarketTime;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EtfCorporateActionService {

    private static final Logger log = LoggerFactory.getLogger(EtfCorporateActionService.class);
    private static final int MAX_DOCUMENTS_PER_FILING = 8;
    private static final int MAX_TEXT_BYTES_PER_DOCUMENT = 1_250_000;
    private static final int MAX_SAMPLE_ROWS = 10;
    private static final int LOOKBACK_YEARS = 8;
    private static final int IDENTITY_MIN_SCORE = 2;

    private final ListingRepository listingRepository;
    private final WebService webService;
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
        this.webService = webService;
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

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isStaleFiling(LocalDate filingDate) {
        if (filingDate == null) {
            return false;
        }
        return filingDate.isBefore(LocalDate.now(MarketTime.MARKET).minusYears(LOOKBACK_YEARS));
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

    private record FilingMeta(String accessionNumber, String formType, String primaryDocument, LocalDate filingDate) {
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

        public int candidateDocumentsScanned() {
            return candidateDocumentsScanned;
        }

        public Map<String, Integer> identityScoreBuckets() {
            return Collections.unmodifiableMap(identityScoreBuckets);
        }

        public Map<String, Integer> amountSourceCounts() {
            return Collections.unmodifiableMap(amountSourceCounts);
        }

        public Map<String, Integer> dateResolutionPathCounts() {
            return Collections.unmodifiableMap(dateResolutionPathCounts);
        }

        public Map<String, Integer> dateSourceCounts() {
            return Collections.unmodifiableMap(dateSourceCounts);
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

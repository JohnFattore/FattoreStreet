package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.model.FilingExtraction;
import com.fattorestreet.sec_api.repository.FilingExtractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence layer for per-accession filing extraction results. Filings are immutable, so a
 * stored section is authoritative for its extractor version: scans reuse it at zero HTTP cost
 * and only fetch documents for accessions without a current-version section. Bump the section's
 * extractor version constant whenever its extraction logic (patterns, scoring, exhibits walk)
 * changes so stale results are re-extracted.
 */
@Component
public class FilingExtractionStore {

    public static final int DIVIDEND_EXTRACTOR_VERSION = 1;
    public static final int SPLIT_EXTRACTOR_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(FilingExtractionStore.class);

    private final FilingExtractionRepository filingExtractionRepository;
    private final ObjectMapper mapper;

    public FilingExtractionStore(FilingExtractionRepository filingExtractionRepository, ObjectMapper mapper) {
        this.filingExtractionRepository = filingExtractionRepository;
        this.mapper = mapper;
    }

    public record DatedScore(LocalDate date, int score) {
    }

    public record DividendExtraction(
            DatedScore bestRecordDate,
            List<DatedScore> exDateCandidates,
            List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations) {
    }

    public record SplitExtraction(DatedScore bestSplitDate) {
    }

    /** All persisted extractions for a CIK, keyed by accession number. */
    public Map<String, FilingExtraction> loadByCik(Long cik) {
        Map<String, FilingExtraction> byAccession = new HashMap<>();
        for (FilingExtraction row : filingExtractionRepository.findByCik(cik)) {
            byAccession.put(row.getAccessionNumber(), row);
        }
        return byAccession;
    }

    /** Stored dividend section, or empty when absent, version-stale, or unparseable. */
    public Optional<DividendExtraction> dividendSection(FilingExtraction row) {
        if (row == null || row.getDividendExtractorVersion() == null
                || row.getDividendExtractorVersion() != DIVIDEND_EXTRACTOR_VERSION) {
            return Optional.empty();
        }
        try {
            DatedScore bestRecordDate = row.getBestRecordDate() == null
                    ? null
                    : new DatedScore(row.getBestRecordDate(),
                            row.getBestRecordDateScore() == null ? 0 : row.getBestRecordDateScore());
            return Optional.of(new DividendExtraction(
                    bestRecordDate,
                    parseDatedScores(row.getExDateCandidatesJson()),
                    parseDeclarations(row)));
        } catch (Exception e) {
            log.warn("Unparseable dividend extraction for accession {}; re-extracting: {}",
                    row.getAccessionNumber(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Stored split section, or empty when absent or version-stale. */
    public Optional<SplitExtraction> splitSection(FilingExtraction row) {
        if (row == null || row.getSplitExtractorVersion() == null
                || row.getSplitExtractorVersion() != SPLIT_EXTRACTOR_VERSION) {
            return Optional.empty();
        }
        DatedScore bestSplitDate = row.getBestSplitDate() == null
                ? null
                : new DatedScore(row.getBestSplitDate(),
                        row.getBestSplitDateScore() == null ? 0 : row.getBestSplitDateScore());
        return Optional.of(new SplitExtraction(bestSplitDate));
    }

    public FilingExtraction saveDividendSection(
            Long cik, String accessionNumber, LocalDate filingDate, String formType,
            FilingExtraction existing, DividendExtraction section) {
        FilingExtraction row = prepareRow(cik, accessionNumber, filingDate, formType, existing);
        row.setDividendExtractorVersion(DIVIDEND_EXTRACTOR_VERSION);
        row.setBestRecordDate(section.bestRecordDate() == null ? null : section.bestRecordDate().date());
        row.setBestRecordDateScore(section.bestRecordDate() == null ? null : section.bestRecordDate().score());
        row.setExDateCandidatesJson(writeDatedScores(section.exDateCandidates()));
        row.setDeclarationsJson(writeDeclarations(section.declarations()));
        return saveQuietly(row);
    }

    public FilingExtraction saveSplitSection(
            Long cik, String accessionNumber, LocalDate filingDate, String formType,
            FilingExtraction existing, SplitExtraction section) {
        FilingExtraction row = prepareRow(cik, accessionNumber, filingDate, formType, existing);
        row.setSplitExtractorVersion(SPLIT_EXTRACTOR_VERSION);
        row.setBestSplitDate(section.bestSplitDate() == null ? null : section.bestSplitDate().date());
        row.setBestSplitDateScore(section.bestSplitDate() == null ? null : section.bestSplitDate().score());
        return saveQuietly(row);
    }

    private FilingExtraction prepareRow(
            Long cik, String accessionNumber, LocalDate filingDate, String formType, FilingExtraction existing) {
        FilingExtraction row = existing != null ? existing : new FilingExtraction();
        row.setCik(cik);
        row.setAccessionNumber(accessionNumber);
        row.setFilingDate(filingDate);
        row.setFormType(formType);
        row.setExtractedAt(LocalDateTime.now());
        return row;
    }

    /** Persist best-effort: a failed cache write only costs a re-extraction next run. */
    private FilingExtraction saveQuietly(FilingExtraction row) {
        try {
            return filingExtractionRepository.save(row);
        } catch (DataIntegrityViolationException e) {
            log.warn("Skipping filing-extraction cache write for accession {}: {}",
                    row.getAccessionNumber(),
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
            return row;
        }
    }

    private String writeDatedScores(List<DatedScore> scores) {
        ArrayNode array = mapper.createArrayNode();
        if (scores != null) {
            for (DatedScore score : scores) {
                if (score.date() == null) {
                    continue;
                }
                ObjectNode node = array.addObject();
                node.put("date", score.date().toString());
                node.put("score", score.score());
            }
        }
        return array.toString();
    }

    private List<DatedScore> parseDatedScores(String json) {
        List<DatedScore> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        JsonNode array = mapper.readTree(json);
        for (JsonNode node : array) {
            LocalDate date = parseDate(node, "date");
            if (date != null) {
                out.add(new DatedScore(date, node.path("score").asInt(0)));
            }
        }
        return out;
    }

    private String writeDeclarations(List<DividendDeclarationTupleExtractor.DividendDeclaration> declarations) {
        ArrayNode array = mapper.createArrayNode();
        if (declarations != null) {
            for (DividendDeclarationTupleExtractor.DividendDeclaration declaration : declarations) {
                ObjectNode node = array.addObject();
                node.put("amountPerShare", declaration.amountPerShare());
                putDate(node, "declarationDate", declaration.declarationDate());
                putDate(node, "recordDate", declaration.recordDate());
                putDate(node, "payableDate", declaration.payableDate());
                putDate(node, "exDate", declaration.exDate());
                node.put("confidenceScore", declaration.confidenceScore());
            }
        }
        return array.toString();
    }

    /** Filing date and accession are row-level fields; the tuples are rehydrated with them. */
    private List<DividendDeclarationTupleExtractor.DividendDeclaration> parseDeclarations(FilingExtraction row) {
        List<DividendDeclarationTupleExtractor.DividendDeclaration> out = new ArrayList<>();
        String json = row.getDeclarationsJson();
        if (json == null || json.isBlank()) {
            return out;
        }
        JsonNode array = mapper.readTree(json);
        for (JsonNode node : array) {
            out.add(new DividendDeclarationTupleExtractor.DividendDeclaration(
                    node.path("amountPerShare").asDouble(),
                    parseDate(node, "declarationDate"),
                    parseDate(node, "recordDate"),
                    parseDate(node, "payableDate"),
                    parseDate(node, "exDate"),
                    row.getFilingDate(),
                    row.getAccessionNumber(),
                    node.path("confidenceScore").asInt(0)));
        }
        return out;
    }

    private void putDate(ObjectNode node, String field, LocalDate date) {
        if (date != null) {
            node.put(field, date.toString());
        }
    }

    private LocalDate parseDate(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.asText());
        } catch (Exception e) {
            return null;
        }
    }
}

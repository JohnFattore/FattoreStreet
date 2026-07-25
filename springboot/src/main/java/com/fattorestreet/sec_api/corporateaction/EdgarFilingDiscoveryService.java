package com.fattorestreet.sec_api.corporateaction;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fattorestreet.sec_api.client.WebService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Canonical source for SEC company-submissions filing metadata: accession, form, primary document,
 * and filing date from {@code data.sec.gov/submissions/} (main CIK JSON plus optional archive shards,
 * limited by {@code maxSubmissionFilesToScan}). Does not download filing bodies.
 */
@Service
public class EdgarFilingDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(EdgarFilingDiscoveryService.class);

    private static final int DEFAULT_MAX_SUBMISSION_FILES_TO_SCAN = 48;

    private final WebService webService;
    private final ObjectMapper objectMapper;

    public EdgarFilingDiscoveryService(WebService webService, ObjectMapper objectMapper) {
        this.webService = webService;
        this.objectMapper = objectMapper;
    }

    public List<FilingMeta> discoverFilings(Long cik) {
        return discoverFilings(cik, DEFAULT_MAX_SUBMISSION_FILES_TO_SCAN);
    }

    public List<FilingMeta> discoverFilings(Long cik, int maxSubmissionFilesToScan) {
        Map<String, FilingMeta> byAccession = new LinkedHashMap<>();
        String recent = webService.fetchSubmissions(cik);
        parseSubmissionJson(recent, byAccession);
        scanSubmissionArchives(cik, recent, byAccession, maxSubmissionFilesToScan);
        return byAccession.values().stream()
                .sorted(Comparator.comparing(FilingMeta::filingDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void scanSubmissionArchives(
            Long cik,
            String recentSubmissionJson,
            Map<String, FilingMeta> out,
            int maxSubmissionFilesToScan) {
        if (recentSubmissionJson == null || recentSubmissionJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(recentSubmissionJson);
            JsonNode files = root.path("filings").path("files");
            if (!files.isArray()) {
                return;
            }
            int scanned = 0;
            for (JsonNode fileEntry : files) {
                if (scanned >= Math.max(maxSubmissionFilesToScan, 0)) {
                    break;
                }
                String name = fileEntry.path("name").asString(null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                scanned++;
                try {
                    parseSubmissionJson(webService.fetchSubmissionsFile(cik, name), out);
                } catch (RuntimeException ex) {
                    // Keep discovery resilient if an archive bundle is unavailable.
                    log.warn("Failed to fetch submission archive bundle '{}' for CIK {}: {}",
                            name, cik, ex.toString());
                }
            }
        } catch (JacksonException ex) {
            // Keep discovery resilient if recent submissions payload is malformed.
            log.warn("Failed to parse recent submissions payload for CIK {}: {}", cik, ex.getMessage());
        }
    }

    private void parseSubmissionJson(String json, Map<String, FilingMeta> out) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode recent = root.path("filings").path("recent");
            if (recent.isObject()) {
                parseRecentArrays(recent, out);
            }
            JsonNode filingsArray = root.path("filings");
            if (filingsArray.isArray()) {
                for (JsonNode filing : filingsArray) {
                    addFilingRow(
                            out,
                            filing.path("accessionNumber").asString(null),
                            filing.path("form").asString(null),
                            filing.path("primaryDocument").asString(null),
                            parseIsoDate(filing.path("filingDate").asString(null)));
                }
            }
        } catch (JacksonException ex) {
            // Keep discovery resilient if a bundle has malformed JSON.
            log.warn("Failed to parse submission JSON ({} chars): {}", json.length(), ex.getMessage());
        }
    }

    private void parseRecentArrays(JsonNode recent, Map<String, FilingMeta> out) {
        JsonNode accession = recent.path("accessionNumber");
        JsonNode forms = recent.path("form");
        JsonNode primaryDocs = recent.path("primaryDocument");
        JsonNode filingDates = recent.path("filingDate");
        if (!accession.isArray() || !forms.isArray() || !primaryDocs.isArray()) {
            return;
        }
        int count = accession.size();
        for (int i = 0; i < count; i++) {
            addFilingRow(
                    out,
                    accession.path(i).asString(null),
                    forms.path(i).asString(null),
                    primaryDocs.path(i).asString(null),
                    parseIsoDate(filingDates.path(i).asString(null)));
        }
    }

    private void addFilingRow(
            Map<String, FilingMeta> out,
            String accessionNumber,
            String formType,
            String primaryDocument,
            LocalDate filingDate) {
        if (accessionNumber == null || accessionNumber.isBlank()) {
            return;
        }
        if (primaryDocument == null || primaryDocument.isBlank()) {
            return;
        }
        out.putIfAbsent(accessionNumber, new FilingMeta(
                accessionNumber,
                normalizeForm(formType),
                primaryDocument,
                filingDate));
    }

    private LocalDate parseIsoDate(String filingDateText) {
        if (filingDateText == null || filingDateText.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(filingDateText);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeForm(String formType) {
        if (formType == null) {
            return "";
        }
        return formType.trim().toUpperCase(Locale.US);
    }

    public record FilingMeta(String accessionNumber, String formType, String primaryDocument, LocalDate filingDate) {
    }
}

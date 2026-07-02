package com.fattorestreet.sec_api.filing;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.model.FilingSummary;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.AssetRepository;
import com.fattorestreet.sec_api.repository.FilingSummaryRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FilingSummaryService {

    private static final Logger log = LoggerFactory.getLogger(FilingSummaryService.class);
    private static final String SEC_SUBMISSIONS_URL = "https://data.sec.gov/submissions/CIK%s.json";
    private static final String SEC_ARCHIVES_URL = "https://www.sec.gov/Archives/edgar/data/%d/%s/%s";
    private static final int MAX_INPUT_WORDS = 1800;

    private static final Pattern ITEM7_START = Pattern.compile(
            "(?i)item\\s*7\\.?\\s*(?:&#\\d+;|\\W)*management[''\\u2019]?s\\s+discussion",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM8_START = Pattern.compile(
            "(?i)item\\s*8\\.?\\s*(?:&#\\d+;|\\W)*financial\\s+statements",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(\\d+);");
    private static final Pattern WHITESPACE_RUNS = Pattern.compile("\\s{2,}");

    private static final String SUMMARY_SYSTEM_PROMPT =
            "You are a financial analyst. Summarize the following 10-K Management's Discussion and Analysis section " +
            "in approximately 200 words. Focus on key business trends, revenue drivers, risks, and management's " +
            "outlook. Be concise and factual.";

    private final FilingSummaryRepository filingSummaryRepository;
    private final AssetRepository assetRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpEntity<String> secEntity;

    @Value("${llm.server.url:http://localhost:8081}")
    private String llmServerUrl;

    public FilingSummaryService(FilingSummaryRepository filingSummaryRepository,
                                AssetRepository assetRepository,
                                @Value("${sec.contact-email}") String secContactEmail) {
        this.filingSummaryRepository = filingSummaryRepository;
        this.assetRepository = assetRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", secContactEmail);
        this.secEntity = new HttpEntity<>(headers);
    }

    public Map<String, Object> summarizeAll() {
        List<Asset> assets = assetRepository.findByIsFund(false);
        log.info("Summarizing 10-K filings for {} non-fund assets", assets.size());

        int processed = 0;
        int skipped = 0;
        int errors = 0;
        int noFilings = 0;

        for (Asset asset : assets) {
            String ticker = asset.getListings().isEmpty() ? "CIK" + asset.getCik()
                    : asset.getListings().get(0).getTicker();
            try {
                int count = summarizeForAsset(asset, ticker);
                if (count > 0) {
                    processed += count;
                } else {
                    noFilings++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[{}] Error: {}", ticker, e.getMessage());
                errors++;
            }
        }

        log.info("Summarization complete. Processed: {}, No filings: {}, Errors: {}", processed, noFilings, errors);
        return Map.of(
                "filingsSummarized", processed,
                "assetsWithNoFilings", noFilings,
                "assetsSkipped", skipped,
                "errors", errors
        );
    }

    public Map<String, Object> summarizeTicker(String ticker, Asset asset) throws Exception {
        int count = summarizeForAsset(asset, ticker);
        return Map.of("ticker", ticker, "filingsSummarized", count);
    }

    private int summarizeForAsset(Asset asset, String ticker) throws Exception {
        String paddedCik = String.format("%010d", asset.getCik());
        String submissionsUrl = String.format(SEC_SUBMISSIONS_URL, paddedCik);

        ResponseEntity<String> response = restTemplate.exchange(
                submissionsUrl, HttpMethod.GET, secEntity, String.class);
        Thread.sleep(100);

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode recent = root.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        JsonNode filingDates = recent.path("filingDate");
        JsonNode accessionNumbers = recent.path("accessionNumber");
        JsonNode primaryDocuments = recent.path("primaryDocument");

        int summarized = 0;

        for (int i = 0; i < forms.size(); i++) {
            if (!"10-K".equals(forms.get(i).asText())) continue;

            String accession = accessionNumbers.get(i).asText();
            if (filingSummaryRepository.existsByAccessionNumber(accession)) continue;

            String filingDate = filingDates.get(i).asText();
            String primaryDoc = primaryDocuments.get(i).asText();
            String accessionPath = accession.replace("-", "");

            String filingUrl = String.format(SEC_ARCHIVES_URL, asset.getCik(), accessionPath, primaryDoc);
            log.info("[{}] Fetching 10-K filed {} from {}", ticker, filingDate, filingUrl);

            try {
                String html = restTemplate.exchange(filingUrl, HttpMethod.GET, secEntity, String.class).getBody();
                Thread.sleep(100);

                String mdaText = extractMda(html);
                if (mdaText == null || mdaText.length() < 200) {
                    log.warn("[{}] Could not extract MD&A from filing {}", ticker, accession);
                    continue;
                }

                String truncated = truncateToWords(mdaText, MAX_INPUT_WORDS);
                String summary = callLlm(truncated);

                if (summary == null || summary.isBlank()) {
                    log.warn("[{}] LLM returned empty summary for {}", ticker, accession);
                    continue;
                }

                FilingSummary fs = new FilingSummary();
                fs.setAsset(asset);
                fs.setTicker(ticker);
                fs.setFilingDate(LocalDate.parse(filingDate));
                fs.setAccessionNumber(accession);
                fs.setRawText(truncated);
                fs.setSummary(summary);
                filingSummaryRepository.save(fs);

                log.info("[{}] Saved summary for filing {} ({} words)", ticker, accession,
                        summary.split("\\s+").length);
                summarized++;

            } catch (Exception e) {
                log.warn("[{}] Failed to process filing {}: {}", ticker, accession, e.getMessage());
            }
        }

        return summarized;
    }

    String extractMda(String html) {
        if (html == null) return null;

        String text = HTML_TAG.matcher(html).replaceAll(" ");
        text = text.replace("&nbsp;", " ").replace("&amp;", "&")
                   .replace("&lt;", "<").replace("&gt;", ">");
        Matcher entityMatcher = NUMERIC_ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (entityMatcher.find()) {
            int codePoint = Integer.parseInt(entityMatcher.group(1));
            entityMatcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) codePoint)));
        }
        entityMatcher.appendTail(sb);
        text = sb.toString();
        text = WHITESPACE_RUNS.matcher(text).replaceAll(" ").trim();

        Matcher startMatcher = ITEM7_START.matcher(text);
        String mda = null;
        while (startMatcher.find()) {
            int start = startMatcher.start();

            Matcher endMatcher = ITEM8_START.matcher(text);
            int end = text.length();
            if (endMatcher.find(start + 1)) {
                end = endMatcher.start();
            }

            String candidate = text.substring(start, end).trim();
            if (candidate.length() >= 1000) {
                mda = candidate;
                break;
            }
        }
        return mda;
    }

    String truncateToWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;
        return String.join(" ", Arrays.copyOf(words, maxWords));
    }

    String callLlm(String inputText) {
        String url = llmServerUrl + "/v1/chat/completions";

        Map<String, Object> request = Map.of(
                "model", "qwen2.5",
                "messages", List.of(
                        Map.of("role", "system", "content", SUMMARY_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", inputText)
                ),
                "max_tokens", 350,
                "temperature", 0.3
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return null;
        }
    }
}

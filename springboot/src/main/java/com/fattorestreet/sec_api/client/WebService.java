package com.fattorestreet.sec_api.client;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Service
public class WebService {
    private static final Logger log = LoggerFactory.getLogger(WebService.class);

    private final RestTemplate restTemplate;
    private final HttpEntity<String> secEntity;
    private final int secRetryMaxAttempts;
    private final long secRetryBaseBackoffMs;
    private final long secMinIntervalMs;
    private long nextSecRequestEpochMs;
    private final ThreadLocal<Map<String, String>> secTickerScopedCache = new ThreadLocal<>();

    private static final String CACHE_SUBMISSIONS_MAIN = "__SUBMISSIONS_MAIN__";
    private static final String CACHE_FILING_INDEX_JSON = "__INDEX_JSON__";
    private static final String CACHE_FULL_SUBMISSION_TXT = "__FULL_TXT__";

    // constructor
    @Autowired
    public WebService(
            @Value("${sec.contact-email}") String secContactEmail,
            @Value("${sec.http.connect-timeout-ms:15000}") int secConnectTimeoutMs,
            @Value("${sec.http.read-timeout-ms:120000}") int secReadTimeoutMs,
            @Value("${sec.http.retry-max-attempts:3}") int secRetryMaxAttempts,
            @Value("${sec.http.retry-base-backoff-ms:1000}") long secRetryBaseBackoffMs,
            @Value("${sec.http.min-interval-ms:250}") long secMinIntervalMs) {
        this(new RestTemplate(),
                secContactEmail,
                secConnectTimeoutMs,
                secReadTimeoutMs,
                secRetryMaxAttempts,
                secRetryBaseBackoffMs,
                secMinIntervalMs);
    }

    WebService(
            RestTemplate restTemplate,
            String secContactEmail,
            int secConnectTimeoutMs,
            int secReadTimeoutMs,
            int secRetryMaxAttempts,
            long secRetryBaseBackoffMs,
            long secMinIntervalMs) {
        this.restTemplate = restTemplate;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(secConnectTimeoutMs, 1000));
        requestFactory.setReadTimeout(Math.max(secReadTimeoutMs, 1000));
        this.restTemplate.setRequestFactory(requestFactory);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", secContactEmail); // required by SEC

        // Save entity with headers
        this.secEntity = new HttpEntity<>(headers);
        this.secRetryMaxAttempts = Math.max(secRetryMaxAttempts, 1);
        this.secRetryBaseBackoffMs = Math.max(secRetryBaseBackoffMs, 0);
        this.secMinIntervalMs = Math.max(secMinIntervalMs, 0);
        this.nextSecRequestEpochMs = 0L;
    }

    /**
     * Enables an in-memory cache for SEC HTTP response bodies for the remainder of the current thread's
     * ticker load. Call {@link #endSecTickerScopedCache()} in a {@code finally} block to avoid leaks.
     */
    public void beginSecTickerScopedCache() {
        secTickerScopedCache.set(new HashMap<>());
    }

    /** Disables and clears the current thread's SEC ticker-scoped cache. */
    public void endSecTickerScopedCache() {
        secTickerScopedCache.remove();
    }

    private Map<String, String> getSecTickerScopedCache() {
        return secTickerScopedCache.get();
    }

    private String normalizeAccession(String accessionNumber) {
        if (accessionNumber == null) {
            return "";
        }
        return accessionNumber.replace("-", "").trim();
    }

    private String cacheKey(Long cik, String normalizedAccession, String resourceId) {
        String safeCik = cik == null ? "" : String.valueOf(cik);
        String safeAccession = normalizedAccession == null ? "" : normalizedAccession;
        String safeResource = resourceId == null ? "" : resourceId;
        return safeCik + "|" + safeAccession + "|" + safeResource;
    }

    // public method
    public Map<Integer, Map<String, String>> fetchSecTickers() {
        String url = "https://www.sec.gov/files/company_tickers.json";
        ResponseEntity<Map<Integer, Map<String, String>>> response = executeSecRequestWithRetry(
                "SEC ticker index",
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        new ParameterizedTypeReference<Map<Integer, Map<String, String>>>() {
                        }));
        return response.getBody();
    }

    public String fetchSecMutualFundTickers() {
        String url = "https://www.sec.gov/files/company_tickers_mf.json";
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC mutual fund ticker index",
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        return response.getBody();
    }

    public String fetchFinancials(Long cik) {
        String paddedCik = String.format("%010d", cik);
        String url = "https://data.sec.gov/api/xbrl/companyfacts/CIK" + paddedCik + ".json";
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC companyfacts CIK " + paddedCik,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        return response.getBody();
    }

    public String fetchCompanyConcept(Long cik, String taxonomy, String tag) {
        String paddedCik = String.format("%010d", cik);
        String safeTaxonomy = taxonomy == null ? "" : taxonomy.trim();
        String safeTag = tag == null ? "" : tag.trim();
        String url = "https://data.sec.gov/api/xbrl/companyconcept/CIK"
                + paddedCik + "/" + safeTaxonomy + "/" + safeTag + ".json";
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC companyconcept CIK " + paddedCik + " " + safeTaxonomy + ":" + safeTag,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        return response.getBody();
    }

    public String fetchSubmissions(Long cik) {
        String paddedCik = String.format("%010d", cik);
        Map<String, String> cache = getSecTickerScopedCache();
        if (cache != null) {
            String key = cacheKey(cik, "", CACHE_SUBMISSIONS_MAIN);
            String cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String url = "https://data.sec.gov/submissions/CIK" + paddedCik + ".json";
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC submissions CIK " + paddedCik,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        String body = response.getBody();
        if (cache != null && body != null) {
            cache.put(cacheKey(cik, "", CACHE_SUBMISSIONS_MAIN), body);
        }
        return body;
    }

    public String fetchSubmissionsFile(Long cik, String fileName) {
        String paddedCik = String.format("%010d", cik);
        Map<String, String> cache = getSecTickerScopedCache();
        if (cache != null) {
            String key = cacheKey(cik, "", fileName);
            String cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String url = "https://data.sec.gov/submissions/CIK" + paddedCik + "-" + fileName;
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC submissions archive CIK " + paddedCik + " file " + fileName,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        String body = response.getBody();
        if (cache != null && body != null) {
            cache.put(cacheKey(cik, "", fileName), body);
        }
        return body;
    }

    public String fetchFilingDocument(Long cik, String accessionNumber, String primaryDocument) {
        String cikNoPadding = String.valueOf(cik);
        String accessionNoDashes = normalizeAccession(accessionNumber);
        Map<String, String> cache = getSecTickerScopedCache();
        if (cache != null) {
            String key = cacheKey(cik, accessionNoDashes, primaryDocument);
            String cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String url = "https://www.sec.gov/Archives/edgar/data/" + cikNoPadding + "/"
                + accessionNoDashes + "/" + primaryDocument;
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC filing document " + accessionNumber + "/" + primaryDocument,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        String body = response.getBody();
        if (cache != null && body != null) {
            cache.put(cacheKey(cik, accessionNoDashes, primaryDocument), body);
        }
        return body;
    }

    public String fetchFilingIndex(Long cik, String accessionNumber) {
        String cikNoPadding = String.valueOf(cik);
        String accessionNoDashes = normalizeAccession(accessionNumber);
        Map<String, String> cache = getSecTickerScopedCache();
        if (cache != null) {
            String key = cacheKey(cik, accessionNoDashes, CACHE_FILING_INDEX_JSON);
            String cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String url = "https://www.sec.gov/Archives/edgar/data/" + cikNoPadding + "/"
                + accessionNoDashes + "/index.json";
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC filing index " + accessionNumber,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        String body = response.getBody();
        if (cache != null && body != null) {
            cache.put(cacheKey(cik, accessionNoDashes, CACHE_FILING_INDEX_JSON), body);
        }
        return body;
    }

    public String fetchFullSubmissionText(Long cik, String accessionNumber) {
        String cikNoPadding = String.valueOf(cik);
        String accessionNoDashes = normalizeAccession(accessionNumber);
        Map<String, String> cache = getSecTickerScopedCache();
        if (cache != null) {
            String key = cacheKey(cik, accessionNoDashes, CACHE_FULL_SUBMISSION_TXT);
            String cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String url = "https://www.sec.gov/Archives/edgar/data/" + cikNoPadding + "/"
                + accessionNoDashes + "/" + accessionNoDashes + ".txt";
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC full submission text " + accessionNumber,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        String body = response.getBody();
        if (cache != null && body != null) {
            cache.put(cacheKey(cik, accessionNoDashes, CACHE_FULL_SUBMISSION_TXT), body);
        }
        return body;
    }

    public String fetchXbrlFrames(String taxonomy, String tag, String unit, String period) {
        String url = String.format("https://data.sec.gov/api/xbrl/frames/%s/%s/%s/%s.json", taxonomy, tag, unit,
                period);
        ResponseEntity<String> response = executeSecRequestWithRetry(
                "SEC xbrl frame " + taxonomy + "/" + tag + "/" + period,
                () -> restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        secEntity,
                        String.class));
        return response.getBody();
    }

    private <T> ResponseEntity<T> executeSecRequestWithRetry(
            String requestLabel,
            SecRequestOperation<T> operation) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= secRetryMaxAttempts; attempt++) {
            try {
                throttleSecRequestRate();
                return operation.execute();
            } catch (Exception ex) {
                lastError = ex;
                boolean retryable = isRetryableSecException(ex);
                boolean canRetry = retryable && attempt < secRetryMaxAttempts;
                if (!canRetry) {
                    break;
                }
                long backoffMs = secRetryBaseBackoffMs * attempt;
                log.warn("SEC request '{}' failed on attempt {}/{} ({}). Retrying in {} ms",
                        requestLabel, attempt, secRetryMaxAttempts, explainException(ex), backoffMs);
                sleepQuietly(backoffMs);
            }
        }
        if (lastError instanceof RuntimeException runtimeEx) {
            throw runtimeEx;
        }
        throw new RuntimeException("SEC request failed after retries: " + requestLabel, lastError);
    }

    private boolean isRetryableSecException(Exception ex) {
        if (ex instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.value() == 429 || status.is5xxServerError();
        }
        if (ex instanceof ResourceAccessException resourceAccessException) {
            Throwable cause = resourceAccessException.getCause();
            return cause instanceof SocketTimeoutException || cause instanceof ConnectException || cause == null;
        }
        return false;
    }

    private String explainException(Exception ex) {
        if (ex instanceof RestClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void throttleSecRequestRate() {
        if (secMinIntervalMs <= 0) {
            return;
        }
        long waitMs;
        synchronized (this) {
            long now = System.currentTimeMillis();
            waitMs = Math.max(0L, nextSecRequestEpochMs - now);
            long base = Math.max(now, nextSecRequestEpochMs);
            nextSecRequestEpochMs = base + secMinIntervalMs;
        }
        sleepQuietly(waitMs);
    }

    @FunctionalInterface
    private interface SecRequestOperation<T> {
        ResponseEntity<T> execute();
    }

    public List<String> getSnP500List() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new ClassPathResource("constituents.csv").getInputStream(),
                        StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(line -> line.split(",", -1)[0]) // first column (ticker)
                    .filter(ticker -> !ticker.equals("Symbol")) // optional: skip header
                    .filter(ticker -> !ticker.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load S&P 500 list", e);
        }
    }
}

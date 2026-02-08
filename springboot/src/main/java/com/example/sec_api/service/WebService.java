package com.example.sec_api.service;

import java.util.*;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import java.util.stream.Collectors;

@Service
public class WebService {

    private final RestTemplate restTemplate;
    private final HttpEntity<String> secEntity;
    private final HttpEntity<String> entity;

    // constructor
    public WebService() {
        this.restTemplate = new RestTemplate();

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "johnefattore@gmail.com"); // required by SEC

        // Save entity with headers
        this.secEntity = new HttpEntity<>(headers);
        this.entity = new HttpEntity<>(new HttpHeaders());
    }

    // public method
    public Map<Integer, Map<String, String>> fetchSecTickers() {
        String url = "https://www.sec.gov/files/company_tickers.json";
        ResponseEntity<Map<Integer, Map<String, String>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                secEntity,
                new ParameterizedTypeReference<Map<Integer, Map<String, String>>>() {
                });
        return response.getBody();
    }

    public String fetchFinancials(Long cik) {
        String paddedCik = String.format("%010d", cik);
        String url = "https://data.sec.gov/api/xbrl/companyfacts/CIK" + paddedCik + ".json";
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                secEntity,
                String.class);
        return response.getBody();
    }

    public String fetchXbrlFrames(String taxonomy, String tag, String unit, String period) {
        String url = String.format("https://data.sec.gov/api/xbrl/frames/%s/%s/%s/%s.json", taxonomy, tag, unit,
                period);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                secEntity,
                String.class);
        return response.getBody();
    }

    public String fetchNasdaqData(String url) {
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class);
        return response.getBody();
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

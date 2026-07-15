package com.fattorestreet.sec_api.economic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FredService {

    private static final String OBSERVATIONS_URL = "https://api.stlouisfed.org/fred/series/observations";
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record FredObservation(LocalDate date, double value) {
    }

    private record CacheEntry(List<FredObservation> observations, long expiresAtEpochMs) {
    }

    @Autowired
    public FredService(@Value("${fred.api-key}") String apiKey) {
        this(new RestTemplate(), apiKey);
    }

    FredService(RestTemplate restTemplate, String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    public List<FredObservation> getSeries(String seriesId, boolean computeYoy) {
        String cacheKey = seriesId + "|" + computeYoy;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAtEpochMs() > System.currentTimeMillis()) {
            return cached.observations();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(OBSERVATIONS_URL)
                .queryParam("series_id", seriesId)
                .queryParam("api_key", apiKey)
                .queryParam("file_type", "json");
        if (computeYoy) {
            builder.queryParam("units", "pc1");
        }
        String body = restTemplate.getForObject(builder.toUriString(), String.class);

        List<FredObservation> observations = parseObservations(body);
        cache.put(cacheKey, new CacheEntry(observations, System.currentTimeMillis() + CACHE_TTL_MS));
        return observations;
    }

    private List<FredObservation> parseObservations(String body) {
        JsonNode root = objectMapper.readTree(body);
        List<FredObservation> result = new ArrayList<>();
        for (JsonNode observation : root.path("observations")) {
            double value;
            try {
                value = Double.parseDouble(observation.path("value").asText());
            } catch (NumberFormatException e) {
                continue; // FRED marks missing values with "."
            }
            result.add(new FredObservation(LocalDate.parse(observation.path("date").asText()), value));
        }
        result.sort(Comparator.comparing(FredObservation::date));
        return result;
    }
}

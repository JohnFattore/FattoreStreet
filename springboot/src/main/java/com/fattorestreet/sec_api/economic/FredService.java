package com.fattorestreet.sec_api.economic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fattorestreet.sec_api.config.CacheConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FredService {

    private static final String OBSERVATIONS_URL = "https://api.stlouisfed.org/fred/series/observations";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;

    public record FredObservation(LocalDate date, double value) {
    }

    @Autowired
    public FredService(@Value("${fred.api-key}") String apiKey) {
        this(new RestTemplate(), apiKey);
    }

    FredService(RestTemplate restTemplate, String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Fetches a series from FRED, cached in Redis per series and units (TTL {@code fred.cache-ttl},
     * default 24h; see {@link CacheConfig}).
     */
    @Cacheable(cacheNames = CacheConfig.FRED_CACHE, key = "#seriesId + '|' + #computeYoy")
    public List<FredObservation> getSeries(String seriesId, boolean computeYoy) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(OBSERVATIONS_URL)
                .queryParam("series_id", seriesId)
                .queryParam("api_key", apiKey)
                .queryParam("file_type", "json");
        if (computeYoy) {
            builder.queryParam("units", "pc1");
        }
        String body = restTemplate.getForObject(builder.toUriString(), String.class);

        return parseObservations(body);
    }

    private List<FredObservation> parseObservations(String body) {
        JsonNode root = objectMapper.readTree(body);
        List<FredObservation> result = new ArrayList<>();
        for (JsonNode observation : root.path("observations")) {
            double value;
            try {
                value = Double.parseDouble(observation.path("value").asString());
            } catch (NumberFormatException e) {
                continue; // FRED marks missing values with "."
            }
            result.add(new FredObservation(LocalDate.parse(observation.path("date").asString()), value));
        }
        result.sort(Comparator.comparing(FredObservation::date));
        return result;
    }
}

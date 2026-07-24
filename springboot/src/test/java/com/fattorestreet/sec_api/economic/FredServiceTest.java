package com.fattorestreet.sec_api.economic;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FredServiceTest {

    private static final String PAYLOAD = """
            {"observations": [
              {"realtime_start": "2026-01-01", "realtime_end": "2026-01-01", "date": "2024-02-01", "value": "3.9"},
              {"realtime_start": "2026-01-01", "realtime_end": "2026-01-01", "date": "2024-01-01", "value": "3.7"},
              {"realtime_start": "2026-01-01", "realtime_end": "2026-01-01", "date": "2024-03-01", "value": "."}
            ]}
            """;

    @Mock
    private RestTemplate restTemplate;

    private FredService fredService;

    @BeforeEach
    void setUp() {
        fredService = new FredService(restTemplate, "test-key");
    }

    @Test
    void getSeries_parsesSortsAndDropsMissingValues() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(PAYLOAD);

        List<FredService.FredObservation> result = fredService.getSeries("UNRATE", false);

        // FRED's "." missing marker is dropped, remaining observations sorted ascending by date
        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2024, 1, 1), result.get(0).date());
        assertEquals(3.7, result.get(0).value());
        assertEquals(LocalDate.of(2024, 2, 1), result.get(1).date());
        assertEquals(3.9, result.get(1).value());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(String.class));
        String url = urlCaptor.getValue();
        assertTrue(url.contains("series_id=UNRATE"));
        assertTrue(url.contains("api_key=test-key"));
        assertTrue(url.contains("file_type=json"));
        assertFalse(url.contains("units=pc1"));
    }

    @Test
    void getSeries_computeYoyRequestsPc1Units() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(PAYLOAD);

        fredService.getSeries("CPIAUCSL", true);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(String.class));
        assertTrue(urlCaptor.getValue().contains("units=pc1"));
    }

    @Test
    void getSeries_cachesResultsPerSeriesAndUnits() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(PAYLOAD);

        fredService.getSeries("UNRATE", false);
        fredService.getSeries("UNRATE", false);
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));

        fredService.getSeries("UNRATE", true);
        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }
}

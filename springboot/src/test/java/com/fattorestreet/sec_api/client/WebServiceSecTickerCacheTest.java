package com.fattorestreet.sec_api.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebServiceSecTickerCacheTest {

    @Test
    void fetchFilingDocument_withTickerScopedCache_dedupesHttpCalls() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        WebService webService = new WebService(
                restTemplate,
                "test@example.com",
                1000,
                1000,
                1,
                0,
                0
        );

        Long cik = 320193L;
        String accession = "0000320193-20-000062";
        String doc = "d8k.htm";

        webService.beginSecTickerScopedCache();
        try {
            webService.fetchFilingDocument(cik, accession, doc);
            webService.fetchFilingDocument(cik, accession, doc);
        } finally {
            webService.endSecTickerScopedCache();
        }

        verify(restTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }
}

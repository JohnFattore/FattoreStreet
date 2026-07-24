package com.fattorestreet.sec_api.client;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebServiceTest {

    @Test
    void fetchFinancials_retriesTransientTimeoutThenSucceeds() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        WebService service = new WebService(restTemplate, "test@test.com", 1000, 1000, 3, 0, 0);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("Read timed out")))
                .thenReturn(ResponseEntity.ok("{\"ok\":true}"));

        String out = service.fetchFinancials(320193L);

        assertEquals("{\"ok\":true}", out);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void fetchFinancials_doesNotRetryOnBadRequest() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        WebService service = new WebService(restTemplate, "test@test.com", 1000, 1000, 3, 0, 0);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThrows(HttpClientErrorException.class, () -> service.fetchFinancials(320193L));
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }
}

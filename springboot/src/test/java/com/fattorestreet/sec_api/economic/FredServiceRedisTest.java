package com.fattorestreet.sec_api.economic;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fattorestreet.sec_api.config.CacheConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the FRED cache against real Redis through Boot's auto-configured {@code RedisCacheManager},
 * covering what the in-memory {@link FredServiceCachingTest} can't: the JSON value serializer, the
 * key prefix and the TTL as stored. Skipped when no Docker daemon is available; CI runners have one.
 */
@SpringBootTest(
        classes = FredServiceRedisTest.Config.class,
        properties = {"spring.cache.type=redis", "spring.cache.redis.key-prefix=springboot::"})
@Testcontainers(disabledWithoutDocker = true)
class FredServiceRedisTest {

    // Same exact image as production (deploy/docker-compose.infra.yml)
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.10.3").withExposedPorts(6379);

    private static final String PAYLOAD = """
            {"observations": [{"date": "2024-01-01", "value": "3.7"}, {"date": "2024-02-01", "value": "3.9"}]}
            """;

    @Configuration
    @ImportAutoConfiguration({DataRedisAutoConfiguration.class, CacheAutoConfiguration.class})
    @Import(CacheConfig.class)
    static class Config {
        @Bean
        RestTemplate restTemplate() {
            RestTemplate restTemplate = mock(RestTemplate.class);
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(PAYLOAD);
            return restTemplate;
        }

        @Bean
        FredService fredService(RestTemplate restTemplate) {
            return new FredService(restTemplate, "test-key");
        }
    }

    @Autowired
    private FredService fredService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
        clearInvocations(restTemplate);
    }

    @Test
    void getSeries_secondCallIsServedFromRedis() {
        List<FredService.FredObservation> first = fredService.getSeries("UNRATE", false);
        List<FredService.FredObservation> second = fredService.getSeries("UNRATE", false);

        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
        assertEquals(first, second);
        assertEquals(new FredService.FredObservation(LocalDate.of(2024, 1, 1), 3.7), second.getFirst());
    }

    @Test
    void getSeries_storesPrefixedJsonWithTheFredTtl() {
        fredService.getSeries("UNRATE", true);

        String key = "springboot::fred::UNRATE|true";
        String stored = redis.opsForValue().get(key);
        assertNotNull(stored, "expected the series under " + key);
        assertTrue(stored.contains("\"2024-01-01\""), stored);

        Long ttlSeconds = redis.getExpire(key);
        assertNotNull(ttlSeconds);
        assertTrue(ttlSeconds > 23 * 3600 && ttlSeconds <= 24 * 3600, "ttl " + ttlSeconds);
    }
}

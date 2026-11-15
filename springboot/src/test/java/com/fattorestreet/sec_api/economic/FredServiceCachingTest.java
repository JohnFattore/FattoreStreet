package com.fattorestreet.sec_api.economic;

import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

import com.fattorestreet.sec_api.config.CacheConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@code @Cacheable} on {@link FredService#getSeries} through the real {@link CacheConfig}
 * proxy, with an in-memory cache standing in for Redis.
 */
class FredServiceCachingTest {

    private static final String PAYLOAD = """
            {"observations": [{"date": "2024-01-01", "value": "3.7"}]}
            """;

    private static RestTemplate stubbedRestTemplate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(PAYLOAD);
        return restTemplate;
    }

    /** Boot's converters, so {@link CacheConfig} can bind {@code fred.cache-ttl} to a Duration. */
    @Configuration
    @Import(CacheConfig.class)
    static class BaseConfig {
        @Bean
        static ConversionService conversionService() {
            return ApplicationConversionService.getSharedInstance();
        }
    }

    @Nested
    @SpringJUnitConfig(WorkingCache.Config.class)
    class WorkingCache {

        @Configuration
        @Import(BaseConfig.class)
        static class Config {
            @Bean
            RestTemplate restTemplate() {
                return stubbedRestTemplate();
            }

            @Bean
            CacheManager cacheManager() {
                return new ConcurrentMapCacheManager(CacheConfig.FRED_CACHE);
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
        private CacheManager cacheManager;

        @BeforeEach
        void reset() {
            cacheManager.getCache(CacheConfig.FRED_CACHE).clear();
            clearInvocations(restTemplate);
            // Shared mock across tests: restore the default payload after any test re-stubs it
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(PAYLOAD);
        }

        @Test
        void getSeries_cachesResultsPerSeriesAndUnits() {
            fredService.getSeries("UNRATE", false);
            fredService.getSeries("UNRATE", false);
            verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));

            fredService.getSeries("UNRATE", true);
            verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));

            fredService.getSeries("CPIAUCSL", false);
            verify(restTemplate, times(3)).getForObject(anyString(), eq(String.class));
        }

        @Test
        void getSeries_doesNotCacheAnEmptySeries() {
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"observations\": []}");

            fredService.getSeries("EMPTY", false);
            fredService.getSeries("EMPTY", false);

            // Nothing stored, so the second call retries FRED rather than serving [] for a full TTL
            verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
            assertNull(cacheManager.getCache(CacheConfig.FRED_CACHE).get("EMPTY|false"));
        }

        @Test
        void getSeries_storesUnderSeriesAndUnitsKey() {
            List<FredService.FredObservation> result = fredService.getSeries("UNRATE", true);

            assertEquals(result, cacheManager.getCache(CacheConfig.FRED_CACHE).get("UNRATE|true").get());
        }
    }

    @Nested
    @SpringJUnitConfig(UnreachableCache.Config.class)
    class UnreachableCache {

        @Configuration
        @Import(BaseConfig.class)
        static class Config {
            @Bean
            RestTemplate restTemplate() {
                return stubbedRestTemplate();
            }

            @Bean
            CacheManager cacheManager() {
                SimpleCacheManager manager = new SimpleCacheManager();
                manager.setCaches(List.of(new FailingCache(CacheConfig.FRED_CACHE)));
                return manager;
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

        @Test
        void getSeries_fallsThroughToFredWhenTheCacheThrows() {
            List<FredService.FredObservation> first = fredService.getSeries("UNRATE", false);
            fredService.getSeries("UNRATE", false);

            assertEquals(1, first.size());
            // Nothing could be cached, so each call reaches FRED instead of failing
            verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
        }
    }

    /** A cache whose every operation fails the way a down Redis connection does. */
    static final class FailingCache extends AbstractValueAdaptingCache {

        private final String name;

        FailingCache(String name) {
            super(false);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        protected Object lookup(Object key) {
            throw new IllegalStateException("redis down");
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            throw new IllegalStateException("redis down");
        }

        @Override
        public void put(Object key, Object value) {
            throw new IllegalStateException("redis down");
        }

        @Override
        public void evict(Object key) {
            throw new IllegalStateException("redis down");
        }

        @Override
        public void clear() {
            throw new IllegalStateException("redis down");
        }
    }
}

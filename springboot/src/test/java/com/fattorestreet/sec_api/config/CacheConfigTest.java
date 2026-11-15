package com.fattorestreet.sec_api.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.fattorestreet.sec_api.economic.FredService.FredObservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    @Test
    void fredSeriesSerializer_roundTripsObservationsAsPlainJson() {
        RedisSerializer<List<FredObservation>> serializer = CacheConfig.fredSeriesSerializer();
        List<FredObservation> series = List.of(
                new FredObservation(LocalDate.of(2024, 1, 1), 3.7),
                new FredObservation(LocalDate.of(2024, 2, 1), 3.9));

        byte[] bytes = serializer.serialize(series);
        String json = new String(bytes, StandardCharsets.UTF_8);

        // ISO dates and no embedded Java class names, so the stored value is readable in redis-cli
        assertTrue(json.contains("\"2024-01-01\""), json);
        assertFalse(json.contains("FredObservation"), json);
        assertEquals(series, serializer.deserialize(bytes));
    }

    @Test
    void fredCacheCustomizer_setsTtlAndKeepsDefaultKeyPrefix() {
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager
                .builder(mock(RedisCacheWriter.class))
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().prefixCacheNameWith("springboot::"));

        new CacheConfig().fredCacheCustomizer(Duration.ofHours(6)).customize(builder);

        RedisCacheConfiguration fred = builder.getCacheConfigurationFor(CacheConfig.FRED_CACHE).orElseThrow();
        assertEquals(Duration.ofHours(6), fred.getTtlFunction().getTimeToLive("key", List.of()));
        assertEquals("springboot::fred::", fred.getKeyPrefixFor(CacheConfig.FRED_CACHE));
        assertFalse(fred.getAllowCacheNullValues());
    }
}

package com.fattorestreet.sec_api.config;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.fattorestreet.sec_api.economic.FredService.FredObservation;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caches upstream API responses in Redis, the same instance Django caches in, so they survive
 * container restarts. Spring Boot auto-configures the {@code RedisCacheManager} from
 * {@code spring.data.redis.*} and {@code spring.cache.redis.*}; this class adds per-cache settings
 * and makes Redis optional: a cache read or write that fails is logged and the call goes straight
 * to the upstream API, so an unreachable Redis degrades to uncached rather than failing requests.
 *
 * <p>The Fargate run modes never touch a cache. Lettuce connects lazily, so they start without a
 * Redis to reach.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String FRED_CACHE = "fred";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    RedisCacheManagerBuilderCustomizer fredCacheCustomizer(@Value("${fred.cache-ttl:24h}") Duration fredCacheTtl) {
        return builder -> builder.withCacheConfiguration(FRED_CACHE, builder.cacheDefaults()
                .entryTtl(fredCacheTtl)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(fredSeriesSerializer())));
    }

    /**
     * JSON with the concrete element type fixed up front, rather than Boot's default JDK
     * serialization (records aren't {@code Serializable}) or embedded class names.
     */
    static RedisSerializer<List<FredObservation>> fredSeriesSerializer() {
        JsonMapper mapper = JsonMapper.builder().build();
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, FredObservation.class);
        return new JacksonJsonRedisSerializer<>(mapper, type);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /** Logs cache failures and lets the cached method run as if the entry were missing. */
    static final class LoggingCacheErrorHandler implements CacheErrorHandler {

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.warn("Cache get failed for {}::{}, calling upstream: {}", cache.getName(), key, exception.toString());
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.warn("Cache put failed for {}::{}: {}", cache.getName(), key, exception.toString());
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.warn("Cache evict failed for {}::{}: {}", cache.getName(), key, exception.toString());
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.warn("Cache clear failed for {}: {}", cache.getName(), exception.toString());
        }
    }
}

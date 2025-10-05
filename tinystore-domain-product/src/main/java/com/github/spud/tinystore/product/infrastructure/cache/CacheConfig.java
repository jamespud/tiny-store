package com.github.spud.tinystore.product.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * CacheConfig - Redis cache configuration for Product Service
 * 
 * Configures distributed caching with Redis for:
 * - Product entities (30 min TTL)
 * - SKU entities (30 min TTL)
 * - Pricing calculation results (10 min TTL)
 * 
 * Features:
 * - JSON serialization with Jackson for human-readable cache values
 * - Dynamic TTL configuration via application properties
 * - Cache key prefixing for multi-service deployment
 * - Cache statistics enabled for monitoring
 * - Support for null values (cache negative results)
 * 
 * Cache invalidation:
 * - Event-driven: ProductEventCacheInvalidationListener deletes keys on domain events
 * - TTL-based: Automatic expiration as fallback
 * - Manual: @CacheEvict annotations on service methods
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Value("${tinystore.product.cache.ttl.product:1800}")
    private long productCacheTtlSeconds;
    
    @Value("${tinystore.product.cache.ttl.sku:1800}")
    private long skuCacheTtlSeconds;
    
    @Value("${tinystore.product.cache.ttl.pricing:600}")
    private long pricingCacheTtlSeconds;
    
    @Value("${tinystore.product.cache.key-prefix:product-service}")
    private String cacheKeyPrefix;
    
    /**
     * Configure RedisCacheManager with custom TTL per cache and JSON serialization
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure JSON serialization with type information
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(productCacheTtlSeconds))
            .prefixCacheNameWith(cacheKeyPrefix + ":")
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
            )
            .disableCachingNullValues() // Don't cache null results (404s should retry)
            .enableTimeToIdle(); // Reset TTL on cache hit
        
        // Per-cache TTL configuration
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Product cache: 30 minutes (or configured value)
        cacheConfigurations.put("products", 
            defaultConfig.entryTtl(Duration.ofSeconds(productCacheTtlSeconds))
        );
        
        // SKU cache: 30 minutes (or configured value)
        cacheConfigurations.put("skus", 
            defaultConfig.entryTtl(Duration.ofSeconds(skuCacheTtlSeconds))
        );
        
        // Pricing results cache: 10 minutes (or configured value)
        // Shorter TTL for pricing due to frequent rule changes
        cacheConfigurations.put("pricing-results", 
            defaultConfig.entryTtl(Duration.ofSeconds(pricingCacheTtlSeconds))
        );
        
        // Build cache manager
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .enableStatistics() // Enable cache metrics for monitoring
            .build();
    }
}

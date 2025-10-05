package com.github.spud.tinystore.product.infrastructure.config;

import com.github.spud.tinystore.product.application.PricingService;
import com.github.spud.tinystore.product.application.ProductService;
import com.github.spud.tinystore.product.domain.repository.PricingRuleRepository;
import com.github.spud.tinystore.product.domain.repository.ProductRepository;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;
import com.github.spud.tinystore.product.infrastructure.event.LoggingEventPublisher;
import com.github.spud.tinystore.product.infrastructure.outbox.OutboxServiceBridge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * ProductConfig - Application configuration for Product domain
 * 
 * Responsibilities:
 * - Wire domain services (ProductService, PricingService)
 * - Configure repository implementations (JPA vs InMemory based on profile)
 * - Configure event publisher (Outbox vs Logging based on feature toggle)
 * - Configure cache manager and TTL settings
 * - Enable dynamic configuration refresh via @RefreshScope
 * 
 * Feature toggles:
 * - feature.outbox.enabled: Use OutboxServiceBridge or LoggingEventPublisher
 * - feature.pricing-rule.enabled: Enable/disable pricing rule execution
 * 
 * Profiles:
 * - local/test: Use InMemory repositories
 * - prod: Use JPA repositories
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository")
@EnableCaching
public class ProductConfig {
    
    @Value("${tinystore.feature.outbox.enabled:false}")
    private boolean outboxEnabled;
    
    @Value("${tinystore.feature.pricing-rule.enabled:true}")
    private boolean pricingRuleEnabled;
    
    /**
     * Configure DomainEventPublisher based on feature toggle
     * 
     * @param outboxServiceBridge Outbox publisher implementation
     * @param loggingEventPublisher Logging fallback publisher
     * @return Active event publisher
     */
    @Bean
    @RefreshScope
    public Object domainEventPublisher(
            OutboxServiceBridge outboxServiceBridge,
            LoggingEventPublisher loggingEventPublisher) {
        // TODO: Return actual DomainEventPublisher interface
        // For now, return the appropriate implementation based on toggle
        if (outboxEnabled) {
            return outboxServiceBridge;
        } else {
            return loggingEventPublisher;
        }
    }
    
    /**
     * Configure ProductService
     * 
     * @param productRepository Product repository
     * @param eventPublisher Event publisher
     * @return ProductService instance
     */
    @Bean
    public ProductService productService(
            ProductRepository productRepository,
            Object eventPublisher) {
        // TODO: Implement ProductService instantiation with dependencies
        throw new UnsupportedOperationException("ProductService bean configuration not yet implemented");
    }
    
    /**
     * Configure PricingService
     * 
     * @param pricingRuleRepository Pricing rule repository
     * @param skuRepository SKU repository
     * @return PricingService instance
     */
    @Bean
    @RefreshScope
    public PricingService pricingService(
            PricingRuleRepository pricingRuleRepository,
            SkuRepository skuRepository,
            CacheManager cacheManager) {
        // TODO: Implement PricingService instantiation
        // Include RuleRegistry, RuleConflictChecker
        throw new UnsupportedOperationException("PricingService bean configuration not yet implemented");
    }
    
    /**
     * Cache configuration - dynamically adjustable TTL
     * TTL values from application.yml:
     * - tinystore.product.cache.ttl.product
     * - tinystore.product.cache.ttl.sku
     * - tinystore.product.cache.ttl.pricing
     * - tinystore.product.cache.ttl.channel
     */
    @Bean
    @RefreshScope
    @ConditionalOnProperty(prefix = "spring.data.redis", name = "host")
    public CacheManager cacheManager(
            @Value("${tinystore.product.cache.ttl.product:3600}") long productTtl,
            @Value("${tinystore.product.cache.ttl.sku:3600}") long skuTtl,
            @Value("${tinystore.product.cache.ttl.pricing:1800}") long pricingTtl,
            @Value("${tinystore.product.cache.ttl.channel:600}") long channelTtl) {
        // TODO: Configure RedisCacheManager with dynamic TTL per cache
        throw new UnsupportedOperationException("CacheManager bean configuration not yet implemented");
    }
}

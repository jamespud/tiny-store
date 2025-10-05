package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import com.github.spud.tinystore.product.domain.repository.PricingRuleRepository;
import com.github.spud.tinystore.product.domain.rules.PricingRule;
import com.github.spud.tinystore.product.domain.rules.SimpleRuleEngine;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * PricingService - Application service for dynamic pricing calculation
 * Uses rule engine to evaluate pricing rules and calculate final prices
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {
    
    private final PricingRuleRepository ruleRepository;
    private final TenantRepositoryConfig.TenantContext tenantContext;
    
    /**
     * Calculate price for a given context (SKU, user, time)
     * Applies all matching active rules in priority order
     */
    @Cacheable(value = "pricing-results", key = "#context.sku().skuId + ':' + #context.at()")
    public PricingResult calculatePrice(PricingContext context) {
        String tenantId = tenantContext.getTenantId();
        Instant currentTime = context.at();
        
        log.debug("Calculating price for SKU: {} at time: {} for tenant: {}", 
                context.sku().getSkuId(), currentTime, tenantId);
        
        // Load active rules for the product
        String productId = context.sku().getProductId();
        List<PricingRule> productRules = ruleRepository.findActiveRulesByProduct(
                tenantId, productId, currentTime);
        
        // Load active rules for the category
        // TODO: Get category from product
        // List<PricingRule> categoryRules = ruleRepository.findActiveRulesByCategory(
        //         tenantId, categoryId, currentTime);
        
        // Load active rules for user tags (from context attributes)
        Object userTagsAttr = context.attributes().get("userTags");
        if (userTagsAttr instanceof List<?> userTagsList) {
            @SuppressWarnings("unchecked")
            List<String> userTags = (List<String>) userTagsList;
            if (!userTags.isEmpty()) {
                List<PricingRule> tagRules = ruleRepository.findActiveRulesByTags(
                        tenantId, userTags, currentTime);
                productRules.addAll(tagRules);
            }
        }
        
        // Load all global active rules
        List<PricingRule> globalRules = ruleRepository.findAllActiveRules(tenantId, currentTime);
        productRules.addAll(globalRules);
        
        // Create rule engine and evaluate
        SimpleRuleEngine engine = new SimpleRuleEngine();
        engine.registerAll(productRules);
        
        PricingResult result = engine.evaluate(context);
        
        log.info("Price calculated for SKU: {} - Original: {}, Final: {}, Adjustments: {}",
                context.sku().getSkuId(), 
                result.basePrice(),
                result.finalPrice(),
                result.adjustments().size());
        
        return result;
    }
    
    /**
     * Calculate prices for multiple SKUs (batch operation)
     */
    public List<PricingResult> calculatePrices(List<PricingContext> contexts) {
        return contexts.stream()
                .map(this::calculatePrice)
                .toList();
    }
    
    /**
     * Preview price calculation without caching
     * Useful for admin preview or testing
     */
    public PricingResult previewPrice(PricingContext context) {
        String tenantId = tenantContext.getTenantId();
        log.info("Previewing price for SKU: {} for tenant: {}", context.sku().getSkuId(), tenantId);
        
        // Same logic as calculatePrice but without caching
        String productId = context.sku().getProductId();
        Instant currentTime = context.at();
        
        List<PricingRule> allRules = ruleRepository.findAllActiveRules(tenantId, currentTime);
        
        SimpleRuleEngine engine = new SimpleRuleEngine();
        engine.registerAll(allRules);
        
        return engine.evaluate(context);
    }
}

package com.github.spud.tinystore.product.domain.repository;

import com.github.spud.tinystore.product.domain.rules.PricingRule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PricingRuleRepository - Domain repository interface for pricing rules
 * 
 * Provides access to pricing rules filtered by:
 * - Tenant (mandatory for all operations)
 * - Product ID
 * - Category ID
 * - User tags
 * - Time window (effective/expire)
 * - Status
 * 
 * Implementation must ensure multi-tenancy isolation
 */
public interface PricingRuleRepository {
    
    /**
     * Save or update a pricing rule
     * 
     * @param rule Pricing rule to persist
     * @return Persisted pricing rule with generated ID
     */
    PricingRule save(PricingRule rule);
    
    /**
     * Find pricing rule by tenant and rule code
     * 
     * @param tenantId Tenant identifier
     * @param ruleCode Unique rule code
     * @return Optional pricing rule
     */
    Optional<PricingRule> findByRuleCode(String tenantId, String ruleCode);
    
    /**
     * Find active pricing rules for a specific product
     * Returns rules applicable at current time
     * 
     * @param tenantId Tenant identifier
     * @param productId Product identifier
     * @param currentTime Current time for window check
     * @return List of active rules ordered by priority (descending)
     */
    List<PricingRule> findActiveRulesByProduct(String tenantId, String productId, Instant currentTime);
    
    /**
     * Find active pricing rules for a category
     * Returns rules applicable at current time
     * 
     * @param tenantId Tenant identifier
     * @param categoryId Category identifier
     * @param currentTime Current time for window check
     * @return List of active rules ordered by priority (descending)
     */
    List<PricingRule> findActiveRulesByCategory(String tenantId, String categoryId, Instant currentTime);
    
    /**
     * Find active pricing rules matching user tags
     * 
     * @param tenantId Tenant identifier
     * @param userTags User tags for filtering
     * @param currentTime Current time for window check
     * @return List of active rules ordered by priority (descending)
     */
    List<PricingRule> findActiveRulesByTags(String tenantId, List<String> userTags, Instant currentTime);
    
    /**
     * Find all active rules within tenant
     * Used for cache preload or batch operations
     * 
     * @param tenantId Tenant identifier
     * @param currentTime Current time for window check
     * @return List of all active rules
     */
    List<PricingRule> findAllActiveRules(String tenantId, Instant currentTime);
}

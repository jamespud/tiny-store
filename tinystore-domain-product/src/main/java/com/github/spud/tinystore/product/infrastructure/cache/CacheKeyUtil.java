package com.github.spud.tinystore.product.infrastructure.cache;

/**
 * CacheKeyUtil - Utility for constructing cache keys
 * 
 * Key patterns:
 * - product:{tenantId}:{productId}
 * - sku:{tenantId}:{skuId}
 * - pricing:rules:{tenantId}:{productId}
 * - sku:dynamic:{tenantId}:{skuId}:{channel}
 * 
 * All cache keys include tenant ID for proper isolation
 */
public class CacheKeyUtil {
    
    private static final String PRODUCT_PREFIX = "product";
    private static final String SKU_PREFIX = "sku";
    private static final String PRICING_RULES_PREFIX = "pricing:rules";
    private static final String SKU_DYNAMIC_PREFIX = "sku:dynamic";
    private static final String DELIMITER = ":";
    
    /**
     * Generate cache key for product
     * 
     * @param tenantId Tenant identifier
     * @param productId Product identifier
     * @return Cache key: product:{tenantId}:{productId}
     */
    public static String productKey(String tenantId, String productId) {
        return String.join(DELIMITER, PRODUCT_PREFIX, tenantId, productId);
    }
    
    /**
     * Generate cache key for SKU
     * 
     * @param tenantId Tenant identifier
     * @param skuId SKU identifier
     * @return Cache key: sku:{tenantId}:{skuId}
     */
    public static String skuKey(String tenantId, String skuId) {
        return String.join(DELIMITER, SKU_PREFIX, tenantId, skuId);
    }
    
    /**
     * Generate cache key for pricing rules by product
     * 
     * @param tenantId Tenant identifier
     * @param productId Product identifier
     * @return Cache key: pricing:rules:{tenantId}:{productId}
     */
    public static String pricingRulesKey(String tenantId, String productId) {
        return String.join(DELIMITER, PRICING_RULES_PREFIX, tenantId, productId);
    }
    
    /**
     * Generate cache key for SKU with dynamic attributes (channel-specific)
     * 
     * @param tenantId Tenant identifier
     * @param skuId SKU identifier
     * @param channel Channel identifier
     * @return Cache key: sku:dynamic:{tenantId}:{skuId}:{channel}
     */
    public static String skuDynamicKey(String tenantId, String skuId, String channel) {
        return String.join(DELIMITER, SKU_DYNAMIC_PREFIX, tenantId, skuId, channel);
    }
    
    /**
     * Generate pattern for deleting all product keys in tenant
     * 
     * @param tenantId Tenant identifier
     * @return Pattern: product:{tenantId}:*
     */
    public static String productPattern(String tenantId) {
        return String.join(DELIMITER, PRODUCT_PREFIX, tenantId, "*");
    }
    
    /**
     * Generate pattern for deleting all SKU keys in tenant
     * 
     * @param tenantId Tenant identifier
     * @return Pattern: sku:{tenantId}:*
     */
    public static String skuPattern(String tenantId) {
        return String.join(DELIMITER, SKU_PREFIX, tenantId, "*");
    }
}

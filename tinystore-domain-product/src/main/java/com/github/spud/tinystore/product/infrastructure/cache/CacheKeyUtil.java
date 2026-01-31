package com.github.spud.tinystore.product.infrastructure.cache;

/**
 * CacheKeyUtil - Utility for constructing cache keys
 * <p>
 * Key patterns: - product:{shopId}:{productId} - sku:{shopId}:{skuId} -
 * pricing:rules:{shopId}:{productId} - sku:dynamic:{shopId}:{skuId}:{channel}
 * <p>
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
	 * @param shopId  Shop identifier
	 * @param productId Product identifier
	 * @return Cache key: product:{shopId}:{productId}
	 */
	public static String productKey(String shopId, String productId) {
		return String.join(DELIMITER, PRODUCT_PREFIX, shopId, productId);
	}

	/**
	 * Generate cache key for SKU
	 *
	 * @param shopId Shop identifier
	 * @param skuId    SKU identifier
	 * @return Cache key: sku:{shopId}:{skuId}
	 */
	public static String skuKey(String shopId, String skuId) {
		return String.join(DELIMITER, SKU_PREFIX, shopId, skuId);
	}

	/**
	 * Generate cache key for pricing rules by product
	 *
	 * @param shopId  Shop identifier
	 * @param productId Product identifier
	 * @return Cache key: pricing:rules:{shopId}:{productId}
	 */
	public static String pricingRulesKey(String shopId, String productId) {
		return String.join(DELIMITER, PRICING_RULES_PREFIX, shopId, productId);
	}

	/**
	 * Generate cache key for SKU with dynamic attributes (channel-specific)
	 *
	 * @param shopId Shop identifier
	 * @param skuId    SKU identifier
	 * @param channel  Channel identifier
	 * @return Cache key: sku:dynamic:{shopId}:{skuId}:{channel}
	 */
	public static String skuDynamicKey(String shopId, String skuId, String channel) {
		return String.join(DELIMITER, SKU_DYNAMIC_PREFIX, shopId, skuId, channel);
	}

	/**
	 * Generate pattern for deleting all product keys in tenant
	 *
	 * @param shopId Shop identifier
	 * @return Pattern: product:{shopId}:*
	 */
	public static String productPattern(String shopId) {
		return String.join(DELIMITER, PRODUCT_PREFIX, shopId, "*");
	}

	/**
	 * Generate pattern for deleting all SKU keys in tenant
	 *
	 * @param shopId Shop identifier
	 * @return Pattern: sku:{shopId}:*
	 */
	public static String skuPattern(String shopId) {
		return String.join(DELIMITER, SKU_PREFIX, shopId, "*");
	}
}

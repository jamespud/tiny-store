package com.github.spud.tinystore.product.infrastructure.cache;

import com.github.spud.tinystore.product.domain.event.ProductCreatedEvent;
import com.github.spud.tinystore.product.domain.event.ProductPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ProductEventCacheInvalidationListener - Event-driven cache invalidation
 * <p>
 * Listens to domain events and invalidates corresponding cache entries: - ProductCreatedEvent,
 * ProductUpdatedEvent -> Invalidate product:{tenantId}:{productId} - SkuCreatedEvent,
	 * SkuUpdatedEvent -> Invalidate sku:{tenantId}:{skuId}
 * <p>
 * Cache invalidation strategy: 1. Event-driven (immediate invalidation on domain changes) 2.
 * TTL-based expiration (fallback for missed events)
 * <p>
 * TODO: Implement event listeners once event infrastructure is complete
 */
@Component
public class ProductEventCacheInvalidationListener {

	private static final Logger logger = LoggerFactory.getLogger(
		ProductEventCacheInvalidationListener.class);

	private final CacheManager cacheManager;

	public ProductEventCacheInvalidationListener(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	/**
	 * Handle ProductCreatedEvent - invalidate product cache
	 *
	 * @param event ProductCreatedEvent
	 */
	@EventListener
	public void onProductCreated(ProductCreatedEvent event) {
		// TODO: Extract tenantId and productId from event
		// String tenantId = event.getTenantId();
		// String productId = event.getProductId();
		// String cacheKey = CacheKeyUtil.productKey(tenantId, productId);
		// evictCache("product", cacheKey);
		logger.info("ProductCreatedEvent received, cache invalidation placeholder");
	}

	/**
	 * Handle ProductPublishedEvent - invalidate product cache
	 *
	 * @param event ProductPublishedEvent
	 */
	@EventListener
	public void onProductPublished(ProductPublishedEvent event) {
		// TODO: Invalidate product cache
		logger.info("ProductPublishedEvent received, cache invalidation placeholder");
	}

	/**
	 * Evict cache entry by key
	 *
	 * @param cacheName Cache name
	 * @param cacheKey  Cache key to evict
	 */
	private void evictCache(String cacheName, String cacheKey) {
		try {
			var cache = cacheManager.getCache(cacheName);
			if (cache != null) {
				cache.evict(cacheKey);
				logger.debug("Evicted cache entry: cacheName={}, key={}", cacheName, cacheKey);
			}
		} catch (Exception e) {
			logger.error("Failed to evict cache: cacheName={}, key={}", cacheName, cacheKey, e);
		}
	}
}

package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.ShopRepositoryConfig;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SkuService - Application service for SKU management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkuService {

	private final SkuRepository skuRepository;
	private final ShopRepositoryConfig.ShopContext shopContext;

	/**
	 * Create a new SKU
	 */
	@Transactional
	@CacheEvict(value = "skus", allEntries = true)
	public Sku createSku(Sku sku) {
		String shopId = shopContext.getShopId();
		log.info("Creating SKU: {} for product: {} shop: {}", sku.getSkuId(), sku.getProductId(),
			shopId);

		// Validate product exists
		// TODO: Add ProductRepository dependency and validation

		return skuRepository.save(sku);
	}

	/**
	 * Update SKU attributes
	 */
	@Transactional
	@CacheEvict(value = "skus", key = "#skuId.id")
	public Sku updateSku(SkuId skuId, Sku updatedSku) {
		String shopId = shopContext.getShopId();
		log.info("Updating SKU: {} for shop: {}", skuId, shopId);

		Optional<Sku> existing = skuRepository.findById(skuId);
		if (existing.isEmpty()) {
			throw new IllegalArgumentException("SKU not found: " + skuId);
		}

		Sku sku = existing.get();

		// Update attributes
		sku.setAttributes(updatedSku.getAttributes());
		sku.setBarCode(updatedSku.getBarCode());

		return skuRepository.save(sku);
	}

	/**
	 * Get SKU by ID (with caching)
	 */
	@Cacheable(value = "skus", key = "#skuId.id")
	public Optional<Sku> getSku(SkuId skuId) {
		String shopId = shopContext.getShopId();
		log.debug("Getting SKU: {} for shop: {}", skuId, shopId);

		return skuRepository.findById(skuId);
	}

	/**
	 * Find all SKUs for a product
	 */
	@Cacheable(value = "product-skus", key = "#productId.id")
	public List<Sku> getSkusByProduct(ProductId productId) {
		String shopId = shopContext.getShopId();
		log.debug("Getting SKUs for product: {} shop: {}", productId, shopId);

		return skuRepository.findByProductId(productId);
	}

	/**
	 * Disable SKU
	 */
	@Transactional
	@CacheEvict(value = {"skus", "product-skus"}, allEntries = true)
	public void disableSku(SkuId skuId) {
		String shopId = shopContext.getShopId();
		log.info("Disabling SKU: {} for shop: {}", skuId, shopId);

		Optional<Sku> existing = skuRepository.findById(skuId);
		if (existing.isEmpty()) {
			throw new IllegalArgumentException("SKU not found: " + skuId);
		}

		Sku sku = existing.get();
		sku.disable(); // Domain behavior

		skuRepository.save(sku);
	}

	/**
	 * Delete SKU
	 */
	@Transactional
	@CacheEvict(value = {"skus", "product-skus"}, allEntries = true)
	public void deleteSku(SkuId skuId) {
		String shopId = shopContext.getShopId();
		log.info("Deleting SKU: {} for shop: {}", skuId, shopId);

		skuRepository.delete(skuId);
	}
}

package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductStatus;
import com.github.spud.tinystore.product.domain.repository.ProductRepository;
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
 * ProductService - Application service for Product aggregate Coordinates domain logic, persistence,
 * and caching
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;
	private final ShopRepositoryConfig.ShopContext shopContext;

	/**
	 * Create a new product (Draft status)
	 */
	@Transactional
	@CacheEvict(value = "products", allEntries = true)
	public Product createProduct(Product product) {
		String shopId = shopContext.getShopId();
		log.info("Creating product for shop: {}, productId: {}", shopId, product.getProductId());

		return productRepository.save(product);
	}

	/**
	 * Update product attributes (only in DRAFT or OFFLINE status)
	 */
	@Transactional
	@CacheEvict(value = "products", key = "#productId.id")
	public Product updateProduct(ProductId productId, Product updatedProduct) {
		String shopId = shopContext.getShopId();
		log.info("Updating product: {} for shop: {}", productId, shopId);

		Optional<Product> existing = productRepository.findById(productId);
		if (existing.isEmpty()) {
			throw new IllegalArgumentException("Product not found: " + productId);
		}

		Product product = existing.get();

		// Domain logic: validate modifiable status
		if (product.getStatus() != ProductStatus.DRAFT
			&& product.getStatus() != ProductStatus.OFFLINE) {
			throw new IllegalArgumentException("Only DRAFT or OFFLINE products can be modified");
		}

		// Update attributes via domain method
		product.updateAttributes(updatedProduct.getBaseAttributes());

		return productRepository.save(product);
	}

	/**
	 * Publish product (transition to PUBLISHED status)
	 */
	@Transactional
	@CacheEvict(value = "products", key = "#productId.id")
	public Product publishProduct(ProductId productId) {
		String shopId = shopContext.getShopId();
		log.info("Publishing product: {} for shop: {}", productId, shopId);

		Optional<Product> existing = productRepository.findById(productId);
		if (existing.isEmpty()) {
			throw new IllegalArgumentException("Product not found: " + productId);
		}

		Product product = existing.get();

		// Domain logic: transition to published
		product.publish();
		return productRepository.save(product);
	}

	/**
	 * Archive product (soft delete)
	 */
	@Transactional
	@CacheEvict(value = "products", key = "#productId.id")
	public void archiveProduct(ProductId productId) {
		String shopId = shopContext.getShopId();
		log.info("Archiving product: {} for shop: {}", productId, shopId);

		Optional<Product> existing = productRepository.findById(productId);
		if (existing.isEmpty()) {
			throw new IllegalArgumentException("Product not found: " + productId);
		}

		productRepository.delete(productId);
	}

	/**
	 * Get product by ID (with caching)
	 */
	@Cacheable(value = "products", key = "#productId.id")
	public Optional<Product> getProduct(ProductId productId) {
		String shopId = shopContext.getShopId();
		log.debug("Getting product: {} for shop: {}", productId, shopId);

		return productRepository.findById(productId);
	}

	/**
	 * Update product tags
	 */
	@Transactional
	@CacheEvict(value = "products", key = "#productId.id")
	public Product updateTags(ProductId productId, List<String> tags) {
		String shopId = shopContext.getShopId();
		log.info("Updating tags for product: {} for shop: {}", productId, shopId);

		Optional<Product> existing = productRepository.findById(productId);
		if (existing.isEmpty()) {
			throw new IllegalArgumentException("Product not found: " + productId);
		}

		Product product = existing.get();

		// TODO: Add updateTags method to Product aggregate
		// For now, just save
		return productRepository.save(product);
	}
}

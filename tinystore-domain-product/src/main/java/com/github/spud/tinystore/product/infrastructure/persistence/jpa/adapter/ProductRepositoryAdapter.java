package com.github.spud.tinystore.product.infrastructure.persistence.jpa.adapter;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.repository.ProductRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.ShopRepositoryConfig;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.ProductEntity;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper.ProductMapper;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaProductRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * ProductRepositoryAdapter - Adapts JPA repository to domain repository interface
 * <p>
 * Responsibilities: - Implement domain ProductRepository interface - Delegate to
 * JpaProductRepository for persistence - Use ProductMapper for entity/domain conversions - Inject
 * tenant ID from context for all operations
 */
@Repository
public class ProductRepositoryAdapter implements ProductRepository {

	private final JpaProductRepository jpaRepository;
	private final ProductMapper mapper;
	private final ShopRepositoryConfig.ShopContext shopContext;

	public ProductRepositoryAdapter(
		JpaProductRepository jpaRepository,
		ProductMapper mapper,
		ShopRepositoryConfig.ShopContext shopContext) {
		this.jpaRepository = jpaRepository;
		this.mapper = mapper;
		this.shopContext = shopContext;
	}

	@Override
	public Product save(Product product) {
		String shopId = shopContext.getShopId();
		ProductEntity entity = mapper.toEntity(product, shopId);
		if (entity.getProductId() != null) {
			jpaRepository.findByProductIdAndShopId(entity.getProductId(), shopId)
				.ifPresent(existing -> entity.setId(existing.getId()));
		}
		ProductEntity saved = jpaRepository.save(entity);
		return mapper.toDomain(saved);
	}

	@Override
	public Optional<Product> findById(ProductId productId) {
		String shopId = shopContext.getShopId();
		// Use productId.getId() to get the string ID
		Optional<ProductEntity> entity = jpaRepository.findByProductIdAndShopId(
			productId.getId(), shopId);
		return entity.map(mapper::toDomain);
	}

	@Override
	public void delete(ProductId productId) {
		String shopId = shopContext.getShopId();
		// Soft delete: find and mark as deleted
		Optional<ProductEntity> entity = jpaRepository.findByProductIdAndShopId(
			productId.getId(), shopId);
		entity.ifPresent(jpaRepository::delete);
	}
}

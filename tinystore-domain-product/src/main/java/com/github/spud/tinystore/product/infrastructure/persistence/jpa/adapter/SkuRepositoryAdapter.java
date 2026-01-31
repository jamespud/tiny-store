package com.github.spud.tinystore.product.infrastructure.persistence.jpa.adapter;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.ShopRepositoryConfig;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.SkuEntity;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper.SkuMapper;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaSkuRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * SkuRepositoryAdapter - Adapts JPA repository to domain SKU repository
 */
@Repository
public class SkuRepositoryAdapter implements SkuRepository {

	private final JpaSkuRepository jpaRepository;
	private final SkuMapper mapper;
	private final ShopRepositoryConfig.ShopContext shopContext;

	public SkuRepositoryAdapter(
		JpaSkuRepository jpaRepository,
		SkuMapper mapper,
		ShopRepositoryConfig.ShopContext shopContext) {
		this.jpaRepository = jpaRepository;
		this.mapper = mapper;
		this.shopContext = shopContext;
	}

	@Override
	public Sku save(Sku sku) {
		String shopId = shopContext.getShopId();
		SkuEntity entity = mapper.toEntity(sku, shopId);
		SkuEntity saved = jpaRepository.save(entity);
		return mapper.toDomain(saved);
	}

	@Override
	public Optional<Sku> findById(SkuId skuId) {
		String shopId = shopContext.getShopId();
		return jpaRepository.findByShopIdAndSkuId(shopId, skuId.getId()).map(mapper::toDomain);
	}

	@Override
	public List<Sku> findByProductId(ProductId productId) {
		String shopId = shopContext.getShopId();
		List<SkuEntity> entities = jpaRepository.findByShopIdAndProductId(shopId,
			productId.getId());
		return entities.stream()
			.map(mapper::toDomain)
			.collect(Collectors.toList());
	}

	@Override
	public void delete(SkuId skuId) {
		String shopId = shopContext.getShopId();
		jpaRepository.findByShopIdAndSkuId(shopId, skuId.getId()).ifPresent(jpaRepository::delete);
	}
}

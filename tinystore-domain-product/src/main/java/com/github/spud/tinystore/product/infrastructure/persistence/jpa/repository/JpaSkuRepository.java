package com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.SkuEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * JpaSkuRepository - Spring Data JPA repository for SkuEntity
 * <p>
 * Query methods must always include shop_id condition for multi-tenancy isolation
 * <p>
 * Derived query methods: - findByShopIdAndId: Find SKU by shop and ID -
 * findByShopIdAndProductId: Find all SKUs for a product -
 * findByShopIdAndProductIdAndSpecCombination: Find SKU by unique spec combination
 * <p>
 * Note: spec_combination must be normalized before query to ensure uniqueness
 */
@Repository
public interface JpaSkuRepository extends JpaRepository<SkuEntity, Long>,
	JpaSpecificationExecutor<SkuEntity> {

	/**
	 * Find SKU by shop ID and entity ID
	 *
	 * @param shopId Shop identifier
	 * @param id       Entity primary key
	 * @return Optional SKU entity
	 */
	Optional<SkuEntity> findByShopIdAndId(String shopId, Long id);

	/**
	 * Find all SKUs for a product within shop
	 *
	 * @param shopId  Shop identifier
	 * @param productId Product identifier
	 * @return List of SKU entities
	 */
	List<SkuEntity> findByShopIdAndProductId(String shopId, String productId);

	Optional<SkuEntity> findByShopIdAndSkuId(String shopId, String skuId);

	List<SkuEntity> findByShopIdAndSkuIdIn(String shopId, Collection<String> skuIds);

	/**
	 * Find SKU by unique product and spec combination Enforces uniqueness constraint uk_product_spec
	 *
	 * @param shopId        Shop identifier
	 * @param productId       Product identifier
	 * @param specCombination Normalized specification combination string
	 * @return Optional SKU entity
	 */
	Optional<SkuEntity> findByShopIdAndProductIdAndSpecCombination(
		String shopId, String productId, String specCombination);
}

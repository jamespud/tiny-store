package com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.ProductEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * JpaProductRepository - Spring Data JPA repository for ProductEntity
 * <p>
 * Query methods must always include shop_id condition for multi-tenancy isolation Use
 * JpaSpecificationExecutor for complex queries with shop predicate injection
 * <p>
 * Derived query methods: - findByShopIdAndId: Find product by shop and ID -
 * findByShopIdAndCategoryId: Find products by shop and category - findByShopIdAndStatus: Find
 * products by shop and status
 * <p>
 * Complex queries should use Specification with BaseRepository shop injection
 */
@Repository
public interface JpaProductRepository extends JpaRepository<ProductEntity, Long>,
	JpaSpecificationExecutor<ProductEntity> {

	/**
	 * Find product by shop ID and entity ID
	 *
	 * @param shopId Shop identifier
	 * @param id       Entity primary key
	 * @return Optional product entity
	 */
	Optional<ProductEntity> findByShopIdAndId(String shopId, Long id);

	/**
	 * Find product by shop ID and product ID (domain ID string)
	 *
	 * @param productId Product domain identifier (string)
	 * @param shopId  Shop identifier
	 * @return Optional product entity
	 */
	Optional<ProductEntity> findByProductIdAndShopId(String productId, String shopId);

	/**
	 * Find all products by shop and category
	 *
	 * @param shopId   Shop identifier
	 * @param categoryId Category identifier
	 * @return List of product entities
	 */
	List<ProductEntity> findByShopIdAndCategoryId(String shopId, String categoryId);

	/**
	 * Find all products by shop and status
	 *
	 * @param shopId Shop identifier
	 * @param status   Product status
	 * @return List of product entities
	 */
	List<ProductEntity> findByShopIdAndStatus(String shopId, String status);
}

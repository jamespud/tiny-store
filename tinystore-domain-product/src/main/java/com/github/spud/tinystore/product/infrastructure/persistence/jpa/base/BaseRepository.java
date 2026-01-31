package com.github.spud.tinystore.product.infrastructure.persistence.jpa.base;

import org.springframework.data.jpa.domain.Specification;

/**
 * BaseRepository - Provides shop isolation utilities for JPA repositories
 * <p>
 * All queries through JPA repositories should use these methods to ensure shop_id condition is
 * always applied for multi-tenancy data isolation.
 * <p>
 * Usage pattern: 1. Obtain current shopId from ShopContext 2. Apply withShopId specification
 * to all queries 3. Verify at query time that shopId is not null
 * <p>
 * Static analysis (SonarQube custom rule) should verify that all JPQL queries include shop_id in
 * WHERE clause.
 */
public class BaseRepository {

	/**
	 * Create a Specification that adds shop_id predicate
	 *
	 * @param <T>      Entity type
	 * @param shopId Shop identifier (must not be null)
	 * @return Specification that adds shop_id = ? condition
	 */
	public static <T> Specification<T> withShopId(String shopId) {
		if (shopId == null || shopId.isBlank()) {
			throw new IllegalStateException("ShopId must not be null for data access");
		}

		return (root, query, criteriaBuilder) ->
			criteriaBuilder.equal(root.get("shopId"), shopId);
	}

	/**
	 * Combine shop specification with additional criteria
	 *
	 * @param <T>            Entity type
	 * @param shopId       Shop identifier
	 * @param additionalSpec Additional specification to AND with shop condition
	 * @return Combined specification
	 */
	public static <T> Specification<T> withShopIdAnd(String shopId,
		Specification<T> additionalSpec) {
		Specification<T> shopSpec = withShopId(shopId);
		if (additionalSpec == null) {
			return shopSpec;
		}
		return shopSpec.and(additionalSpec);
	}
}

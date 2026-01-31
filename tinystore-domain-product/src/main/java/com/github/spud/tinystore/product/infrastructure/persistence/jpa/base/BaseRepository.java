package com.github.spud.tinystore.product.infrastructure.persistence.jpa.base;

import org.springframework.data.jpa.domain.Specification;

/**
 * BaseRepository - Provides tenant isolation utilities for JPA repositories
 * <p>
 * All queries through JPA repositories should use these methods to ensure tenant_id condition is
 * always applied for multi-tenancy data isolation.
 * <p>
 * Usage pattern: 1. Obtain current shopId from ShopContext 2. Apply withShopId specification
 * to all queries 3. Verify at query time that shopId is not null
 * <p>
 * Static analysis (SonarQube custom rule) should verify that all JPQL queries include tenant_id in
 * WHERE clause.
 */
public class BaseRepository {

	/**
	 * Create a Specification that adds tenant_id predicate
	 *
	 * @param <T>      Entity type
	 * @param shopId Shop identifier (must not be null)
	 * @return Specification that adds tenant_id = ? condition
	 */
	public static <T> Specification<T> withShopId(String shopId) {
		if (shopId == null || shopId.isBlank()) {
			throw new IllegalStateException("TenantId must not be null for data access");
		}

		return (root, query, criteriaBuilder) ->
			criteriaBuilder.equal(root.get("shopId"), shopId);
	}

	/**
	 * Combine tenant specification with additional criteria
	 *
	 * @param <T>            Entity type
	 * @param shopId       Shop identifier
	 * @param additionalSpec Additional specification to AND with tenant condition
	 * @return Combined specification
	 */
	public static <T> Specification<T> withShopIdAnd(String shopId,
		Specification<T> additionalSpec) {
		Specification<T> tenantSpec = withShopId(shopId);
		if (additionalSpec == null) {
			return tenantSpec;
		}
		return tenantSpec.and(additionalSpec);
	}
}

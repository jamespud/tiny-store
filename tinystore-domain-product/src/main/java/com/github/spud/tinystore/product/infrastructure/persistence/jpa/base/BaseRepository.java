package com.github.spud.tinystore.product.infrastructure.persistence.jpa.base;

import org.springframework.data.jpa.domain.Specification;

/**
 * BaseRepository - Provides tenant isolation utilities for JPA repositories
 * <p>
 * All queries through JPA repositories should use these methods to ensure tenant_id condition is
 * always applied for multi-tenancy data isolation.
 * <p>
 * Usage pattern: 1. Obtain current tenantId from TenantContext 2. Apply withTenantId specification
 * to all queries 3. Verify at query time that tenantId is not null
 * <p>
 * Static analysis (SonarQube custom rule) should verify that all JPQL queries include tenant_id in
 * WHERE clause.
 */
public class BaseRepository {

	/**
	 * Create a Specification that adds tenant_id predicate
	 *
	 * @param <T>      Entity type
	 * @param tenantId Tenant identifier (must not be null)
	 * @return Specification that adds tenant_id = ? condition
	 */
	public static <T> Specification<T> withTenantId(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalStateException("TenantId must not be null for data access");
		}

		return (root, query, criteriaBuilder) ->
			criteriaBuilder.equal(root.get("tenantId"), tenantId);
	}

	/**
	 * Combine tenant specification with additional criteria
	 *
	 * @param <T>            Entity type
	 * @param tenantId       Tenant identifier
	 * @param additionalSpec Additional specification to AND with tenant condition
	 * @return Combined specification
	 */
	public static <T> Specification<T> withTenantIdAnd(String tenantId,
		Specification<T> additionalSpec) {
		Specification<T> tenantSpec = withTenantId(tenantId);
		if (additionalSpec == null) {
			return tenantSpec;
		}
		return tenantSpec.and(additionalSpec);
	}
}

package com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.PricingRuleEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JpaPricingRuleRepository - Spring Data JPA repository for PricingRuleEntity
 * <p>
 * Query methods for complex rule filtering: - By tenant, product, category, tags - By effective
 * time window - By status and priority
 * <p>
 * All queries must include tenant_id for multi-tenancy isolation
 */
@Repository
public interface JpaPricingRuleRepository extends JpaRepository<PricingRuleEntity, Long>,
	JpaSpecificationExecutor<PricingRuleEntity> {

	/**
	 * Find pricing rule by tenant and rule code Enforces uniqueness constraint uk_tenant_rule_code
	 *
	 * @param tenantId Tenant identifier
	 * @param ruleCode Unique rule code within tenant
	 * @return Optional pricing rule entity
	 */
	Optional<PricingRuleEntity> findByTenantIdAndRuleCode(String tenantId, String ruleCode);

	/**
	 * Find active pricing rules for a product within time window
	 *
	 * @param tenantId    Tenant identifier
	 * @param productId   Product identifier (null for category/global rules)
	 * @param currentTime Current time for window check
	 * @return List of active pricing rule entities ordered by priority desc
	 */
	@Query("SELECT r FROM PricingRuleEntity r WHERE r.tenantId = :tenantId " +
		"AND (r.productId = :productId OR r.productId IS NULL) " +
		"AND r.status = 'ACTIVE' " +
		"AND r.effectiveTime <= :currentTime " +
		"AND (r.expireTime IS NULL OR r.expireTime > :currentTime) " +
		"ORDER BY r.priority DESC")
	List<PricingRuleEntity> findActiveRulesByProduct(
		@Param("tenantId") String tenantId,
		@Param("productId") String productId,
		@Param("currentTime") LocalDateTime currentTime);

	/**
	 * Find active pricing rules for a category within time window
	 *
	 * @param tenantId    Tenant identifier
	 * @param categoryId  Category identifier
	 * @param currentTime Current time for window check
	 * @return List of active pricing rule entities ordered by priority desc
	 */
	@Query("SELECT r FROM PricingRuleEntity r WHERE r.tenantId = :tenantId " +
		"AND (r.categoryId = :categoryId OR r.categoryId IS NULL) " +
		"AND r.status = 'ACTIVE' " +
		"AND r.effectiveTime <= :currentTime " +
		"AND (r.expireTime IS NULL OR r.expireTime > :currentTime) " +
		"ORDER BY r.priority DESC")
	List<PricingRuleEntity> findActiveRulesByCategory(
		@Param("tenantId") String tenantId,
		@Param("categoryId") String categoryId,
		@Param("currentTime") LocalDateTime currentTime);

	/**
	 * Find all active pricing rules within tenant and time window Used for batch pricing or preload
	 *
	 * @param tenantId    Tenant identifier
	 * @param currentTime Current time for window check
	 * @return List of all active pricing rule entities
	 */
	@Query("SELECT r FROM PricingRuleEntity r WHERE r.tenantId = :tenantId " +
		"AND r.status = 'ACTIVE' " +
		"AND r.effectiveTime <= :currentTime " +
		"AND (r.expireTime IS NULL OR r.expireTime > :currentTime) " +
		"ORDER BY r.priority DESC")
	List<PricingRuleEntity> findAllActiveRules(
		@Param("tenantId") String tenantId,
		@Param("currentTime") LocalDateTime currentTime);
}

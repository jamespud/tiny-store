package com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JpaProductRepository - Spring Data JPA repository for ProductEntity
 * 
 * Query methods must always include tenant_id condition for multi-tenancy isolation
 * Use JpaSpecificationExecutor for complex queries with tenant predicate injection
 * 
 * Derived query methods:
 * - findByTenantIdAndId: Find product by tenant and ID
 * - findByTenantIdAndCategoryId: Find products by tenant and category
 * - findByTenantIdAndStatus: Find products by tenant and status
 * 
 * Complex queries should use Specification with BaseRepository tenant injection
 */
@Repository
public interface JpaProductRepository extends JpaRepository<ProductEntity, Long>, 
                                               JpaSpecificationExecutor<ProductEntity> {
    
    /**
     * Find product by tenant ID and entity ID
     * 
     * @param tenantId Tenant identifier
     * @param id Entity primary key
     * @return Optional product entity
     */
    Optional<ProductEntity> findByTenantIdAndId(String tenantId, Long id);
    
    /**
     * Find product by tenant ID and product ID (domain ID string)
     * 
     * @param productId Product domain identifier (string)
     * @param tenantId Tenant identifier
     * @return Optional product entity
     */
    Optional<ProductEntity> findByProductIdAndTenantId(String productId, String tenantId);
    
    /**
     * Find all products by tenant and category
     * 
     * @param tenantId Tenant identifier
     * @param categoryId Category identifier
     * @return List of product entities
     */
    List<ProductEntity> findByTenantIdAndCategoryId(String tenantId, String categoryId);
    
    /**
     * Find all products by tenant and status
     * 
     * @param tenantId Tenant identifier
     * @param status Product status
     * @return List of product entities
     */
    List<ProductEntity> findByTenantIdAndStatus(String tenantId, String status);
}

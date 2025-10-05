package com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/**
 * PricingRuleEntity - JPA entity for pricing rule persistence
 * 
 * Fields:
 * - id: Primary key
 * - tenantId: Multi-tenancy identifier
 * - ruleCode: Unique rule code (unique within tenant)
 * - priority: Rule priority (higher number = higher priority)
 * - exclusiveGroup: Exclusive group identifier (rules in same group conflict)
 * - productId: Product ID filter (null = all products)
 * - categoryId: Category ID filter (null = all categories)
 * - userTags: User tag filters (JSON array)
 * - content: Rule content stored as JSONB
 * - effectiveTime: Rule becomes effective at this time
 * - expireTime: Rule expires at this time
 * - status: Rule status (ACTIVE, INACTIVE, EXPIRED)
 * - version: Optimistic locking version
 * - createdAt: Creation timestamp
 * - updatedAt: Last update timestamp
 * 
 * Unique constraint:
 * - uk_tenant_rule_code: (tenant_id, rule_code)
 * 
 * Indexes:
 * - idx_tenant_product_category_status_time: For efficient rule queries
 */
@Entity
@Table(name = "pricing_rule", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tenant_rule_code", columnNames = {"tenant_id", "rule_code"})
})
@Data
public class PricingRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @Column(name = "rule_code", nullable = false)
    private String ruleCode;
    
    @Column(name = "priority", nullable = false)
    private Integer priority;
    
    @Column(name = "exclusive_group")
    private String exclusiveGroup;
    
    @Column(name = "product_id")
    private String productId;
    
    @Column(name = "category_id")
    private String categoryId;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_tags", columnDefinition = "jsonb")
    private String userTags;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    private String content;
    
    @Column(name = "effective_time")
    private LocalDateTime effectiveTime;
    
    @Column(name = "expire_time")
    private LocalDateTime expireTime;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

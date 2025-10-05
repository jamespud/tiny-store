package com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SkuEntity - JPA entity for SKU persistence
 * 
 * Fields:
 * - id: Primary key
 * - productId: Foreign key to product
 * - tenantId: Multi-tenancy identifier
 * - specCombination: Specification combination string (normalized, for uniqueness)
 * - price: SKU price
 * - stock: Available stock quantity
 * - barCode: Bar code
 * - status: SKU status (AVAILABLE, DISABLED)
 * - version: Optimistic locking version
 * - createdAt: Creation timestamp
 * - updatedAt: Last update timestamp
 * 
 * Unique constraint:
 * - uk_product_spec: (product_id, spec_combination)
 * 
 * Index:
 * - idx_product_spec: (product_id, spec_combination) for fast lookup
 */
@Entity
@Table(name = "sku", uniqueConstraints = {
    @UniqueConstraint(name = "uk_product_spec", columnNames = {"product_id", "spec_combination"})
})
@Data
public class SkuEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "product_id", nullable = false)
    private String productId;
    
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @Column(name = "spec_combination", nullable = false)
    private String specCombination;
    
    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;
    
    @Column(name = "stock")
    private Integer stock;
    
    @Column(name = "bar_code")
    private String barCode;
    
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

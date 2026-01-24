package com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * SkuEntity - JPA entity for SKU persistence
 * <p>
 * Fields: - id: Primary key - productId: Foreign key to product - tenantId: Multi-tenancy
 * identifier - specCombination: Specification combination string (normalized, for uniqueness) -
 * price: SKU price - stock: Available stock quantity - barCode: Bar code - status: SKU status
 * (AVAILABLE, DISABLED) - version: Optimistic locking version - createdAt: Creation timestamp -
 * updatedAt: Last update timestamp
 * <p>
 * Unique constraint: - uk_product_spec: (product_id, spec_combination)
 * <p>
 * Index: - idx_product_spec: (product_id, spec_combination) for fast lookup
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

	@Column(name = "sku_id", nullable = false)
	private String skuId;

	@Column(name = "merchant_id")
	private String merchantId;

	@Column(name = "sku_name")
	private String skuName;

	@Column(name = "spec_json", columnDefinition = "jsonb")
	private String specJson;

	@Column(name = "weight_grams")
	private Long weightGrams;

	@Column(name = "unit_price_cents")
	private Long unitPriceCents;

	@Column(name = "promote_price_cents")
	private Long promotePriceCents;

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

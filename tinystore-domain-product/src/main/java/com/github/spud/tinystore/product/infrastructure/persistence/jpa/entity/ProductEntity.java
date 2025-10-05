package com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * ProductEntity - JPA entity for product persistence
 * <p>
 * Fields: - id: Primary key - tenantId: Multi-tenancy identifier (mandatory for all queries) -
 * name: Product name - status: Product status (DRAFT, PUBLISHED, ARCHIVED) - categoryId: Category
 * reference - version: Optimistic locking version - createdAt: Creation timestamp - updatedAt: Last
 * update timestamp
 * <p>
 * Indexes: - idx_tenant_category_status: (tenant_id, category_id, status)
 */
@Entity
@Table(name = "product")
@Data
public class ProductEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false)
	private String tenantId;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "category_id")
	private String categoryId;

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

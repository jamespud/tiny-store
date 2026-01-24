package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Entity
@Table(name = "inventory_adjustment", schema = "tinystore_inventory")
@Data
@Accessors(chain = true)
public class InventoryAdjustmentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 100)
	private String tenantId;

	@Column(name = "sku_id", nullable = false, length = 128)
	private String skuId;

	@Column(name = "delta_total", nullable = false)
	private long deltaTotal;

	@Column(name = "reason", nullable = false, length = 64)
	private String reason;

	@Column(name = "reference_id", length = 128)
	private String referenceId;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@PrePersist
	public void prePersist() {
		this.createdAt = OffsetDateTime.now();
	}
}


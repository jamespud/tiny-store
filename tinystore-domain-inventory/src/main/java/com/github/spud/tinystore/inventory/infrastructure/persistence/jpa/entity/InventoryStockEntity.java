package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Entity
@Table(name = "inventory_stock", schema = "tinystore_inventory")
@Data
@Accessors(chain = true)
public class InventoryStockEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "shop_id", nullable = false, length = 100)
	private String shopId;

	@Column(name = "sku_id", nullable = false, length = 128)
	private String skuId;

	@Column(name = "total_quantity", nullable = false)
	private long totalQuantity;

	@Column(name = "reserved_quantity", nullable = false)
	private long reservedQuantity;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@PrePersist
	public void prePersist() {
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = OffsetDateTime.now();
	}
}


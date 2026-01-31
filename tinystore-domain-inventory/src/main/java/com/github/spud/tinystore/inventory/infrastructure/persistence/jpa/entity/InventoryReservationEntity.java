package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Entity
@Table(name = "inventory_reservation", schema = "tinystore_inventory")
@Data
@Accessors(chain = true)
public class InventoryReservationEntity {

	@Id
	@Column(name = "reservation_id", nullable = false, length = 64)
	private String reservationId;

	@Column(name = "tenant_id", nullable = false, length = 100)
	private String tenantId;

	@Column(name = "sku_id", nullable = false, length = 128)
	private String skuId;

	@Column(name = "quantity", nullable = false)
	private long quantity;

	@Column(name = "status", nullable = false, length = 32)
	private String status;

	@Column(name = "expire_at", nullable = false)
	private OffsetDateTime expireAt;

	@Column(name = "trade_id", nullable = false, length = 64)
	private String tradeId;

	@Column(name = "operation_id", nullable = false, length = 128)
	private String operationId;

	@Column(name = "release_reason", length = 255)
	private String releaseReason;

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


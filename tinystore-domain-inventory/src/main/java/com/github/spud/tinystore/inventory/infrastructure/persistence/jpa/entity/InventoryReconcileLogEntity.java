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
@Table(name = "inventory_reconcile_log", schema = "tinystore_inventory")
@Data
@Accessors(chain = true)
public class InventoryReconcileLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "shop_id", nullable = false, length = 100)
	private String shopId;

	@Column(name = "sku_id", nullable = false, length = 128)
	private String skuId;

	@Column(name = "db_total", nullable = false)
	private long dbTotal;

	@Column(name = "db_confirmed", nullable = false)
	private long dbConfirmed;

	@Column(name = "db_pre_deducted", nullable = false)
	private long dbPreDeducted;

	@Column(name = "redis_total_before")
	private Long redisTotalBefore;

	@Column(name = "redis_deducted_before")
	private Long redisDeductedBefore;

	@Column(name = "target_total", nullable = false)
	private long targetTotal;

	@Column(name = "target_deducted", nullable = false)
	private long targetDeducted;

	@Column(name = "action", nullable = false, length = 32)
	private String action;

	@Column(name = "repaired_fields", length = 64)
	private String repairedFields;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@PrePersist
	public void prePersist() {
		this.createdAt = OffsetDateTime.now();
	}
}

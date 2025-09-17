package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventory_reservation", indexes = {
	@Index(name = "idx_resv_state_expire", columnList = "state,expire_at")
}, uniqueConstraints = {
	@UniqueConstraint(name = "uk_resv_operation", columnNames = {"operation_id"})
})
@Getter
@Setter
public class ReservationEntity {

	@Id
	@Column(name = "reservation_id", nullable = false, length = 64)
	private String reservationId;

	@Column(name = "shop_id", nullable = false)
	private String shopId;

	@Column(name = "sku_id", nullable = false)
	private String skuId;

	@Column(name = "quantity", nullable = false)
	private long quantity;

	@Column(name = "state", nullable = false)
	private String state;

	@Column(name = "expire_at", nullable = false)
	private Instant expireAt;

	@Column(name = "operation_id")
	private String operationId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "release_reason")
	private String releaseReason;
}


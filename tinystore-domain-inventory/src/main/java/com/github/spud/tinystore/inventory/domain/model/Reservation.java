package com.github.spud.tinystore.inventory.domain.model;

import com.github.spud.tinystore.inventory.domain.exception.InventoryBusinessException;
import com.github.spud.tinystore.inventory.domain.exception.InventoryErrorCode;
import java.time.Instant;
import java.util.Objects;

/**
 * Reservation 子实体/独立聚合（单表持久化）。
 */
public class Reservation {

	private final String reservationId;
	private final String shopId;
	private final String skuId;
	private final long quantity;
	private ReservationState state;
	private final Instant expireAt;
	private final String operationId; // 幂等标识（可空）
	private final Instant createdAt;
	private Instant updatedAt;
	private long version;
	private String releaseReason;

	public Reservation(String reservationId, String shopId, String skuId, long quantity,
		ReservationState state,
		Instant expireAt, String operationId, Instant createdAt, Instant updatedAt, long version) {
		this.reservationId = Objects.requireNonNull(reservationId);
		this.shopId = Objects.requireNonNull(shopId);
		this.skuId = Objects.requireNonNull(skuId);
		this.quantity = quantity;
		this.state = state;
		this.expireAt = expireAt;
		this.operationId = operationId;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.version = version;
		validate();
	}

	private void validate() {
		if (quantity <= 0) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_ARG_INVALID,
				"reservation 数量必须>0");
		}
		if (expireAt.isBefore(createdAt)) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_ARG_INVALID,
				"expireAt 早于 createdAt");
		}
	}

	public void markConfirmed() {
		transition(ReservationState.CONFIRMED);
	}

	public void markReleased(String reason) {
		this.releaseReason = reason;
		transition(ReservationState.RELEASED);
	}

	public void markExpired() {
		transition(ReservationState.EXPIRED);
	}

	private void transition(ReservationState target) {
		if (state != ReservationState.PENDING) {
			if (state == target) {
				return; // 幂等
			}
			throw new InventoryBusinessException(InventoryErrorCode.INV_RESERVATION_STATE_INVALID,
				"非法状态迁移: " + state + " -> " + target);
		}
		state = target;
		updatedAt = Instant.now();
		// version 增加由仓储 updateState 完成
	}

	public boolean isExpired(Instant now) {
		return now.isAfter(expireAt) || now.equals(expireAt);
	}

	public String getReservationId() {
		return reservationId;
	}

	public String getShopId() {
		return shopId;
	}

	public String getSkuId() {
		return skuId;
	}

	public long getQuantity() {
		return quantity;
	}

	public ReservationState getState() {
		return state;
	}

	public Instant getExpireAt() {
		return expireAt;
	}

	public String getOperationId() {
		return operationId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

	public String getReleaseReason() {
		return releaseReason;
	}
}

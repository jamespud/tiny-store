package com.github.spud.tinystore.inventory.domain.model;

import com.github.spud.tinystore.inventory.domain.event.DomainEvent;
import com.github.spud.tinystore.inventory.domain.event.ReservationConfirmedEvent;
import com.github.spud.tinystore.inventory.domain.event.ReservationExpiredEvent;
import com.github.spud.tinystore.inventory.domain.event.ReservationReleasedEvent;
import com.github.spud.tinystore.inventory.domain.event.StockAdjustedEvent;
import com.github.spud.tinystore.inventory.domain.exception.InventoryBusinessException;
import com.github.spud.tinystore.inventory.domain.exception.InventoryErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 聚合根：库存 + 预留总览。
 */
public class StockAggregate {

	private final String shopId;
	private final String skuId;
	private long totalQuantity;
	private long reservedQuantity;
	private long version;
	// === 新增：领域事件暂存 ===
	private final List<DomainEvent> domainEvents = new ArrayList<>();

	public StockAggregate(String shopId, String skuId, long totalQuantity, long reservedQuantity,
		long version) {
		this.shopId = Objects.requireNonNull(shopId);
		this.skuId = Objects.requireNonNull(skuId);
		this.totalQuantity = totalQuantity;
		this.reservedQuantity = reservedQuantity;
		this.version = version;
		validateInvariants();
	}

	private void validateInvariants() {
		if (totalQuantity < 0 || reservedQuantity < 0 || reservedQuantity > totalQuantity) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_ARG_INVALID, "库存不变量被破坏");
		}
	}

	public long getAvailable() {
		return totalQuantity - reservedQuantity;
	}

	public Reservation reserve(long quantity, int expireSeconds, String operationId) {
		if (quantity <= 0) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_ARG_INVALID, "预留数量必须>0");
		}
		if (getAvailable() < quantity) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_AVAILABLE_NOT_ENOUGH,
				"可用库存不足");
		}
		reservedQuantity += quantity;
		Instant now = Instant.now();
		Instant expireAt = now.plusSeconds(expireSeconds <= 0 ? 300 : expireSeconds);
		return new Reservation(UUID.randomUUID().toString(), shopId, skuId, quantity,
			ReservationState.PENDING, expireAt, operationId, now, now, 0L);
	}

	public void confirm(Reservation reservation) {
		if (reservation.getState() != ReservationState.PENDING) {
			return;
		}
		if (reservation.getQuantity() > reservedQuantity) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_RESERVATION_STATE_INVALID,
				"预留量大于聚合记录的预留");
		}
		reservation.markConfirmed();
		reservedQuantity -= reservation.getQuantity();
		totalQuantity -= reservation.getQuantity();
		validateInvariants();
		registerEvent(new ReservationConfirmedEvent(shopId, skuId, reservation.getReservationId(),
			reservation.getQuantity()));
	}

	public void release(Reservation reservation, String reason) {
		if (reservation.getState() != ReservationState.PENDING) {
			return;
		}
		reservation.markReleased(reason);
		reservedQuantity -= reservation.getQuantity();
		validateInvariants();
		registerEvent(new ReservationReleasedEvent(shopId, skuId, reservation.getReservationId(),
			reservation.getQuantity(), reason));
	}

	public void expire(Reservation reservation) {
		if (reservation.getState() != ReservationState.PENDING) {
			return;
		}
		reservation.markExpired();
		reservedQuantity -= reservation.getQuantity();
		validateInvariants();
		registerEvent(new ReservationExpiredEvent(shopId, skuId, reservation.getReservationId(),
			reservation.getQuantity()));
	}

	// 原 adjustTotal 保留，同时注册事件
	public void adjustTotal(long delta, String reason) {
		if (delta == 0) {
			return;
		}
		long newTotal = totalQuantity + delta;
		if (newTotal < 0) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_ADJUST_ILLEGAL, "调整后总量<0");
		}
		long newAvailable = newTotal - reservedQuantity;
		if (newAvailable < 0) {
			throw new InventoryBusinessException(InventoryErrorCode.INV_ADJUST_ILLEGAL, "调整后可用<0");
		}
		totalQuantity = newTotal;
		registerEvent(new StockAdjustedEvent(shopId, skuId, delta, reason));
	}

	// === 新增语义化包装 ===
	public void incrTotal(long delta, String reason) {
		if (delta <= 0) {
			throw new IllegalArgumentException();
		}
		adjustTotal(delta, reason);
	}

	public void decrTotal(long delta, String reason) {
		if (delta <= 0) {
			throw new IllegalArgumentException();
		}
		adjustTotal(-delta, reason);
	}

	public void adjustReservedOnExpire(long quantity) {
		if (quantity <= 0) {
			return; /* 已在 expire 调用时处理，这里占位保留 */
		}
	}

	private void registerEvent(DomainEvent e) {
		this.domainEvents.add(e);
	}

	public List<DomainEvent> getDomainEvents() {
		return Collections.unmodifiableList(domainEvents);
	}

	public void clearDomainEvents() {
		domainEvents.clear();
	}

	public String getShopId() {
		return shopId;
	}

	public String getSkuId() {
		return skuId;
	}

	public long getTotalQuantity() {
		return totalQuantity;
	}

	public long getReservedQuantity() {
		return reservedQuantity;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}

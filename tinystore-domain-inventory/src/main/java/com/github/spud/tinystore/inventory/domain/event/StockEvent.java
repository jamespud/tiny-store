package com.github.spud.tinystore.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 库存领域事件数据结构
 */
public class StockEvent {

	private final String eventId = UUID.randomUUID().toString();
	private final StockEventType type;
	private final String shopId;
	private final String skuId;
	private final String reservationId;
	private final Long deltaTotal;
	private final Long deltaReserved;
	private final Long quantity;
	private final long version;
	private final String correlationId;
	private final Instant occurredAt = Instant.now();

	public StockEvent(StockEventType type, String shopId, String skuId, String reservationId,
		Long deltaTotal, Long deltaReserved, Long quantity, long version, String correlationId) {
		this.type = type;
		this.shopId = shopId;
		this.skuId = skuId;
		this.reservationId = reservationId;
		this.deltaTotal = deltaTotal;
		this.deltaReserved = deltaReserved;
		this.quantity = quantity;
		this.version = version;
		this.correlationId = correlationId;
	}

	public String getEventId() {
		return eventId;
	}

	public StockEventType getType() {
		return type;
	}

	public String getShopId() {
		return shopId;
	}

	public String getSkuId() {
		return skuId;
	}

	public String getReservationId() {
		return reservationId;
	}

	public Long getDeltaTotal() {
		return deltaTotal;
	}

	public Long getDeltaReserved() {
		return deltaReserved;
	}

	public Long getQuantity() {
		return quantity;
	}

	public long getVersion() {
		return version;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}


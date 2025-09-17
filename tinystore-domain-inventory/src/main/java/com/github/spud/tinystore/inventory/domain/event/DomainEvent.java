package com.github.spud.tinystore.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 通用领域事件接口
 */
public interface DomainEvent {

	String getEventId();

	String getType();

	String getShopId();

	String getSkuId();

	Instant getOccurredAt();
}

abstract class AbstractDomainEvent implements DomainEvent {

	private final String eventId = UUID.randomUUID().toString();
	private final Instant occurredAt = Instant.now();
	protected final String shopId;
	protected final String skuId;

	protected AbstractDomainEvent(String shopId, String skuId) {
		this.shopId = shopId;
		this.skuId = skuId;
	}

	public String getEventId() {
		return eventId;
	}

	public String getShopId() {
		return shopId;
	}

	public String getSkuId() {
		return skuId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}


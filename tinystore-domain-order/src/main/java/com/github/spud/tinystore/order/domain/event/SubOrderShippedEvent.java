package com.github.spud.tinystore.order.domain.event;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Sub Order Shipped Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubOrderShippedEvent extends OrderDomainEvent {

	private String orderId;
	private String subOrderId;
	private String shipmentInfo;

	@Builder
	public SubOrderShippedEvent(String orderId, String subOrderId, String shipmentInfo) {
		this.orderId = orderId;
		this.subOrderId = subOrderId;
		this.shipmentInfo = shipmentInfo;

		// Set base event properties
		setEventType(EventType.ORDER_SHIPPED);
		setAggregateId(orderId);
		setOccurredAt(LocalDateTime.now());
	}
}

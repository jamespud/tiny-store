package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Sub Order Shipped Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubOrderShippedEvent extends OrderDomainBaseEvent {

	private String orderId;
	private String subOrderId;
	private String shipmentInfo;

	@Builder
	public SubOrderShippedEvent(String orderId, String subOrderId, String shipmentInfo) {
		this.orderId = orderId;
		this.subOrderId = subOrderId;
		this.shipmentInfo = shipmentInfo;

		// Set base event properties
		setType(OrderEventType.ORDER_SHIPPED);
		setAggregateId(orderId);
		setOccurredAt(OffsetDateTime.now());
	}
}

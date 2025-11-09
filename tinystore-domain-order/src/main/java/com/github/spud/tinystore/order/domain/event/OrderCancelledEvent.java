package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Order Canceled Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCancelledEvent extends OrderDomainBaseEvent {

	private String orderId;
	private OffsetDateTime cancelledAt;

	@Builder
	public OrderCancelledEvent(String orderId, OffsetDateTime cancelledAt) {
		this.orderId = orderId;
		this.cancelledAt = cancelledAt;

		// Set base event properties
		setType(OrderEventType.ORDER_CANCELLED);
		setAggregateId(orderId);
		setOccurredAt(cancelledAt != null ? cancelledAt : OffsetDateTime.now());
	}
}

package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Refund Completed Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RefundCompletedEvent extends OrderDomainBaseEvent {

	private String orderId;
	private OffsetDateTime refundedAt;

	@Builder
	public RefundCompletedEvent(String orderId, OffsetDateTime refundedAt) {
		this.orderId = orderId;
		this.refundedAt = refundedAt;

		// Set base event properties
		setType(OrderEventType.AFTERSALE_COMPLETED);
		setAggregateId(orderId);
		setOccurredAt(refundedAt != null ? refundedAt : OffsetDateTime.now());
	}
}

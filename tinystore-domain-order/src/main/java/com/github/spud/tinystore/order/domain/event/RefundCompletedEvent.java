package com.github.spud.tinystore.order.domain.event;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Refund Completed Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RefundCompletedEvent extends OrderDomainEvent {

	private String orderId;
	private LocalDateTime refundedAt;

	@Builder
	public RefundCompletedEvent(String orderId, LocalDateTime refundedAt) {
		this.orderId = orderId;
		this.refundedAt = refundedAt;

		// Set base event properties
		setEventType(EventType.AFTERSALE_COMPLETED);
		setAggregateId(orderId);
		setOccurredAt(refundedAt != null ? refundedAt : LocalDateTime.now());
	}
}

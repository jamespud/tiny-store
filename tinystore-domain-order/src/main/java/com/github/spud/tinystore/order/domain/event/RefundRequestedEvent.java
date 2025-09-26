package com.github.spud.tinystore.order.domain.event;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Refund Requested Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RefundRequestedEvent extends OrderDomainEvent {

	private String orderId;
	private LocalDateTime requestedAt;

	@Builder
	public RefundRequestedEvent(String orderId, LocalDateTime requestedAt) {
		this.orderId = orderId;
		this.requestedAt = requestedAt;

		// Set base event properties
		setEventType(EventType.AFTERSALE_REQUESTED);
		setAggregateId(orderId);
		setOccurredAt(requestedAt != null ? requestedAt : LocalDateTime.now());
	}
}

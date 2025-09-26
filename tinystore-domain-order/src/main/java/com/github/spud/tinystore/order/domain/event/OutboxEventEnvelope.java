package com.github.spud.tinystore.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Outbox Event Envelope for transactional event publishing
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEnvelope {

	// Event identification
	private String eventId;
	private String aggregateType;
	private String aggregateId;
	private Integer version;

	// Event metadata
	private String eventType;
	private LocalDateTime occurredAt;
	private String idempotencyKey;
	private String topic;

	// Event payload
	private String payloadType;
	private String payload; // JSON serialized

	// Outbox state
	private OutboxStatus status;
	private LocalDateTime publishedAt;
	private Integer retryCount;
	private String errorMessage;

	// Additional headers/context
	private Map<String, String> headers;

	public enum OutboxStatus {
		PENDING,
		PUBLISHED,
		FAILED
	}

	/**
	 * Create envelope from domain event
	 */
	public static OutboxEventEnvelope fromDomainEvent(OrderDomainEvent domainEvent, String topic, String payload) {
		return OutboxEventEnvelope.builder()
			.eventId(domainEvent.getEventId())
			.aggregateType("Order")
			.aggregateId(domainEvent.getAggregateId())
			.version(domainEvent.getVersion())
			.eventType(domainEvent.getEventType().name())
			.occurredAt(domainEvent.getOccurredAt())
			.idempotencyKey(generateIdempotencyKey(domainEvent))
			.topic(topic)
			.payloadType(domainEvent.getClass().getSimpleName())
			.payload(payload)
			.status(OutboxStatus.PENDING)
			.retryCount(0)
			.build();
	}

	private static String generateIdempotencyKey(OrderDomainEvent domainEvent) {
		return String.format("%s#%s#%s",
			domainEvent.getAggregateId(),
			domainEvent.getVersion(),
			domainEvent.getEventType().name());
	}

	public void markPublished() {
		this.status = OutboxStatus.PUBLISHED;
		this.publishedAt = LocalDateTime.now();
	}

	public void markFailed(String errorMessage) {
		this.status = OutboxStatus.FAILED;
		this.errorMessage = errorMessage;
		this.retryCount = (this.retryCount != null) ? this.retryCount + 1 : 1;
	}

	public boolean isPublished() {
		return OutboxStatus.PUBLISHED.equals(this.status);
	}

	public boolean shouldRetry(int maxRetries) {
		return OutboxStatus.FAILED.equals(this.status) &&
			(this.retryCount == null || this.retryCount < maxRetries);
	}
}

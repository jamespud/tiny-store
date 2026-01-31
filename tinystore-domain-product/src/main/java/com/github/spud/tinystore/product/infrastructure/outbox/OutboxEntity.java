package com.github.spud.tinystore.product.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * OutboxEntity - JPA entity for outbox pattern
 * <p>
 * Stores domain events for reliable publishing to Kafka Ensures transactional consistency between
 * domain changes and event publishing
 * <p>
 * Lifecycle: 1. Created with status PENDING when domain event occurs 2. Background worker polls
 * PENDING events 3. Published to Kafka and marked PUBLISHED 4. Failed publishing marked FAILED with
 * retry logic
 */
@Entity
@Table(name = "outbox")
@Data
public class OutboxEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "aggregate_type", nullable = false)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private String aggregateId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@Column(name = "payload", nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private LocalDateTime occurredAt;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "status", nullable = false)
	private String status = "PENDING";

	@Column(name = "retry_count")
	private Integer retryCount = 0;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "shop_id", nullable = false)
	private String shopId;

	@PrePersist
	protected void onCreate() {
		if (occurredAt == null) {
			occurredAt = LocalDateTime.now();
		}
	}
}

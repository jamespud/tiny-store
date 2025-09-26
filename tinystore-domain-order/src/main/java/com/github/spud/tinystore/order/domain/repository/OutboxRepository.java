package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.event.OutboxEventEnvelope;
import com.github.spud.tinystore.order.domain.model.Outbox;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox Repository for transactional event publishing
 *
 * @author Spud
 * @date 2025/9/22
 */
public interface OutboxRepository {

	/**
	 * Append event to outbox for publishing
	 *
	 * @param envelope Event envelope to store
	 */
	void append(OutboxEventEnvelope envelope);

	/**
	 * Fetch pending events for publishing
	 *
	 * @param batchSize Maximum number of events to fetch
	 * @return List of pending events
	 */
	List<OutboxEventEnvelope> fetchPendingBatch(int batchSize);

	/**
	 * Mark event as published
	 *
	 * @param eventId Event ID to mark as published
	 */
	void markPublished(String eventId);

	/**
	 * Mark event as failed with error
	 *
	 * @param eventId      Event ID to mark as failed
	 * @param errorMessage Error message
	 */
	void markFailed(String eventId, String errorMessage);

	/**
	 * Check if event exists (for idempotency)
	 *
	 * @param eventId Event ID to check
	 * @return true if event exists
	 */
	boolean existsById(String eventId);

	/**
	 * Check if event exists by idempotency key
	 *
	 * @param idempotencyKey Idempotency key to check
	 * @return true if event with this idempotency key exists
	 */
	boolean existsByIdempotencyKey(String idempotencyKey);

	/**
	 * Fetch failed events for retry
	 *
	 * @param maxRetries Maximum retry count to consider
	 * @param batchSize  Maximum number of events to fetch
	 * @return List of failed events eligible for retry
	 */
	List<OutboxEventEnvelope> fetchFailedForRetry(int maxRetries, int batchSize);

	/**
	 * Delete old published events (cleanup)
	 *
	 * @param beforeTime Delete events published before this time
	 * @return Number of deleted events
	 */
	int deletePublishedBefore(LocalDateTime beforeTime);

	List<Outbox> findPendingEvents(int batchSize);

	List<Outbox> findEventsForRetry(Instant now, int batchSize);

	int deletePublishedEventsBefore(Instant sevenDaysAgo);

	void markAsPublished(UUID eventId);

	void updateStatus(UUID eventId, Outbox.PublishStatus publishStatus, int newRetryCount, Instant nextRetryAt, String errorMessage);
}
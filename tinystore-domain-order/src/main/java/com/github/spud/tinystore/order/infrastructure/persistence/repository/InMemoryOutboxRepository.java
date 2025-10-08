package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.order.domain.event.OutboxEventEnvelope;
import com.github.spud.tinystore.order.domain.model.Outbox;
import com.github.spud.tinystore.order.domain.model.Outbox.PublishStatus;
import com.github.spud.tinystore.order.domain.repository.OutboxRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * Outbox 仓储的内存实现 用于开发阶段，生产环境应使用数据库实现
 *
 * @author Spud
 * @date 2025/9/22
 */
@Slf4j
@Repository
public class InMemoryOutboxRepository implements OutboxRepository {

	private final Map<String, Outbox> storage = new ConcurrentHashMap<>();

	public void save(Outbox outbox) {
		storage.put(outbox.getEventId(), outbox);
		log.debug("Saved outbox event: eventId={}, eventType={}", outbox.getEventId(),
			outbox.getEventType());
	}

	@Override
	public void append(OutboxEventEnvelope envelope) {

	}

	@Override
	public List<OutboxEventEnvelope> fetchPendingBatch(int batchSize) {
		return List.of();
	}

	@Override
	public void markPublished(String eventId) {

	}

	@Override
	public void markFailed(String eventId, String errorMessage) {

	}

	@Override
	public boolean existsById(String eventId) {
		return false;
	}

	@Override
	public boolean existsByIdempotencyKey(String idempotencyKey) {
		return false;
	}

	@Override
	public List<OutboxEventEnvelope> fetchFailedForRetry(int maxRetries, int batchSize) {
		return List.of();
	}

	@Override
	public int deletePublishedBefore(LocalDateTime beforeTime) {
		return 0;
	}

	@Override
	public List<Outbox> findPendingEvents(int limit) {
		return storage.values().stream()
			.filter(event -> event.getStatus() == Outbox.PublishStatus.PENDING)
			.limit(limit)
			.collect(Collectors.toList());
	}

	@Override
	public List<Outbox> findEventsForRetry(Instant beforeTime, int limit) {
		return storage.values().stream()
			.filter(event -> event.getStatus() == Outbox.PublishStatus.FAILED)
			.filter(
				event -> event.getNextRetryAt() != null && event.getNextRetryAt().isBefore(beforeTime))
			.limit(limit)
			.collect(Collectors.toList());
	}

	public void updateStatus(UUID eventId, Outbox.PublishStatus status,
		Integer retryCount, Instant nextRetryAt, String errorMessage) {
		Outbox event = storage.get(eventId);
		if (event != null) {
			event.setStatus(status);
			event.setRetryCount(retryCount);
			event.setNextRetryAt(nextRetryAt);
			event.setErrorMessage(errorMessage);
			log.debug("Updated outbox event status: eventId={}, status={}, retryCount={}",
				eventId, status, retryCount);
		}
	}

	@Override
	public int deletePublishedEventsBefore(Instant beforeTime) {
		List<String> toDelete = storage.values().stream()
			.filter(event -> event.getStatus() == Outbox.PublishStatus.PUBLISHED)
			.filter(
				event -> event.getPublishedAt() != null && event.getPublishedAt().isBefore(beforeTime))
			.map(Outbox::getEventId)
			.toList();

		toDelete.forEach(storage::remove);

		log.info("Deleted {} published outbox events before {}", toDelete.size(), beforeTime);
		return toDelete.size();
	}

	@Override
	public void markAsPublished(String eventId) {

	}

	@Override
	public void updateStatus(String eventId, PublishStatus publishStatus, int newRetryCount,
		Instant nextRetryAt, String errorMessage) {

	}
}
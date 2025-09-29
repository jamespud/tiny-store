package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.event.DomainEvent;
import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 事件服务
 * 实现 Outbox Pattern，确保事件的最终一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

	private final OrderOutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	/**
	 * 保存领域事件到 Outbox
	 *
	 * @param event 领域事件
	 */
	@Transactional
	public void saveEvent(DomainEvent event) {
		try {
			String eventPayload = objectMapper.writeValueAsString(event.getPayload());

			OrderOutboxEventPO outboxEvent = new OrderOutboxEventPO()
				.setId(event.getEventId())
				.setOrderNo(event.getOrderNo())
				.setEventType(event.getType())
				.setEventPayload(eventPayload)
				.setTraceId(event.getTraceId())
				.setStatus(OrderOutboxEventPO.OutboxEventStatus.PENDING)
				.setRetryCount(0);

			outboxEventRepository.save(outboxEvent);

			log.info("Saved domain event to outbox: eventId={}, orderNo={}, type={}",
				event.getEventId(), event.getOrderNo(), event.getType());

		} catch (JsonProcessingException e) {
			log.error("Failed to serialize event payload: eventId={}, orderNo={}",
				event.getEventId(), event.getOrderNo(), e);
			throw new RuntimeException("Failed to save domain event", e);
		}
	}

	/**
	 * 批量保存领域事件到 Outbox
	 *
	 * @param events 领域事件列表
	 */
	@Transactional
	public void saveEvents(List<DomainEvent> events) {
		if (events == null || events.isEmpty()) {
			return;
		}

		List<OrderOutboxEventPO> outboxEvents = events.stream()
			.map(this::convertToOutboxEvent)
			.toList();

		outboxEventRepository.saveAll(outboxEvents);

		log.info("Saved {} domain events to outbox", events.size());
	}

	/**
	 * 查询待发送的事件
	 *
	 * @param limit 限制数量
	 * @return 待发送的事件列表
	 */
	@Transactional(readOnly = true)
	public List<OrderOutboxEventPO> findPendingEvents(int limit) {
		return outboxEventRepository.findPendingEvents(limit);
	}

	/**
	 * 查询可重试的失败事件
	 *
	 * @param maxRetries 最大重试次数
	 * @return 可重试的事件列表
	 */
	@Transactional(readOnly = true)
	public List<OrderOutboxEventPO> findRetryableEvents(int maxRetries) {
		return outboxEventRepository.findRetryableEvents(maxRetries);
	}

	/**
	 * 标记事件为已发送
	 *
	 * @param eventIds 事件ID列表
	 */
	@Transactional
	public void markEventsSent(List<UUID> eventIds) {
		if (eventIds == null || eventIds.isEmpty()) {
			return;
		}

		outboxEventRepository.updateStatusBatch(
			eventIds,
			OrderOutboxEventPO.OutboxEventStatus.SENT,
			OffsetDateTime.now()
		);

		log.info("Marked {} events as sent", eventIds.size());
	}

	/**
	 * 标记事件为失败并增加重试次数
	 *
	 * @param eventId 事件ID
	 */
	@Transactional
	public void markEventFailed(UUID eventId) {
		outboxEventRepository.incrementRetryCount(eventId, OffsetDateTime.now());

		// 如果重试次数过多，标记为失败
		OrderOutboxEventPO event = outboxEventRepository.findById(eventId).orElse(null);
		if (event != null && event.getRetryCount() >= 5) { // 最大重试5次
			outboxEventRepository.updateStatusBatch(
				List.of(eventId),
				OrderOutboxEventPO.OutboxEventStatus.FAILED,
				OffsetDateTime.now()
			);

			log.warn("Event marked as permanently failed after max retries: eventId={}", eventId);
		}
	}

	/**
	 * 清理过期的已完成事件
	 *
	 * @param retentionDays 保留天数
	 */
	@Transactional
	public void cleanCompletedEvents(int retentionDays) {
		OffsetDateTime cutoffTime = OffsetDateTime.now().minusDays(retentionDays);
		outboxEventRepository.cleanCompletedEvents(cutoffTime);

		log.info("Cleaned completed outbox events older than {} days", retentionDays);
	}

	/**
	 * 将领域事件转换为 Outbox 事件
	 */
	private OrderOutboxEventPO convertToOutboxEvent(DomainEvent event) {
		try {
			String eventPayload = objectMapper.writeValueAsString(event.getPayload());

			return new OrderOutboxEventPO()
				.setId(event.getEventId())
				.setOrderNo(event.getOrderNo())
				.setEventType(event.getType())
				.setEventPayload(eventPayload)
				.setTraceId(event.getTraceId())
				.setStatus(OrderOutboxEventPO.OutboxEventStatus.PENDING)
				.setRetryCount(0);

		} catch (JsonProcessingException e) {
			log.error("Failed to serialize event payload: eventId={}, orderNo={}",
				event.getEventId(), event.getOrderNo(), e);
			throw new RuntimeException("Failed to convert domain event", e);
		}
	}
}
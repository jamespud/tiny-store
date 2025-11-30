package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderOutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.spud.tinystore.order.infrastructure.tenant.TenantContext;

/**
 * Outbox 事件服务 实现 Outbox Pattern，确保事件的最终一致性
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
	public void saveEvent(OrderDomainEvent event) {
		try {
			String eventPayload = objectMapper.writeValueAsString(buildEnvelopePayload(event));

			OrderOutboxEventPO outboxEvent = new OrderOutboxEventPO()
				.setId(event.getEventId())
				.setOrderId(event.getOrderId())
				.setEventType(event.getType().toString())
				.setEventPayload(eventPayload)
				.setTraceId(event.getTraceId())
				.setStatus(OrderOutboxEventPO.OutboxEventStatus.PENDING)
				.setRetryCount(0);

			outboxEventRepository.save(outboxEvent);

			log.info("Saved domain event to outbox: eventId={}, orderId={}, type={}",
				event.getEventId(), event.getEventId(), event.getType());

		} catch (JsonProcessingException e) {
			log.error("Failed to serialize event payload: eventId={}, orderId={}",
				event.getEventId(), event.getOrderId(), e);
			throw new RuntimeException("Failed to save domain event", e);
		}
	}

	/**
	 * 批量保存领域事件到 Outbox
	 *
	 * @param events 领域事件列表
	 */
	@Transactional
	public void saveEvents(List<OrderDomainEvent> events) {
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
	public void markEventsSent(List<String> eventIds) {
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
	public void markEventFailed(String eventId) {
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
	private OrderOutboxEventPO convertToOutboxEvent(OrderDomainEvent event) {
		try {
			String eventPayload = objectMapper.writeValueAsString(buildEnvelopePayload(event));

			return new OrderOutboxEventPO()
				.setId(event.getEventId())
				.setOrderId(event.getOrderId())
				.setEventType(event.getType().toString())
				.setEventPayload(eventPayload)
				.setTraceId(event.getTraceId())
				.setStatus(OrderOutboxEventPO.OutboxEventStatus.PENDING)
				.setRetryCount(0);

		} catch (JsonProcessingException e) {
			log.error("Failed to serialize event payload: eventId={}, orderId={}",
				event.getEventId(), event.getEventId(), e);
			throw new RuntimeException("Failed to convert domain event", e);
		}
	}

	/**
	 * 构建统一的事件载荷信封，包含版本、聚合ID、时间、租户/操作者/追踪信息与数据体
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> buildEnvelopePayload(OrderDomainEvent event) {
		Map<String, Object> envelope = new HashMap<>();
		envelope.put("version", "v1");
		envelope.put("eventId", event.getEventId());
		envelope.put("aggregateId", event.getOrderId());
		if (event.getOccurredAt() != null) {
			envelope.put("occurredAt", event.getOccurredAt());
		}
		// 上下文信息
		String tenantId = TenantContext.getTenantId();
		String userId = TenantContext.getUserId();
		String traceId = event.getTraceId() != null ? event.getTraceId() : MDC.get("traceId");
		if (tenantId != null) envelope.put("tenantId", tenantId);
		if (traceId != null) envelope.put("traceId", traceId);
		Map<String, Object> operator = new HashMap<>();
		if (userId != null) {
			operator.put("id", userId);
			operator.put("type", "user");
		} else {
			operator.put("id", "system");
			operator.put("type", "system");
		}
		envelope.put("operator", operator);

		// 事件数据体
		Object rawPayload = null;
		try {
			rawPayload = event.getPayload();
		} catch (Exception ignore) {
			// 某些事件未实现 getPayload，容忍为空
		}
		Map<String, Object> data = new HashMap<>();
		if (rawPayload instanceof Map) {
			//noinspection unchecked
			data.putAll((Map<String, Object>) rawPayload);
			Object subOrderId = ((Map<?, ?>) rawPayload).get("subOrderId");
			if (subOrderId != null) {
				envelope.put("subOrderId", subOrderId);
			}
		}
		envelope.put("data", data);
		return envelope;
	}

	/**
	 * 统一构造事件载荷（不依赖 OrderDomainEvent），用于状态机 Action 等场景。
	 */
	public Map<String, Object> buildEvent(String eventType,
	                                     String aggregateId,
	                                     String subOrderId,
	                                     String tenantId,
	                                     String operatorId,
	                                     Object domainData) {
		Map<String, Object> envelope = new HashMap<>();
		envelope.put("version", "v1");
		envelope.put("eventType", eventType);
		envelope.put("aggregateId", aggregateId);
		if (subOrderId != null && !subOrderId.isBlank()) {
			envelope.put("subOrderId", subOrderId);
		}
		envelope.put("occurredAt", OffsetDateTime.now().toString());
		String traceId = MDC.get("traceId");
		if (tenantId != null && !tenantId.isBlank()) envelope.put("tenantId", tenantId);
		if (operatorId != null && !operatorId.isBlank()) envelope.put("operatorId", operatorId);
		if (traceId != null && !traceId.isBlank()) envelope.put("traceId", traceId);

		Map<String, Object> data = new HashMap<>();
		if (domainData instanceof Map<?, ?> m) {
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (e.getKey() != null) data.put(String.valueOf(e.getKey()), e.getValue());
			}
		} else if (domainData != null) {
			try {
				// 尝试将对象转 Map（best-effort）
				String json = objectMapper.writeValueAsString(domainData);
				//noinspection unchecked
				Map<?, ?> tmp = objectMapper.readValue(json, Map.class);
				for (Map.Entry<?, ?> e : tmp.entrySet()) {
					if (e.getKey() != null) data.put(String.valueOf(e.getKey()), e.getValue());
				}
			} catch (Exception ignored) {
				data.put("value", String.valueOf(domainData));
			}
		}
		envelope.put("data", data);
		return envelope;
	}
}
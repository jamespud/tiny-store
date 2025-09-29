package com.github.spud.tinystore.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 订单状态变更事件
 * 所有订单状态变更都会触发此事件，用于审计和追踪
 *
 * @author Spud
 * @date 2025/9/29
 */
@Data
@AllArgsConstructor
public class OrderStatusChangedEvent implements DomainEvent {

	private final UUID eventId;
	private final String orderNo;
	private final OffsetDateTime occurredAt;
	private final String fromStatus;
	private final String toStatus;
	private final String reason;
	private final String actorType;
	private final String actorId;
	private final String traceId;

	public OrderStatusChangedEvent(String orderNo, String fromStatus, String toStatus, String reason, String actorType, String actorId) {
		this.eventId = UUID.randomUUID();
		this.orderNo = orderNo;
		this.occurredAt = OffsetDateTime.now();
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason;
		this.actorType = actorType;
		this.actorId = actorId;
		this.traceId = MDC.get("traceId");
	}

	@Override
	public String getType() {
		return "ORDER_STATUS_CHANGED";
	}

	@Override
	public Map<String, Object> getPayload() {
		Map<String, Object> payload = new HashMap<>();
		payload.put("fromStatus", fromStatus);
		payload.put("toStatus", toStatus);
		payload.put("reason", reason);
		payload.put("actorType", actorType);
		payload.put("actorId", actorId);
		return payload;
	}
}
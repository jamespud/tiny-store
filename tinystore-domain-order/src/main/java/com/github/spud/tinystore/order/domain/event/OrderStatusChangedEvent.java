package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.MDC;

/**
 * 订单状态变更事件 所有订单状态变更都会触发此事件，用于审计和追踪
 *
 * @author Spud
 * @date 2025/9/29
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class OrderStatusChangedEvent extends OrderDomainBaseEvent {

  private final String fromStatus;
  private final String toStatus;
  private final String reason;
  private final String actorType;
  private final String actorId;


  public OrderStatusChangedEvent(String orderId, String fromStatus, String toStatus, String reason,
    String actorType, String actorId) {
    this.setEventId(UUID.randomUUID().toString().replace("-", ""));
    this.setOrderId(orderId);
    this.setOccurredAt(OffsetDateTime.now());
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.reason = reason;
    this.actorType = actorType;
    this.actorId = actorId;
    this.setTraceId(MDC.get("traceId"));
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
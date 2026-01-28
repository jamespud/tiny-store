package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class OrderDomainBaseEvent implements OrderDomainEvent {

  private String eventId;

  private String orderId;

  private OrderEventType type;

  private OffsetDateTime occurredAt;

  private Object aggregateType;

  private String aggregateId;

  private Integer version;

  private Object payloadType;

  private Map<String, Object> payload;

  private Object dataHash;

  private String traceId;
}

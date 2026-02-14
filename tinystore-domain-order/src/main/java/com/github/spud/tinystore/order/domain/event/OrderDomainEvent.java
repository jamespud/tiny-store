package com.github.spud.tinystore.order.domain.event;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单域基础事件（用于 Outbox 存储的事件 envelope）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDomainEvent {
    private String eventId;
    private OrderEventType eventType;
    private String aggregateType;
    private String aggregateId;
    private LocalDateTime occurredAt;
    private String traceId;
    private String payloadJson;
    
}

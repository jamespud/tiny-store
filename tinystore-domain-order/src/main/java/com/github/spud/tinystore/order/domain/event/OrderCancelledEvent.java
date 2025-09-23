package com.github.spud.tinystore.order.domain.event;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Order Cancelled Domain Event
 * 
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCancelledEvent extends OrderDomainEvent {
    
    private String orderId;
    private LocalDateTime cancelledAt;

    @Builder
    public OrderCancelledEvent(String orderId, LocalDateTime cancelledAt) {
        this.orderId = orderId;
        this.cancelledAt = cancelledAt;
        
        // Set base event properties
        setEventType(EventType.ORDER_CANCELLED);
        setAggregateId(orderId);
        setOccurredAt(cancelledAt != null ? cancelledAt : LocalDateTime.now());
    }
}

package com.github.spud.tinystore.order.domain.event;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Order Completed Domain Event
 * 
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCompletedEvent extends OrderDomainEvent {
    
    private String orderId;
    private LocalDateTime completedAt;

    @Builder
    public OrderCompletedEvent(String orderId, LocalDateTime completedAt) {
        this.orderId = orderId;
        this.completedAt = completedAt;
        
        // Set base event properties
        setEventType(EventType.ORDER_COMPLETED);
        setAggregateId(orderId);
        setOccurredAt(completedAt != null ? completedAt : LocalDateTime.now());
    }
}

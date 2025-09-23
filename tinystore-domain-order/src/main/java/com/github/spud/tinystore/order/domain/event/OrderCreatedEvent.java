package com.github.spud.tinystore.order.domain.event;

import java.time.LocalDateTime;

import com.github.spud.tinystore.order.domain.model.Money;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Order Created Domain Event
 * 
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreatedEvent extends OrderDomainEvent {
    
    private String orderId;
    private String buyerId; 
    private Money totalAmount;
    private LocalDateTime createdAt;

    @Builder
    public OrderCreatedEvent(String orderId, String buyerId, Money totalAmount, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        
        // Set base event properties
        setEventType(EventType.ORDER_CREATED);
        setAggregateId(orderId);
        setOccurredAt(createdAt != null ? createdAt : LocalDateTime.now());
    }
}

package com.github.spud.tinystore.order.domain.event;

import com.github.spud.tinystore.order.domain.model.Money;
import java.time.OffsetDateTime;
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
public class OrderCreatedEvent extends OrderDomainBaseEvent {

  private String orderId;
  private String buyerId;
  private Money totalAmount;
  private OffsetDateTime createdAt;

  @Builder
  public OrderCreatedEvent(String orderId, String buyerId, Money totalAmount,
    OffsetDateTime createdAt) {
    this.orderId = orderId;
    this.buyerId = buyerId;
    this.totalAmount = totalAmount;
    this.createdAt = createdAt;

    // Set base event properties
    setType(OrderEventType.ORDER_CREATED);
    setAggregateId(orderId);
    setOccurredAt(createdAt != null ? createdAt : OffsetDateTime.now());
  }
}

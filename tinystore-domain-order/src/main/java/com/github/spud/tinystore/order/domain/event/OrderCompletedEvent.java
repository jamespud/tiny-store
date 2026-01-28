package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
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
public class OrderCompletedEvent extends OrderDomainBaseEvent {

  private String orderId;
  private OffsetDateTime completedAt;

  @Builder
  public OrderCompletedEvent(String orderId, OffsetDateTime completedAt) {
    this.orderId = orderId;
    this.completedAt = completedAt;

    // Set base event properties
    setType(OrderEventType.ORDER_COMPLETED);
    setAggregateId(orderId);
    setOccurredAt(completedAt != null ? completedAt : OffsetDateTime.now());
  }
}

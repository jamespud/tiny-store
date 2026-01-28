package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Refund Requested Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RefundRequestedEvent extends OrderDomainBaseEvent {

  private String orderId;
  private OffsetDateTime requestedAt;

  @Builder
  public RefundRequestedEvent(String orderId, OffsetDateTime requestedAt) {
    this.orderId = orderId;
    this.requestedAt = requestedAt;

    // Set base event properties
    setType(OrderEventType.AFTERSALE_REQUESTED);
    setAggregateId(orderId);
    setOccurredAt(requestedAt != null ? requestedAt : OffsetDateTime.now());
  }
}

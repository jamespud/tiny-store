package com.github.spud.tinystore.order.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Order Payment Succeeded Domain Event
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPaymentSucceededEvent extends OrderDomainBaseEvent {

  private String orderId;
  private String paymentId;
  private BigDecimal amount;
  private boolean isDeposit;
  private boolean isFinalPayment;

  @Builder
  public OrderPaymentSucceededEvent(String orderId, String paymentId, BigDecimal amount,
    boolean isDeposit, boolean isFinalPayment) {
    this.orderId = orderId;
    this.paymentId = paymentId;
    this.amount = amount;
    this.isDeposit = isDeposit;
    this.isFinalPayment = isFinalPayment;

    // Set base event properties
    setType(OrderEventType.ORDER_PAID);
    setAggregateId(orderId);
    setOccurredAt(OffsetDateTime.now());
  }
}

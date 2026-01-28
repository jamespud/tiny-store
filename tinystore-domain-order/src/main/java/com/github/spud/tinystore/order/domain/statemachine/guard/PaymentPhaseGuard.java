package com.github.spud.tinystore.order.domain.statemachine.guard;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;

public class PaymentPhaseGuard implements Guard<CoreFlowStatus, OrderEvent> {

  @Override
  public boolean evaluate(StateContext<CoreFlowStatus, OrderEvent> context) {
    return context.getSource() != null
      && context.getSource().getId() == CoreFlowStatus.PENDING_PAYMENT;
  }
}

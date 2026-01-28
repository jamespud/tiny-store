package com.github.spud.tinystore.order.domain.statemachine.guard;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

public class CanShipGuard implements Guard<CoreFlowStatus, OrderEvent> {

  @Override
  public boolean evaluate(StateContext<CoreFlowStatus, OrderEvent> context) {
    Transition<CoreFlowStatus, OrderEvent> t = context.getTransition();
    State<CoreFlowStatus, OrderEvent> sourceState = t != null ? t.getSource() : context.getSource();
    CoreFlowStatus s = sourceState.getId();
    // 简化: 已支付或已接受允许进入履约发货
    return s == CoreFlowStatus.PAID || s == CoreFlowStatus.ACCEPTED;
  }
}
package com.github.spud.tinystore.order.domain.statemachine.guard;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

public class CanPayGuard implements Guard<CoreFlowStatus, OrderEvent> {

  @Override
  public boolean evaluate(StateContext<CoreFlowStatus, OrderEvent> context) {
    Transition<CoreFlowStatus, OrderEvent> t = context.getTransition();
    State<CoreFlowStatus, OrderEvent> sourceState = t != null ? t.getSource() : context.getSource();
    CoreFlowStatus s = sourceState.getId();
    // 简化: 仅在待支付阶段允许支付成功事件
    return s == CoreFlowStatus.PENDING_PAYMENT;
  }
}
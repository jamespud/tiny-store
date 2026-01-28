package com.github.spud.tinystore.order.domain.statemachine.guard;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

public class TerminalStateGuard implements Guard<CoreFlowStatus, OrderEvent> {

  @Override
  public boolean evaluate(StateContext<CoreFlowStatus, OrderEvent> context) {
    Transition<CoreFlowStatus, OrderEvent> transition = context.getTransition();
    State<CoreFlowStatus, OrderEvent> sourceState =
      transition != null ? transition.getSource() : context.getSource();
    CoreFlowStatus source = sourceState.getId();
    return source != CoreFlowStatus.COMPLETED && source != CoreFlowStatus.CANCELLED;
  }
}

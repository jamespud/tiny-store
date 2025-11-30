package com.github.spud.tinystore.order.domain.statemachine.guard;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.transition.Transition;
import org.springframework.statemachine.state.State;

public class CanConfirmDeliveryGuard implements Guard<CoreFlowStatus, OrderEvent> {
    @Override
    public boolean evaluate(StateContext<CoreFlowStatus, OrderEvent> context) {
        Transition<CoreFlowStatus, OrderEvent> t = context.getTransition();
        State<CoreFlowStatus, OrderEvent> sourceState = t != null ? t.getSource() : context.getSource();
        CoreFlowStatus s = sourceState.getId();
        // 简化: 履约中允许发货/妥投内部事件
        return s == CoreFlowStatus.FULFILLING;
    }
}
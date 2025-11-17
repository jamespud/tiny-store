package com.github.spud.tinystore.order.domain.statemachine.guard;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.StateContext;

public class FulfillmentMutexGuard implements Guard<CoreFlowStatus, OrderEvent> {
    @Override
    public boolean evaluate(StateContext<CoreFlowStatus, OrderEvent> context) {
        if (context.getSource() == null) {
            return false;
        }
        CoreFlowStatus status = context.getSource().getId();
        return status == CoreFlowStatus.FULFILLING || status == CoreFlowStatus.PAID || status == CoreFlowStatus.ACCEPTED;
    }
}

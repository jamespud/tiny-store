package com.github.spud.tinystore.order.domain.statemachine.action;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.StateContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnGoodsReceivedAction implements Action<CoreFlowStatus, OrderEvent> {
    @Override
    public void execute(StateContext<CoreFlowStatus, OrderEvent> context) {
        log.debug("Action: goods received");
    }
}

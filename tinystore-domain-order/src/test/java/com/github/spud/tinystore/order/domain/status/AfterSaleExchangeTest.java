package com.github.spud.tinystore.order.domain.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AfterSaleExchangeTest {
    private final OrderStateTransitionService svc = new OrderStateTransitionService();

    @Test
    void testExchangeFlowCreatesCompletion(){
        CoreFlowStatus s = CoreFlowStatus.FULFILLING;
        s = svc.requestAfterSale(s);
        Assertions.assertEquals(CoreFlowStatus.AFTER_SALE, s);
        s = svc.exchangeCompleted(s);
        Assertions.assertEquals(CoreFlowStatus.COMPLETED, s);
        Assertions.assertTrue(TerminalStateChecker.isTerminal(s));
    }
}


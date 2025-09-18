package com.github.spud.tinystore.order.domain.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RefundTerminalTest {
    private final OrderStateTransitionService svc = new OrderStateTransitionService();

    @Test
    void testRefundFlowToTerminal(){
        CoreFlowStatus s = CoreFlowStatus.FULFILLING;
        s = svc.requestAfterSale(s);
        Assertions.assertEquals(CoreFlowStatus.AFTER_SALE, s);
        s = svc.refundSuccess(s);
        Assertions.assertEquals(CoreFlowStatus.REFUNDED, s);
        Assertions.assertTrue(TerminalStateChecker.isTerminal(s));
        // 再次尝试发起售后不应改变
        CoreFlowStatus again = svc.requestAfterSale(s);
        Assertions.assertEquals(CoreFlowStatus.REFUNDED, again);
    }
}


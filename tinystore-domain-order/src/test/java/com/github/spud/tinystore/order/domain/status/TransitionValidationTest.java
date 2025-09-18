package com.github.spud.tinystore.order.domain.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TransitionValidationTest {
    private final OrderStateTransitionService svc = new OrderStateTransitionService();

    @Test
    void testFullPaymentNormalFlow(){
        CoreFlowStatus s = CoreFlowStatus.PENDING_PAYMENT;
        s = svc.paymentSuccess(s,false,false); // full pay
        Assertions.assertEquals(CoreFlowStatus.PAID_CONFIRMED, s);
        s = svc.moveToAwaitingFulfillment(s);
        Assertions.assertEquals(CoreFlowStatus.AWAITING_FULFILLMENT, s);
        s = svc.startFulfillment(s);
        Assertions.assertEquals(CoreFlowStatus.FULFILLING, s);
        s = svc.completeIfNoAfterSale(s);
        Assertions.assertEquals(CoreFlowStatus.COMPLETED, s);
        Assertions.assertTrue(TerminalStateChecker.isTerminal(s));
    }

    @Test
    void testCancelApproveFlow(){
        CoreFlowStatus s = CoreFlowStatus.PENDING_PAYMENT;
        s = svc.paymentSuccess(s,false,false);
        s = svc.moveToAwaitingFulfillment(s);
        s = svc.startFulfillment(s);
        // 用户申请取消
        CoreFlowStatus previous = s;
        s = svc.cancelRequest(s);
        Assertions.assertEquals(CoreFlowStatus.CANCELLING, s);
        // 取消审核通过 -> CANCELLED
        s = svc.cancelApproved(s);
        Assertions.assertEquals(CoreFlowStatus.CANCELLED, s);
        Assertions.assertTrue(TerminalStateChecker.isTerminal(s));
        // 再次取消请求不应变化
        CoreFlowStatus again = svc.cancelRequest(s);
        Assertions.assertEquals(CoreFlowStatus.CANCELLED, again);
        Assertions.assertEquals(previous, CoreFlowStatus.FULFILLING); // 确认之前状态记录
    }

    @Test
    void testCancelRejectRollback(){
        CoreFlowStatus s = CoreFlowStatus.FULFILLING; // 假定已在履约
        CoreFlowStatus prev = s;
        s = svc.cancelRequest(s);
        Assertions.assertEquals(CoreFlowStatus.CANCELLING, s);
        s = svc.cancelRejected(s, prev);
        Assertions.assertEquals(prev, s);
    }
}

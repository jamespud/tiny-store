package com.github.spud.tinystore.order.domain.status;

/**
 * 核心订单状态转换服务（精简版，仅用于当前重构测试）
 * 说明：未引入订单实体，纯函数式状态机。
 */
public class OrderStateTransitionService {

    public CoreFlowStatus paymentSuccess(CoreFlowStatus current, boolean isDeposit, boolean isFinalPayment) {
        if (current == CoreFlowStatus.PENDING_PAYMENT) {
            if (isDeposit) {
                return CoreFlowStatus.PENDING_FINAL_PAYMENT;
            }
            return CoreFlowStatus.PAID_CONFIRMED;
        }
        if (current == CoreFlowStatus.PENDING_FINAL_PAYMENT && isFinalPayment) {
            return CoreFlowStatus.PAID_CONFIRMED;
        }
        return current; // 其它不变
    }

    public CoreFlowStatus moveToAwaitingFulfillment(CoreFlowStatus current) {
        if (current == CoreFlowStatus.PAID_CONFIRMED) {
            return CoreFlowStatus.AWAITING_FULFILLMENT;
        }
        return current;
    }

    public CoreFlowStatus startFulfillment(CoreFlowStatus current) {
        if (current == CoreFlowStatus.AWAITING_FULFILLMENT) {
            return CoreFlowStatus.FULFILLING;
        }
        return current;
    }

    public CoreFlowStatus delivered(CoreFlowStatus current, boolean afterSaleWindowOpen) {
        if (current == CoreFlowStatus.FULFILLING) {
            // 交付后暂不直接进入 COMPLETED，假设此时仍在观察期，可根据业务策略直接完成
            return afterSaleWindowOpen ? CoreFlowStatus.FULFILLING : CoreFlowStatus.COMPLETED;
        }
        return current;
    }

    public CoreFlowStatus completeIfNoAfterSale(CoreFlowStatus current) {
        if (current == CoreFlowStatus.FULFILLING) {
            return CoreFlowStatus.COMPLETED;
        }
        return current;
    }

    public CoreFlowStatus requestAfterSale(CoreFlowStatus current) {
        if (current == CoreFlowStatus.FULFILLING || current == CoreFlowStatus.COMPLETED) {
            return CoreFlowStatus.AFTER_SALE;
        }
        return current;
    }

    public CoreFlowStatus refundSuccess(CoreFlowStatus current) {
        if (current == CoreFlowStatus.AFTER_SALE || current == CoreFlowStatus.CANCELLING) {
            return CoreFlowStatus.REFUNDED;
        }
        return current;
    }

    public CoreFlowStatus cancelRequest(CoreFlowStatus current) {
        if (!TerminalStateChecker.isTerminal(current) && current != CoreFlowStatus.CANCELLING) {
            return CoreFlowStatus.CANCELLING;
        }
        return current;
    }

    public CoreFlowStatus cancelApproved(CoreFlowStatus current) {
        if (current == CoreFlowStatus.CANCELLING) {
            return CoreFlowStatus.CANCELLED;
        }
        return current;
    }

    public CoreFlowStatus cancelRejected(CoreFlowStatus current, CoreFlowStatus previous) {
        if (current == CoreFlowStatus.CANCELLING) {
            return previous; // 回退
        }
        return current;
    }

    public CoreFlowStatus exchangeCompleted(CoreFlowStatus current) {
        if (current == CoreFlowStatus.AFTER_SALE) {
            return CoreFlowStatus.COMPLETED; // 新订单另起，这里完成
        }
        return current;
    }
}


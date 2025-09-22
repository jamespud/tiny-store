package com.github.spud.tinystore.order.domain.status;

import org.springframework.stereotype.Component;

/**
 * 核心订单状态转换服务（精简版，仅用于当前重构测试）
 * 说明：未引入订单实体，纯函数式状态机。
 * 
 * <h2>接口与状态机方法映射关系</h2>
 * 
 * <h3>用户侧接口 (/order/user)</h3>
 * <ul>
 *   <li>POST /order/user/confirm-receipt → delivered(current, afterSaleWindowOpen=false) → completeIfNoAfterSale()</li>
 *   <li>POST /order/user/after-sale/apply → requestAfterSale(current)</li>
 *   <li>POST /order/user/cancel/apply → cancelRequest(current)</li>
 * </ul>
 * 
 * <h3>商家侧接口 (/order/merchant)</h3>
 * <ul>
 *   <li>POST /order/merchant/order/receive → moveToAwaitingFulfillment(current)</li>
 *   <li>POST /order/merchant/ship → startFulfillment(current)</li>
 *   <li>POST /order/merchant/cancel/approve → cancelApproved(current)</li>
 *   <li>POST /order/merchant/cancel/reject → cancelRejected(current, previous)</li>
 * </ul>
 * 
 * <h3>内部回调接口 (/order/internal)</h3>
 * <ul>
 *   <li>POST /order/internal/payment/success → paymentSuccess(current, isDeposit, isFinalPayment)</li>
 *   <li>POST /order/internal/logistics/delivered → delivered(current, afterSaleWindowOpen=true)</li>
 *   <li>POST /order/internal/refund/success → refundSuccess(current)</li>
 *   <li>POST /order/internal/timeout/unpaid-cancel → cancelRequest(current) → cancelApproved(current)</li>
 *   <li>POST /order/internal/auto/complete → completeIfNoAfterSale(current)</li>
 *   <li>POST /order/internal/auto/await-fulfillment → moveToAwaitingFulfillment(current)</li>
 * </ul>
 * 
 * <h3>状态流转链路</h3>
 * <pre>
 * 正常流程：
 * PENDING_PAYMENT → (支付成功) → PAID_CONFIRMED → (商家接单) → AWAITING_FULFILLMENT 
 * → (发货) → FULFILLING → (妥投) → COMPLETED
 * 
 * 预售流程：
 * PENDING_PAYMENT → (定金支付) → PENDING_FINAL_PAYMENT → (尾款支付) → PAID_CONFIRMED → ...
 * 
 * 取消流程：
 * 任意非终态 → (申请取消) → CANCELLING → (审批通过) → CANCELLED
 *                                    → (审批拒绝) → 回退到原状态
 * 
 * 售后流程：
 * FULFILLING/COMPLETED → (申请售后) → AFTER_SALE → (退款成功) → REFUNDED
 * </pre>
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Component
public class OrderStateTransitionService {

    /**
     * 支付成功状态转换
     * 
     * 触发接口：POST /order/internal/payment/success
     * 
     * @param current 当前状态
     * @param isDeposit 是否为定金支付
     * @param isFinalPayment 是否为尾款支付
     * @return 新状态
     */
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

    /**
     * 转为待履约状态
     * 
     * 触发接口：
     * - POST /order/merchant/order/receive (商家接单)
     * - POST /order/internal/auto/await-fulfillment (自动转换)
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus moveToAwaitingFulfillment(CoreFlowStatus current) {
        if (current == CoreFlowStatus.PAID_CONFIRMED) {
            return CoreFlowStatus.AWAITING_FULFILLMENT;
        }
        return current;
    }

    /**
     * 开始履约（发货）
     * 
     * 触发接口：POST /order/merchant/ship
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus startFulfillment(CoreFlowStatus current) {
        if (current == CoreFlowStatus.AWAITING_FULFILLMENT) {
            return CoreFlowStatus.FULFILLING;
        }
        return current;
    }

    /**
     * 妥投/签收处理
     * 
     * 触发接口：
     * - POST /order/internal/logistics/delivered (物流回调)
     * - POST /order/user/confirm-receipt (用户确认收货)
     * 
     * @param current 当前状态
     * @param afterSaleWindowOpen 是否开启售后观察期
     * @return 新状态
     */
    public CoreFlowStatus delivered(CoreFlowStatus current, boolean afterSaleWindowOpen) {
        if (current == CoreFlowStatus.FULFILLING) {
            // 交付后暂不直接进入 COMPLETED，假设此时仍在观察期，可根据业务策略直接完成
            return afterSaleWindowOpen ? CoreFlowStatus.FULFILLING : CoreFlowStatus.COMPLETED;
        }
        return current;
    }

    /**
     * 无售后自动完成
     * 
     * 触发接口：
     * - POST /order/internal/auto/complete (定时任务)
     * - POST /order/user/confirm-receipt (用户确认收货后)
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus completeIfNoAfterSale(CoreFlowStatus current) {
        if (current == CoreFlowStatus.FULFILLING) {
            return CoreFlowStatus.COMPLETED;
        }
        return current;
    }

    /**
     * 申请售后
     * 
     * 触发接口：POST /order/user/after-sale/apply
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus requestAfterSale(CoreFlowStatus current) {
        if (current == CoreFlowStatus.FULFILLING || current == CoreFlowStatus.COMPLETED) {
            return CoreFlowStatus.AFTER_SALE;
        }
        return current;
    }

    /**
     * 退款成功
     * 
     * 触发接口：POST /order/internal/refund/success
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus refundSuccess(CoreFlowStatus current) {
        if (current == CoreFlowStatus.AFTER_SALE || current == CoreFlowStatus.CANCELLING) {
            return CoreFlowStatus.REFUNDED;
        }
        return current;
    }

    /**
     * 申请取消
     * 
     * 触发接口：
     * - POST /order/user/cancel/apply (用户申请)
     * - POST /order/internal/timeout/unpaid-cancel (超时取消)
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus cancelRequest(CoreFlowStatus current) {
        if (!TerminalStateChecker.isTerminal(current) && current != CoreFlowStatus.CANCELLING) {
            return CoreFlowStatus.CANCELLING;
        }
        return current;
    }

    /**
     * 取消审批通过
     * 
     * 触发接口：
     * - POST /order/merchant/cancel/approve (商家同意)
     * - POST /order/internal/timeout/unpaid-cancel (超时自动取消)
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus cancelApproved(CoreFlowStatus current) {
        if (current == CoreFlowStatus.CANCELLING) {
            return CoreFlowStatus.CANCELLED;
        }
        return current;
    }

    /**
     * 取消审批拒绝
     * 
     * 触发接口：POST /order/merchant/cancel/reject
     * 
     * @param current 当前状态
     * @param previous 拒绝后回退的状态
     * @return 新状态
     */
    public CoreFlowStatus cancelRejected(CoreFlowStatus current, CoreFlowStatus previous) {
        if (current == CoreFlowStatus.CANCELLING) {
            return previous; // 回退
        }
        return current;
    }

    /**
     * 换货完成
     * 
     * 触发场景：售后换货流程完成
     * 
     * @param current 当前状态
     * @return 新状态
     */
    public CoreFlowStatus exchangeCompleted(CoreFlowStatus current) {
        if (current == CoreFlowStatus.AFTER_SALE) {
            return CoreFlowStatus.COMPLETED; // 新订单另起，这里完成
        }
        return current;
    }
}


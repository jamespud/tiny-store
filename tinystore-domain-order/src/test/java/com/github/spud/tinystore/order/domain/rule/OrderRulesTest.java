package com.github.spud.tinystore.order.domain.rule;

import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单规则单元测试
 */
@DisplayName("订单规则测试")
class OrderRulesTest {

    // ========== OrderStatusRules 测试 ==========

    @Test
    @DisplayName("订单状态：PENDING_PAY -> PENDING_SHIP 允许")
    void testOrderStatus_PendingPayToPendingShip_Allowed() {
        boolean result = OrderStatusRules.isTransitionAllowed(OrderStatus.PENDING_PAY, OrderStatus.PENDING_SHIP);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("订单状态：PENDING_PAY -> CLOSED 允许")
    void testOrderStatus_PendingPayToClosed_Allowed() {
        boolean result = OrderStatusRules.isTransitionAllowed(OrderStatus.PENDING_PAY, OrderStatus.CLOSED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("订单状态：PENDING_PAY -> SUCCESS 不允许")
    void testOrderStatus_PendingPayToSuccess_NotAllowed() {
        boolean result = OrderStatusRules.isTransitionAllowed(OrderStatus.PENDING_PAY, OrderStatus.SUCCESS);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("订单状态：SUCCESS -> PENDING_PAY 不允许（终态）")
    void testOrderStatus_SuccessToPendingPay_NotAllowed() {
        boolean result = OrderStatusRules.isTransitionAllowed(OrderStatus.SUCCESS, OrderStatus.PENDING_PAY);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("订单状态：CLOSED -> PENDING_SHIP 不允许（终态）")
    void testOrderStatus_ClosedToPendingShip_NotAllowed() {
        boolean result = OrderStatusRules.isTransitionAllowed(OrderStatus.CLOSED, OrderStatus.PENDING_SHIP);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("订单状态：null 处理")
    void testOrderStatus_NullHandling() {
        assertThat(OrderStatusRules.isTransitionAllowed((OrderStatus) null, OrderStatus.PENDING_SHIP)).isFalse();
        assertThat(OrderStatusRules.isTransitionAllowed(OrderStatus.PENDING_PAY, (OrderStatus) null)).isFalse();
        assertThat(OrderStatusRules.isTransitionAllowed((OrderStatus) null, (OrderStatus) null)).isFalse();
    }

    @Test
    @DisplayName("订单状态：字符串版本 - 合法迁移")
    void testOrderStatus_StringVersion_Allowed() {
        boolean result = OrderStatusRules.isTransitionAllowed("PENDING_PAY", "PENDING_SHIP");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("订单状态：字符串版本 - 非法迁移")
    void testOrderStatus_StringVersion_NotAllowed() {
        boolean result = OrderStatusRules.isTransitionAllowed("SUCCESS", "PENDING_PAY");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("订单状态：字符串版本 - 无效状态码")
    void testOrderStatus_StringVersion_InvalidCode() {
        boolean result = OrderStatusRules.isTransitionAllowed("INVALID_STATUS", "PENDING_SHIP");
        assertThat(result).isFalse();
    }

    // ========== PayStatusRules 测试 ==========

    @Test
    @DisplayName("支付状态：UNPAID -> PAID 允许")
    void testPayStatus_UnpaidToPaid_Allowed() {
        boolean result = PayStatusRules.isTransitionAllowed(PayStatus.UNPAID, PayStatus.PAID);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("支付状态：PAID -> PART_REFUNDED 允许")
    void testPayStatus_PaidToPartRefunded_Allowed() {
        boolean result = PayStatusRules.isTransitionAllowed(PayStatus.PAID, PayStatus.PART_REFUNDED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("支付状态：PAID -> REFUNDED 允许")
    void testPayStatus_PaidToRefunded_Allowed() {
        boolean result = PayStatusRules.isTransitionAllowed(PayStatus.PAID, PayStatus.REFUNDED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("支付状态：PART_REFUNDED -> REFUNDED 允许")
    void testPayStatus_PartRefundedToRefunded_Allowed() {
        boolean result = PayStatusRules.isTransitionAllowed(PayStatus.PART_REFUNDED, PayStatus.REFUNDED);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("支付状态：REFUNDED -> PAID 不允许（终态）")
    void testPayStatus_RefundedToPaid_NotAllowed() {
        boolean result = PayStatusRules.isTransitionAllowed(PayStatus.REFUNDED, PayStatus.PAID);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("支付状态：UNPAID -> PART_REFUNDED 不允许")
    void testPayStatus_UnpaidToPartRefunded_NotAllowed() {
        boolean result = PayStatusRules.isTransitionAllowed(PayStatus.UNPAID, PayStatus.PART_REFUNDED);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("支付状态：null 处理")
    void testPayStatus_NullHandling() {
        assertThat(PayStatusRules.isTransitionAllowed(null, PayStatus.PAID)).isFalse();
        assertThat(PayStatusRules.isTransitionAllowed(PayStatus.UNPAID, null)).isFalse();
        assertThat(PayStatusRules.isTransitionAllowed(null, null)).isFalse();
    }

    @Test
    @DisplayName("支付状态：获取允许的目标状态")
    void testPayStatus_GetAllowedTargets() {
        assertThat(PayStatusRules.getAllowedTargets(PayStatus.UNPAID)).containsExactly(PayStatus.PAID);
        assertThat(PayStatusRules.getAllowedTargets(PayStatus.PAID)).containsExactlyInAnyOrder(PayStatus.PART_REFUNDED, PayStatus.REFUNDED);
        assertThat(PayStatusRules.getAllowedTargets(PayStatus.REFUNDED)).isEmpty();
    }
}

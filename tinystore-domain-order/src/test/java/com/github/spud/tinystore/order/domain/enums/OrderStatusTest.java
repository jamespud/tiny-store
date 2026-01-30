package com.github.spud.tinystore.order.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 订单状态枚举测试
 */
class OrderStatusTest {

    @Test
    void testOrderStatusEnum() {
        // 检查所有状态都被定义
        assertNotNull(OrderStatus.PENDING_PAY);
        assertNotNull(OrderStatus.PENDING_SHIP);
        assertNotNull(OrderStatus.PENDING_RECEIVE);
        assertNotNull(OrderStatus.SUCCESS);
        assertNotNull(OrderStatus.CLOSED);
    }

    @Test
    void testOrderStatusCode() {
        assertEquals("PENDING_PAY", OrderStatus.PENDING_PAY.getCode());
        assertEquals("PENDING_SHIP", OrderStatus.PENDING_SHIP.getCode());
        assertEquals("PENDING_RECEIVE", OrderStatus.PENDING_RECEIVE.getCode());
        assertEquals("SUCCESS", OrderStatus.SUCCESS.getCode());
        assertEquals("CLOSED", OrderStatus.CLOSED.getCode());
    }

    @Test
    void testPayStatusEnum() {
        // 检查所有支付状态都被定义
        assertNotNull(PayStatus.UNPAID);
        assertNotNull(PayStatus.PAID);
        assertNotNull(PayStatus.PART_REFUNDED);
        assertNotNull(PayStatus.REFUNDED);
    }

    @Test
    void testPayStatusCode() {
        assertEquals("UNPAID", PayStatus.UNPAID.getCode());
        assertEquals("PAID", PayStatus.PAID.getCode());
        assertEquals("PART_REFUNDED", PayStatus.PART_REFUNDED.getCode());
        assertEquals("REFUNDED", PayStatus.REFUNDED.getCode());
    }
}

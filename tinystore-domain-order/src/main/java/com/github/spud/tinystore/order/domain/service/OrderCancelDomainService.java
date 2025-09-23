package com.github.spud.tinystore.order.domain.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.github.spud.tinystore.order.domain.enums.CancelDecisionType;
import com.github.spud.tinystore.order.domain.event.OrderStatus;
import com.github.spud.tinystore.order.domain.model.OrderLine;

/**
 * 订单取消领域服务
 *
 * @author Spud
 * @date 2025/8/28
 */
@Service
public class OrderCancelDomainService {

    /**
     * 判断订单取消决策
     *
     * @param order 订单
     * @param now   当前时间
     * @return 取消决策类型
     */
    public CancelDecisionType decide(OrderLine order, Instant now) {
        // 订单不存在的情况
        if (order == null) {
            return CancelDecisionType.NOT_ALLOWED;
        }

        // 获取订单状态
        String status = getOrderStatus(order);
        if (status == null) {
            return CancelDecisionType.NOT_ALLOWED;
        }
        // 简单取消集合（未支付/待处理）
        if (statusEquals(status, OrderStatus.CREATED, OrderStatus.PAYMENT_PROCESSING)) {
            return CancelDecisionType.ALLOW_SIMPLE;
        }
        // 已支付未发货（包括待接单/待发货等） → 退款后取消
        if (statusEquals(status, OrderStatus.PAID, OrderStatus.ACCEPT_PENDING, OrderStatus.ACCEPTED,
                OrderStatus.PACKING, OrderStatus.SHIP_PENDING)) {
            return CancelDecisionType.REFUND_THEN_CANCEL;
        }
        // 已发货及终态（含部分发货） → 不允许（引导售后）
        if (statusEquals(status, OrderStatus.PARTIALLY_SHIPPED, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                OrderStatus.COMPLETED)) {
            return CancelDecisionType.NOT_ALLOWED;
        }
        // 其他异常/关闭类 → 不允许
        return CancelDecisionType.NOT_ALLOWED;
    }

    /**
     * 获取订单状态
     *
     * @param order 订单
     * @return 订单状态
     */
    private String getOrderStatus(OrderLine order) {
        // 获取订单状态 - 需要从Order或SubOrder中获取正确的状态
        // TODO: Fix this to use the proper status from Order or SubOrder
        return "UNKNOWN"; // order.orderStatus().getCode();
    }

    private boolean statusEquals(String code, OrderStatus... statuses) {
        for (OrderStatus s : statuses) {
            if (s.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

}

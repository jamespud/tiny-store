package com.github.spud.tinystore.order.domain.rule;

import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单履约状态迁移规则
 */
public class OrderStatusRules {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
        OrderStatus.PENDING_PAY, new HashSet<>(Set.of(OrderStatus.PENDING_SHIP, OrderStatus.CLOSED)),
        OrderStatus.PENDING_SHIP, new HashSet<>(Set.of(OrderStatus.PENDING_RECEIVE, OrderStatus.CLOSED)),
        OrderStatus.PENDING_RECEIVE, new HashSet<>(Set.of(OrderStatus.SUCCESS, OrderStatus.CLOSED)),
        OrderStatus.SUCCESS, new HashSet<>(),
        OrderStatus.CLOSED, new HashSet<>()
    );

    /**
     * 检验状态迁移是否合法
     *
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @return true 允许迁移，false 不允许
     */
    public static boolean isTransitionAllowed(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        Set<OrderStatus> allowedTargets = ALLOWED_TRANSITIONS.get(currentStatus);
        return allowedTargets != null && allowedTargets.contains(targetStatus);
    }

    /**
     * 检验状态迁移是否合法（字符串版本）
     *
     * @param currentStatusCode 当前状态编码
     * @param targetStatusCode 目标状态编码
     * @return true 允许迁移，false 不允许
     */
    public static boolean isTransitionAllowed(String currentStatusCode, String targetStatusCode) {
        try {
            OrderStatus current = OrderStatus.getByCode(currentStatusCode);
            OrderStatus target = OrderStatus.getByCode(targetStatusCode);
            return isTransitionAllowed(current, target);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

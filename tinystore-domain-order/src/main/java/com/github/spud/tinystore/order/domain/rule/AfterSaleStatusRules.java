package com.github.spud.tinystore.order.domain.rule;

import com.github.spud.tinystore.order.domain.enums.AfterSaleStatus;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 售后状态迁移规则
 */
public class AfterSaleStatusRules {

    private static final Map<AfterSaleStatus, Set<AfterSaleStatus>> ALLOWED_TRANSITIONS = Map.of(
        AfterSaleStatus.APPLIED, new HashSet<>(Set.of(AfterSaleStatus.APPROVED, AfterSaleStatus.REJECTED)),
        AfterSaleStatus.APPROVED, new HashSet<>(Set.of(AfterSaleStatus.REFUNDING, AfterSaleStatus.CLOSED)),
        AfterSaleStatus.REJECTED, new HashSet<>(),
        AfterSaleStatus.REFUNDING, new HashSet<>(Set.of(AfterSaleStatus.REFUNDED, AfterSaleStatus.CLOSED)),
        AfterSaleStatus.REFUNDED, new HashSet<>(),
        AfterSaleStatus.CLOSED, new HashSet<>()
    );

    /**
     * 检验状态迁移是否合法
     *
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @return true 允许迁移，false 不允许
     */
    public static boolean isTransitionAllowed(AfterSaleStatus currentStatus, AfterSaleStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        Set<AfterSaleStatus> allowedTargets = ALLOWED_TRANSITIONS.get(currentStatus);
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
            AfterSaleStatus current = AfterSaleStatus.getByCode(currentStatusCode);
            AfterSaleStatus target = AfterSaleStatus.getByCode(targetStatusCode);
            return isTransitionAllowed(current, target);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

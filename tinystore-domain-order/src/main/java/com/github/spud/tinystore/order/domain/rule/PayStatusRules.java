package com.github.spud.tinystore.order.domain.rule;

import com.github.spud.tinystore.order.domain.enums.PayStatus;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 支付状态迁移规则
 */
public class PayStatusRules {

    private static final Map<PayStatus, Set<PayStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        // UNPAID 可以转换为 PAID
        ALLOWED_TRANSITIONS.put(PayStatus.UNPAID, EnumSet.of(PayStatus.PAID));

        // PAID 可以转换为 PART_REFUNDED 或 REFUNDED
        ALLOWED_TRANSITIONS.put(PayStatus.PAID, EnumSet.of(PayStatus.PART_REFUNDED, PayStatus.REFUNDED));

        // PART_REFUNDED 可以继续转换为 REFUNDED
        ALLOWED_TRANSITIONS.put(PayStatus.PART_REFUNDED, EnumSet.of(PayStatus.REFUNDED));

        // REFUNDED 是终态，不可再转换
        ALLOWED_TRANSITIONS.put(PayStatus.REFUNDED, EnumSet.noneOf(PayStatus.class));
    }

    /**
     * 判断从 fromStatus 到 toStatus 的状态迁移是否合法
     *
     * @param fromStatus 当前状态
     * @param toStatus   目标状态
     * @return 是否允许迁移
     */
    public static boolean isTransitionAllowed(PayStatus fromStatus, PayStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        Set<PayStatus> allowedTargets = ALLOWED_TRANSITIONS.get(fromStatus);
        return allowedTargets != null && allowedTargets.contains(toStatus);
    }

    /**
     * 获取指定状态的所有允许目标状态
     *
     * @param fromStatus 当前状态
     * @return 允许的目标状态集合
     */
    public static Set<PayStatus> getAllowedTargets(PayStatus fromStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(fromStatus, EnumSet.noneOf(PayStatus.class));
    }
}

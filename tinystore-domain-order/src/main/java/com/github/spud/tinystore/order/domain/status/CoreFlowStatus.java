package com.github.spud.tinystore.order.domain.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 核心订单主生命周期状态（CORE_FLOW）
 * 终态集合: COMPLETED, CANCELLED, CLOSED, REFUNDED
 */
public enum CoreFlowStatus {
    CREATED("CREATED", "已创建", false, "CORE_FLOW"),
    PENDING_PAYMENT("PENDING_PAYMENT", "待支付", false, "CORE_FLOW"),
    PENDING_FINAL_PAYMENT("PENDING_FINAL_PAYMENT", "待付尾款", false, "CORE_FLOW"),
    PAID_CONFIRMED("PAID_CONFIRMED", "支付已确认", false, "CORE_FLOW"),
    AWAITING_FULFILLMENT("AWAITING_FULFILLMENT", "待履约", false, "CORE_FLOW"),
    FULFILLING("FULFILLING", "履约中", false, "CORE_FLOW"),
    AFTER_SALE("AFTER_SALE", "售后中", false, "CORE_FLOW"),
    CANCELLING("CANCELLING", "取消中", false, "CORE_FLOW"),
    COMPLETED("COMPLETED", "已完成", true, "CORE_FLOW"),
    CANCELLED("CANCELLED", "已取消", true, "CORE_FLOW"),
    CLOSED("CLOSED", "已关闭", true, "CORE_FLOW"),
    REFUNDED("REFUNDED", "已退款", true, "CORE_FLOW");

    private final String code;
    private final String label;
    private final boolean terminal;
    private final String domain;

    CoreFlowStatus(String code, String label, boolean terminal, String domain) {
        this.code = code;
        this.label = label;
        this.terminal = terminal;
        this.domain = domain;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public String getDomain() {
        return domain;
    }
}

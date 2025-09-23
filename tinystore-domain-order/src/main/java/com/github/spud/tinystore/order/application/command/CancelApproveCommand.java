package com.github.spud.tinystore.order.application.command;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * 取消审批通过命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class CancelApproveCommand {
    /**
     * 订单ID
     */
    private UUID orderId;

    /**
     * 操作员ID
     */
    private String operatorId;

    /**
     * 幂等键
     */
    private String idempotencyKey;

    /**
     * 备注
     */
    private String remark;
}
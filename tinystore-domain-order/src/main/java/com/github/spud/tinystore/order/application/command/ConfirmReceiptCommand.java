package com.github.spud.tinystore.order.application.command;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 确认收货命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class ConfirmReceiptCommand {
    /**
     * 订单ID
     */
    private UUID orderId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 幂等键
     */
    private String idempotencyKey;

    /**
     * 备注
     */
    private String remark;
}
package com.github.spud.tinystore.order.application.command;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 支付超时取消命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class UnpaidTimeoutCancelCommand {
    /**
     * 订单ID
     */
    private UUID orderId;

    /**
     * 调度ID
     */
    private String scheduleId;

    /**
     * 事件ID（幂等键）
     */
    private String eventId;
}
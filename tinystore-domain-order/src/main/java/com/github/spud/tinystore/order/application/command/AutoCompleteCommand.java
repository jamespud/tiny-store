package com.github.spud.tinystore.order.application.command;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 自动完成命令
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class AutoCompleteCommand {
    /**
     * 订单ID
     */
    private UUID orderId;
    
    /**
     * 宽限天数
     */
    private Integer graceDays;
    
    /**
     * 调度时间戳
     */
    private Long scheduledAt;
    
    /**
     * 事件ID（幂等键）
     */
    private String eventId;
}
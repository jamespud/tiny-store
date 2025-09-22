package com.github.spud.tinystore.order.application.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.github.spud.tinystore.order.interfaces.dto.request.RefundSuccessRequest;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 退款成功命令
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class RefundSuccessCommand {
    /**
     * 订单ID
     */
    private UUID orderId;
    
    /**
     * 退款ID
     */
    private String refundId;
    
    /**
     * 退款金额
     */
    private BigDecimal amount;
    
    /**
     * 退款商品明细
     */
    private List<RefundSuccessRequest.RefundItem> items;
    
    /**
     * 退款时间戳
     */
    private Long time;
    
    /**
     * 事件ID（幂等键）
     */
    private String eventId;
}
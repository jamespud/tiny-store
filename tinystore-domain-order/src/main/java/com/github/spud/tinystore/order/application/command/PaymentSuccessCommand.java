package com.github.spud.tinystore.order.application.command;

import java.math.BigDecimal;
import java.util.UUID;

import com.github.spud.tinystore.order.interfaces.dto.request.PaymentSuccessRequest;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 支付成功命令
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class PaymentSuccessCommand {
    /**
     * 订单ID
     */
    private UUID orderId;
    
    /**
     * 支付类型
     */
    private PaymentSuccessRequest.PayType payType;
    
    /**
     * 支付金额
     */
    private BigDecimal payAmount;
    
    /**
     * 支付时间戳
     */
    private Long paidAt;
    
    /**
     * 事件ID（幂等键）
     */
    private String eventId;
    
    /**
     * 支付流水号
     */
    private String paymentId;
}
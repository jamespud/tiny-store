package com.github.spud.tinystore.order.interfaces.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import com.github.spud.tinystore.order.application.command.PaymentSuccessCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付成功回调请求DTO
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessRequest {
    /**
     * 订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private UUID orderId;
    
    /**
     * 支付类型: DEPOSIT-定金, FINAL-尾款, FULL-全款
     */
    @NotNull(message = "支付类型不能为空")
    private PayType payType;
    
    /**
     * 支付金额
     */
    @NotNull(message = "支付金额不能为空")
    private BigDecimal payAmount;
    
    /**
     * 支付时间戳
     */
    @NotNull(message = "支付时间不能为空")
    private Long paidAt;
    
    /**
     * 事件ID（幂等键）
     */
    @NotBlank(message = "事件ID不能为空")
    @Size(max = 100)
    private String eventId;
    
    /**
     * 支付流水号
     */
    @Size(max = 100)
    private String paymentId;
    
    public PaymentSuccessCommand toCommand() {
        return new PaymentSuccessCommand(orderId, payType, payAmount, paidAt, eventId, paymentId);
    }
    
    public String getEventId() {
        return eventId;
    }
    
    /**
     * 支付类型枚举
     */
    public enum PayType {
        DEPOSIT,    // 定金
        FINAL,      // 尾款
        FULL        // 全款
    }
}
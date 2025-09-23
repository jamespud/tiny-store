package com.github.spud.tinystore.order.interfaces.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.github.spud.tinystore.order.application.command.AfterSaleApplyCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后申请请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleApplyRequest {
    /**
     * 订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private UUID orderId;

    /**
     * 售后类型: REFUND-仅退款, RETURN_REFUND-退货退款, EXCHANGE-换货
     */
    @NotNull(message = "售后类型不能为空")
    private AfterSaleType type;

    /**
     * 原因代码
     */
    @Size(max = 64)
    private String reasonCode;

    /**
     * 退款金额（部分退款时使用）
     */
    private BigDecimal amount;

    /**
     * 退货商品明细（部分退货时使用）
     */
    private List<AfterSaleItem> items;

    /**
     * 幂等键
     */
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 100)
    private String idempotencyKey;

    /**
     * 备注说明
     */
    @Size(max = 500)
    private String remark;

    public AfterSaleApplyCommand toCommand(String userId) {
        return new AfterSaleApplyCommand(orderId, userId, type, reasonCode, amount, items, idempotencyKey, remark);
    }

    /**
     * 售后类型枚举
     */
    public enum AfterSaleType {
        REFUND,         // 仅退款
        RETURN_REFUND,  // 退货退款
        EXCHANGE        // 换货
    }

    /**
     * 售后商品项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AfterSaleItem {
        private String skuId;
        private Integer quantity;
        private BigDecimal amount;
    }
}
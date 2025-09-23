package com.github.spud.tinystore.order.interfaces.dto.request;

import java.util.UUID;

import com.github.spud.tinystore.order.application.command.ConfirmReceiptCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认收货请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmReceiptRequest {
    /**
     * 订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private UUID orderId;

    /**
     * 幂等键
     */
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 100)
    private String idempotencyKey;

    /**
     * 备注
     */
    @Size(max = 256)
    private String remark;

    public ConfirmReceiptCommand toCommand(String userId) {
        return new ConfirmReceiptCommand(orderId, userId, idempotencyKey, remark);
    }
}
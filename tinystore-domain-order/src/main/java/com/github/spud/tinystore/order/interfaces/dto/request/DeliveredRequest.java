package com.github.spud.tinystore.order.interfaces.dto.request;

import java.util.UUID;

import com.github.spud.tinystore.order.application.command.DeliveredCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物流妥投/签收请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveredRequest {
    /**
     * 订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private UUID orderId;

    /**
     * 运单号
     */
    @Size(max = 100)
    private String trackingNo;

    /**
     * 妥投时间戳（可选）
     */
    private Long deliveredAt;

    /**
     * 事件ID（幂等键）
     */
    @NotBlank(message = "事件ID不能为空")
    @Size(max = 100)
    private String eventId;

    /**
     * 来源：LOGISTICS-物流回调, MERCHANT-商家确认, USER-用户确认
     */
    private String source = "LOGISTICS";

    public DeliveredCommand toCommand() {
        return new DeliveredCommand(orderId, trackingNo, deliveredAt, source, eventId);
    }

    public String getEventId() {
        return eventId;
    }
}
package com.github.spud.tinystore.order.interfaces.dto.request;

import java.util.UUID;

import com.github.spud.tinystore.order.application.command.AutoCompleteCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动完成订单请求DTO
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoCompleteRequest {
    /**
     * 订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private UUID orderId;
    
    /**
     * 宽限天数
     */
    private Integer graceDays = 7;
    
    /**
     * 调度时间戳
     */
    @NotNull(message = "调度时间不能为空")
    private Long scheduledAt;
    
    /**
     * 事件ID（幂等键）
     */
    @NotBlank(message = "事件ID不能为空")
    @Size(max = 100)
    private String eventId;
    
    public AutoCompleteCommand toCommand() {
        return new AutoCompleteCommand(orderId, graceDays, scheduledAt, eventId);
    }
    
    public String getEventId() {
        return eventId;
    }
}
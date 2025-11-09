package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.MoveToAwaitFulfillmentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转待履约请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveToAwaitFulfillmentRequest {

	/**
	 * 订单ID
	 */
	@NotNull(message = "订单ID不能为空")
	private UUID orderId;

	/**
	 * 事件ID（幂等键）
	 */
	@NotBlank(message = "事件ID不能为空")
	@Size(max = 100)
	private String eventId;

	public MoveToAwaitFulfillmentCommand toCommand() {
		return new MoveToAwaitFulfillmentCommand(orderId, eventId);
	}

	public String getEventId() {
		return eventId;
	}
}
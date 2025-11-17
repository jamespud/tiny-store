package com.github.spud.tinystore.order.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

/**
 * 取消订单预览请求
 */
@Data
public class PreviewCancelRequest {

	/**
	 * 订单ID
	 */
	@NotNull(message = "订单ID不能为空")
	private UUID orderId;

	/**
	 * 取消原因（可选）
	 */
	@Size(max = 64)
	private String reasonCode;
}

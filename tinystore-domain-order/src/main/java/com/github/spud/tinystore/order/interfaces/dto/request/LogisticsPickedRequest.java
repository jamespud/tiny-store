package com.github.spud.tinystore.order.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物流揽收请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsPickedRequest {

	/**
	 * 订单ID
	 */
	@NotNull(message = "订单ID不能为空")
	private UUID orderId;

	/**
	 * 运单号
	 */
	@NotBlank(message = "运单号不能为空")
	@Size(max = 100)
	private String trackingNo;

	/**
	 * 揽收时间戳
	 */
	@NotNull(message = "揽收时间不能为空")
	private Long time;

	/**
	 * 事件ID（幂等键）
	 */
	@NotBlank(message = "事件ID不能为空")
	@Size(max = 100)
	private String eventId;

	public String getEventId() {
		return eventId;
	}
}
package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.RefundSucceededCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退款成功回调请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundSuccessRequest {

	/**
	 * 订单ID
	 */
	@NotNull(message = "订单ID不能为空")
	private UUID orderId;

	/**
	 * 退款ID
	 */
	@NotBlank(message = "退款ID不能为空")
	@Size(max = 100)
	private String refundId;

	/**
	 * 退款金额
	 */
	@NotNull(message = "退款金额不能为空")
	private BigDecimal amount;

	/**
	 * 退款商品明细（部分退款时使用）
	 */
	private List<RefundItem> items;

	/**
	 * 退款时间戳
	 */
	@NotNull(message = "退款时间不能为空")
	private Long time;

	/**
	 * 事件ID（幂等键）
	 */
	@NotBlank(message = "事件ID不能为空")
	@Size(max = 100)
	private String eventId;

	public RefundSucceededCommand toCommand() {
		return RefundSucceededCommand.builder()
			.orderId(orderId != null ? orderId.toString() : null)
			.refundId(refundId)
			.idempotencyKey(eventId)
			.build();
	}

	public String getEventId() {
		return eventId;
	}

	/**
	 * 退款商品项
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RefundItem {

		private String skuId;
		private Integer quantity;
		private BigDecimal amount;
	}
}
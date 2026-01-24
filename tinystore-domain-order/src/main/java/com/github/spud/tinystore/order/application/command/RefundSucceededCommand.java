package com.github.spud.tinystore.order.application.command;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Command for refund success callback
 */
@Data
@Builder
public class RefundSucceededCommand {

	private String orderId;
	private String refundId;
	private BigDecimal amount;
	private Long time;
	private List<RefundItem> items;
	private String idempotencyKey;

	@Data
	@Builder
	public static class RefundItem {
		private String skuId;
		private Integer quantity;
		private BigDecimal amount;
	}
}

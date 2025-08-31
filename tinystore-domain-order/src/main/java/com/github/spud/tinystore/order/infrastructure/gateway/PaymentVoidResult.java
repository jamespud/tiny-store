package com.github.spud.tinystore.order.infrastructure.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付撤销结果占位
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVoidResult {

	private boolean success;
	private String gatewayCode;
	private String message;

	public static PaymentVoidResult success() {
		return PaymentVoidResult.builder().success(true).gatewayCode("OK")
			.message("void success (mock)").build();
	}

	public static PaymentVoidResult failure(String code, String msg) {
		return PaymentVoidResult.builder().success(false).gatewayCode(code).message(msg).build();
	}
}

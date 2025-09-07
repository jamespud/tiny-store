package com.github.spud.tinystore.order.domain.enums;

/**
 * @author Spud
 * @date 2025/9/2
 */
public enum PaymentType {

	WECHAT("WECHAT", "微信支付"),
	ALIPAY("ALIPAY", "支付宝支付"),
	UNION_PAY("UNION_PAY", "银联支付"),
	COD("COD", "货到付款"),
	PAYPAL("PAYPAL", "PayPal支付"),
	APPLE_PAY("APPLE_PAY", "Apple Pay支付");

	private String code;
	private String description;

	PaymentType(String code, String description) {
		this.code = code;
		this.description = description;
	}
}



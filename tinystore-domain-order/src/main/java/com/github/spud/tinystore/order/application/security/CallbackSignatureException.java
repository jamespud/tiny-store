package com.github.spud.tinystore.order.application.security;

/**
 * 支付/退款回调验签失败（配置缺失 / 签名缺失 / 签名不合法）。
 */
public class CallbackSignatureException extends RuntimeException {

	public CallbackSignatureException(String message) {
		super(message);
	}
}

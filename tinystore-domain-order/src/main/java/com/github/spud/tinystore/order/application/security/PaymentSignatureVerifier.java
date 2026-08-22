package com.github.spud.tinystore.order.application.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付/退款回调验签（A4）。
 *
 * <p>阻断“任意人 POST 一个自填 amountCents 就能标记已付”的 demo 级缺陷。回调必须携带
 * HMAC-SHA256 签名，签名密钥为 {@code order.payment.callback-secret}（生产从密钥管理注入）。
 * 签名载荷采用竖直分隔的稳定串，防止字段拼接歧义。
 */
@Component
public class PaymentSignatureVerifier {

	private static final String HMAC_ALG = "HmacSHA256";

	private final String secret;

	public PaymentSignatureVerifier(@Value("${order.payment.callback-secret:}") String secret) {
		this.secret = secret;
	}

	public void verifyPaymentCallback(String tradeId, String paymentId, Long amountCents,
		Long timestamp, String signature) {
		String payload = tradeId + "|" + paymentId + "|" + amountCents + "|" + timestamp;
		verify(payload, signature);
	}

	public void verifyRefundCallback(String tradeId, String refundId, Long refundAmountCents,
		Long timestamp, String signature) {
		String payload = tradeId + "|" + refundId + "|" + refundAmountCents + "|" + timestamp;
		verify(payload, signature);
	}

	private void verify(String payload, String signature) {
		if (secret == null || secret.isBlank()) {
			throw new CallbackSignatureException("Payment callback secret is not configured");
		}
		if (signature == null || signature.isBlank()) {
			throw new CallbackSignatureException("Missing callback signature");
		}
		String expected = hmacSha256Hex(payload, secret);
		if (!MessageDigest.isEqual(
			expected.getBytes(StandardCharsets.UTF_8),
			signature.getBytes(StandardCharsets.UTF_8))) {
			throw new CallbackSignatureException("Invalid callback signature");
		}
	}

	private String hmacSha256Hex(String data, String key) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALG);
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
			byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("HMAC-SHA256 signing failed", e);
		}
	}
}

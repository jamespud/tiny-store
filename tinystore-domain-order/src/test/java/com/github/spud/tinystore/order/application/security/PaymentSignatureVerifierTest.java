package com.github.spud.tinystore.order.application.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A4 支付/退款回调验签：正确签名放行，篡改/缺失/错误密钥拒绝。
 */
@DisplayName("PaymentSignatureVerifier — callback signature verification (A4)")
class PaymentSignatureVerifierTest {

	private static final String SECRET = "test-callback-secret";

	@Test
	@DisplayName("valid signature should pass")
	void validSignature_shouldPass() {
		PaymentSignatureVerifier v = new PaymentSignatureVerifier(SECRET);
		String sig = hmac("trade-1|pay-1|2000|1234567890", SECRET);
		assertThatCode(() -> v.verifyPaymentCallback("trade-1", "pay-1", 2000L, 1234567890L, sig))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("tampered payload should be rejected")
	void tamperedPayload_shouldReject() {
		PaymentSignatureVerifier v = new PaymentSignatureVerifier(SECRET);
		// 用错误 payload（amount 改了）生成签名，却在合法参数下校验
		String sig = hmac("trade-1|pay-1|9999|1234567890", SECRET);
		assertThatThrownBy(() -> v.verifyPaymentCallback("trade-1", "pay-1", 2000L, 1234567890L, sig))
			.isInstanceOf(CallbackSignatureException.class)
			.hasMessageContaining("Invalid callback signature");
	}

	@Test
	@DisplayName("missing signature should be rejected")
	void missingSignature_shouldReject() {
		PaymentSignatureVerifier v = new PaymentSignatureVerifier(SECRET);
		assertThatThrownBy(() -> v.verifyPaymentCallback("trade-1", "pay-1", 2000L, 1234567890L, null))
			.isInstanceOf(CallbackSignatureException.class)
			.hasMessageContaining("Missing callback signature");
	}

	@Test
	@DisplayName("unconfigured secret should be rejected")
	void unconfiguredSecret_shouldReject() {
		PaymentSignatureVerifier v = new PaymentSignatureVerifier("");
		String sig = hmac("trade-1|pay-1|2000|1234567890", SECRET);
		assertThatThrownBy(() -> v.verifyPaymentCallback("trade-1", "pay-1", 2000L, 1234567890L, sig))
			.isInstanceOf(CallbackSignatureException.class)
			.hasMessageContaining("secret is not configured");
	}

	private String hmac(String data, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] d = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : d) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

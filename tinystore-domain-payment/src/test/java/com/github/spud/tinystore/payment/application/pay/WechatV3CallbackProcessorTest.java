package com.github.spud.tinystore.payment.application.pay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 用“编造的微信 v3 支付成功回调样例”验证 A4 真实渠道的验签 + 解密。
 * 样例由 tests/payment/wechat-callback/generate_wechat_callback.py 确定性生成。
 */
@DisplayName("WechatV3CallbackProcessor — verify+decrypt a real wechat v3 callback")
class WechatV3CallbackProcessorTest {

	// 与生成样例一致的固定演示密钥（仅测试）
	private static final String CERT_PEM = """
		-----BEGIN CERTIFICATE-----
		MIIC2jCCAcKgAwIBAgIQSxsmHz0w1JA3Ifp6I5X4FTANBgkqhkiG9w0BAQsFADAp
		MScwJQYDVQQDDB5UaW55c3RvcmUgV2VDaGF0IFBsYXRmb3JtIFRlc3QwHhcNMjYw
		ODIxMjAyOTMwWhcNMjcwODIyMjAyOTMwWjApMScwJQYDVQQDDB5UaW55c3RvcmUg
		V2VDaGF0IFBsYXRmb3JtIFRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
		AoIBAQDPmo4PKM5SLgfVCQB1o1zCWl21ExBRkPQLP7dqrNbfFecsM/5HedOoPD0C
		CRrkTz728vDnX1SWNRDKLrUQObyAuMBEUSJHXLSUF3qqnsuFt+wsQDZq8or6vSjx
		E9gcEDUAHMe/5vz4yZjIL9dYyhC18th5S7cA/LEmu4uY7Fg0U627Bon7jzdJ4wKY
		NJorV8wO0LsC/CoXu2n5SVbPJdv8lFHSKM/YUgO8jFDdJKPmz3Qo+ie4fDgc/9oG
		c2kaydJv3nsBG+Bh2djgDVXhWXxf3zQ10K+UCklVtBBRR4rqGylWoVcZg5wK+vox
		61C1C41ZQIhc5OQSmciRhOrWgxk7AgMBAAEwDQYJKoZIhvcNAQELBQADggEBAE9w
		+XN4uO+tZ+he2esrt8k8lyX57ZDGqbnPbWKBmSRK3C+gMhZnMqtGUOvlw/KvWejP
		fyD+eJngUGScog3nWrUb0rKVrV5VV6B/UcH+geshUNAKSaU0gDAGXcz6n3jbeiZk
		aKHwrassPTnl4Wbk92IVtQuNomOEkC2Ay+XcabC294Y+PUbh4Nu8CIJvyY0E1YAC
		xvOCbNCk+SZkyg5MIuYaDGS13qOo+wzEiaSs3alrJNtllQWuAW62FeiodgiPe8lH
		ManIJ9Hdqx0AhunMykvV5yhV0ddwvCyJXM6XrTVDvEQ7Fmo2//aaZCEsqA2e6wtx
		zHMm6wJBME+vnnnCZcM=
		-----END CERTIFICATE-----""";
	private static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";
	private static final String SERIAL = "4B1B261F3D30D4903721FA7A2395F815";
	private static final String TS = "1787532000";
	private static final String NONCE_HDR = "c0kZP6cSbveFpn0U";
	private static final String SIG = "p3Po/bctnXmOrNDs6m+vvlx8luS3n18E/QVR/nh7r5jjPB3m3pBSFT8J50H/YUJI9wPL7iS7elGmORFJ2qoaJfeGdOgoi2evvqtvpa7cknWFo2C5H6i5fqJm/WLTWy868htIuvl2MiqP7h+LY+aLc7Rqn596dBZgtWaDQLW4G5fUADvlHkRAU5PMK4e53gRFFZrT9ExH0uegsivERBMD0Yi065KB6O7Aw2nYDTvSOuRJBlvYzlCrKo3SlDQo7wPCkpAmY5ZRciim+66y0X2RTJDhh9MJMBvFq3N7e7N/s3dXd2fzLm2wNorbnlK2vQBYT6QALVzIAA+Vfpc2HpwM2Q==";

	private final WechatV3CallbackProcessor processor = new WechatV3CallbackProcessor();

	@Test
	@DisplayName("valid signature passes; decrypted resource carries transaction fields")
	void validSignature_andDecrypt() throws Exception {
		String body = readBody().trim();

		assertThat(processor.verifySignature(TS, NONCE_HDR, SIG, body, CERT_PEM, null))
			.isTrue();

		String plain = processor.decryptResource(resourceJsonField("ciphertext"),
			resourceJsonField("nonce"), resourceJsonField("associated_data"), API_V3_KEY);
		assertThat(plain).contains("\"out_trade_no\":\"TS202608231234567890\"");
		assertThat(plain).contains("\"trade_state\":\"SUCCESS\"");
		assertThat(plain).contains("\"total\":1000");
	}

	@Test
	@DisplayName("tampered body fails signature verification")
	void tamperedBody_failsVerify() throws Exception {
		String body = readBody().trim();
		assertThat(processor.verifySignature(TS, NONCE_HDR, SIG, body + "x", CERT_PEM, null))
			.isFalse();
	}

	private String readBody() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/wechat-pay-callback-sample.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String resourceJsonField(String field) throws Exception {
		// 从样例 JSON 的 resource 中取字段（简单字符串抽取，测试用）
		String body = readBody().trim();
		int idx = body.indexOf("\"" + field + "\"");
		if (idx < 0) {
			throw new IllegalStateException("field not found: " + field);
		}
		int colon = body.indexOf(':', idx);
		int start = body.indexOf('"', colon);
		int end = body.indexOf('"', start + 1);
		return body.substring(start + 1, end);
	}
}

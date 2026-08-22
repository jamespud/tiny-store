package com.github.spud.tinystore.payment.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.infrastructure.rpc.order.OrderClient;
import com.github.spud.tinystore.infrastructure.rpc.order.dto.request.OrderPaymentCallbackRequest;
import com.github.spud.tinystore.payment.application.pay.WechatV3CallbackProcessor;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信支付 v3 回调入口（notify_url）。
 *
 * <p>流程：接收回调 → RSA-SHA256 验签（Wechatpay-* 头）→ AES-256-GCM 解密 resource →
 * 解析交易状态 → 支付成功则通知订单域 → 应答 200/204。验签/失败应答 4XX/5XX（微信会按频次重试）。
 */
@RestController
@RequestMapping("/api/pay/wechat")
public class WechatNotifyController {

	private final WechatV3CallbackProcessor processor;
	private final OrderClient orderClient;
	private final ObjectMapper objectMapper;
	private final String certPem;
	private final String apiV3Key;

	public WechatNotifyController(
		WechatV3CallbackProcessor processor,
		OrderClient orderClient,
		ObjectMapper objectMapper,
		@Value("${wechat.pay.api-v3-key:}") String apiV3Key,
		@Value("${wechat.pay.platform-cert-pem:}") String certPem) {
		this.processor = processor;
		this.orderClient = orderClient;
		this.objectMapper = objectMapper;
		this.apiV3Key = apiV3Key;
		this.certPem = certPem;
	}

	@PostMapping("/notify")
	public ResponseEntity<Map<String, Object>> notify(
		@RequestBody(required = false) String body,
		@RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
		@RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
		@RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
		@RequestHeader(value = "Wechatpay-Signature", required = false) String signature) {
		try {
			if (body == null || body.isBlank()) {
				return fail(HttpStatus.BAD_REQUEST, "empty callback body");
			}
			// 1) 验签
			if (!processor.verifySignature(timestamp, nonce, signature, body, certPem, null)) {
				return fail(HttpStatus.BAD_REQUEST, "signature verification failed");
			}
			// 2) 解密 resource
			JsonNode resource = objectMapper.readTree(body).path("resource");
			String plain = processor.decryptResource(
				resource.path("ciphertext").asText(),
				resource.path("nonce").asText(),
				resource.path("associated_data").asText(),
				apiV3Key);
			JsonNode txn = objectMapper.readTree(plain);
			// 3) 仅处理支付成功
			if (!"SUCCESS".equals(txn.path("trade_state").asText())) {
				return ResponseEntity.ok().build();
			}
			String outTradeNo = txn.path("out_trade_no").asText();
			long totalCents = txn.path("amount").path("total").asLong();
			// 4) 通知订单域确认支付
			orderClient.paymentCallback(
				outTradeNo,
				"wechat-notify-" + outTradeNo,
				OrderPaymentCallbackRequest.builder()
					.paymentIntentId(outTradeNo)
					.amountCents(totalCents)
					.traceId("wechat-" + outTradeNo)
					.build());
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			return fail(HttpStatus.BAD_REQUEST, "FAIL: " + e.getMessage());
		}
	}

	private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {
		Map<String, Object> body = new HashMap<>();
		body.put("code", "FAIL");
		body.put("message", message);
		return ResponseEntity.status(status).body(body);
	}
}

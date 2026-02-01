package com.github.spud.tinystore.infrastructure.rpc.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 订单服务 Feign 客户端
 * 服务名：order-service
 * 用途：支付域调用订单域的回调接口（支付成功、退款结果通知）
 * 
 * @author Spud
 * @date 2026/01/30
 */
@FeignClient(value = "order-service", contextId = "orderClient", url = "${feign.client.url.order:}")
public interface OrderClient {

	/**
	 * 支付成功回调（支付域通知订单域支付已完成）
	 * 
	 * @param tradeId 交易ID
	 * @param idempotencyKey 幂等键
	 * @param request 支付回调请求体（paymentIntentId/paymentOrderId/amountCents/tradeNo等）
	 * @return 回调结果
	 */
	@PostMapping("/order/trades/{tradeId}/pay/callback")
	Map<String, Object> paymentCallback(
		@PathVariable("tradeId") String tradeId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody Map<String, Object> request
	);

	/**
	 * 退款结果通知（支付域通知订单域退款执行结果）
	 * 
	 * @param tradeId 交易ID
	 * @param idempotencyKey 幂等键
	 * @param request 退款回调请求体（refundId/refundStatus/refundAmountCents等）
	 * @return 回调结果
	 */
	@PostMapping("/order/trades/{tradeId}/refund/callback")
	Map<String, Object> refundCallback(
		@PathVariable("tradeId") String tradeId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody Map<String, Object> request
	);

}

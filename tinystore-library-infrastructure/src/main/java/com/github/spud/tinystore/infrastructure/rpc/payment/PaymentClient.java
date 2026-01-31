package com.github.spud.tinystore.infrastructure.rpc.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 支付服务 Feign 客户端
 * 服务名：pay-service（与网关路由 lb://pay-service 严格对齐）
 * 
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient(value = "pay-service", contextId = "paymentClient")
public interface PaymentClient {

	/**
	 * 获取收银台支付参数/链接
	 * 
	 * @param paymentIntentId 订单域支付意图ID
	 * @return 收银台参数（包含支付链接、JSAPI参数等）
	 */
	@GetMapping("/api/pay/cashier/{paymentIntentId}")
	Map<String, Object> getCashier(@PathVariable("paymentIntentId") String paymentIntentId);

	/**
	 * 查询支付状态
	 * 
	 * @param paymentIntentId 订单域支付意图ID
	 * @return 支付状态（UNPAID/PAID/CLOSED/EXPIRED）
	 */
	@GetMapping("/api/pay/state/{paymentIntentId}")
	Map<String, Object> queryPayState(@PathVariable("paymentIntentId") String paymentIntentId);

	/**
	 * 关闭支付单（订单超时/用户取消时调用）
	 * 
	 * @param paymentIntentId 订单域支付意图ID
	 * @return 关闭结果
	 */
	@PostMapping("/api/pay/close/{paymentIntentId}")
	Map<String, Object> closePayOrder(@PathVariable("paymentIntentId") String paymentIntentId);

}

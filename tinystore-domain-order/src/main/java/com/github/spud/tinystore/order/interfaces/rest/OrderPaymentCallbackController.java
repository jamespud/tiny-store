package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.OrderPaymentCallbackAppService;
import com.github.spud.tinystore.order.application.service.OrderPaymentCallbackAppService.PaymentCallbackResult;
import com.github.spud.tinystore.order.application.service.OrderPaymentCallbackAppService.PaymentSuccessCallbackRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * 订单支付回调接口
 * 接收第三方支付平台的回调通知
 */
@Slf4j
@RestController
@RequestMapping("/api/orders/payment/callback")
@RequiredArgsConstructor
public class OrderPaymentCallbackController {

	private final OrderPaymentCallbackAppService paymentCallbackAppService;

	/**
	 * 支付成功回调接口
	 *
	 * @param request      HTTP 请求
	 * @param callbackData 回调数据
	 * @return 回调处理结果
	 */
	@PostMapping("/success")
	public ResponseEntity<CallbackResponse> handlePaymentSuccess(
		HttpServletRequest request,
		@RequestBody Map<String, Object> callbackData) {

		// 设置追踪ID
		String traceId = generateTraceId();
		MDC.put("traceId", traceId);

		try {
			log.info("收到支付成功回调: remoteAddr={}, callbackData={}",
				request.getRemoteAddr(), callbackData);

			// 解析回调数据
			PaymentSuccessCallbackRequest callbackRequest = parseCallbackData(callbackData);
			callbackRequest.setRequestId(generateRequestId(callbackRequest));

			// 业务处理
			PaymentCallbackResult result = paymentCallbackAppService.handlePaymentSuccess(callbackRequest);

			// 构造响应
			CallbackResponse response = new CallbackResponse(
				result.getStatus().equals("SUCCESS") ? "SUCCESS" : "FAILURE",
				result.getMessage(),
				result.getOrderNo(),
				traceId
			);

			log.info("支付回调处理完成: orderNo={}, status={}, traceId={}",
				result.getOrderNo(), result.getStatus(), traceId);

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("支付回调处理异常: traceId={}", traceId, e);

			CallbackResponse errorResponse = new CallbackResponse(
				"FAILURE",
				"内部处理异常: " + e.getMessage(),
				null,
				traceId
			);

			return ResponseEntity.status(500).body(errorResponse);

		} finally {
			MDC.clear();
		}
	}

	/**
	 * 支付失败回调接口
	 */
	@PostMapping("/failure")
	public ResponseEntity<CallbackResponse> handlePaymentFailure(
		HttpServletRequest request,
		@RequestBody Map<String, Object> callbackData) {

		String traceId = generateTraceId();
		MDC.put("traceId", traceId);

		try {
			log.info("收到支付失败回调: remoteAddr={}, callbackData={}",
				request.getRemoteAddr(), callbackData);

			// 这里可以实现支付失败的处理逻辑
			// 例如：订单状态回滚、通知用户等

			String orderNo = (String) callbackData.get("orderNo");

			CallbackResponse response = new CallbackResponse(
				"SUCCESS",
				"支付失败回调处理完成",
				orderNo,
				traceId
			);

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("支付失败回调处理异常: traceId={}", traceId, e);

			return ResponseEntity.status(500).body(new CallbackResponse(
				"FAILURE",
				"处理异常: " + e.getMessage(),
				null,
				traceId
			));

		} finally {
			MDC.clear();
		}
	}

	/**
	 * 查询回调处理结果
	 */
	@GetMapping("/result/{requestId}")
	public ResponseEntity<CallbackResponse> queryCallbackResult(@PathVariable String requestId) {
		String traceId = generateTraceId();
		MDC.put("traceId", traceId);

		try {
			var result = paymentCallbackAppService.queryCallbackResult(requestId);

			if (result.isPresent()) {
				PaymentCallbackResult callbackResult = result.get();
				CallbackResponse response = new CallbackResponse(
					callbackResult.getStatus(),
					callbackResult.getMessage(),
					callbackResult.getOrderNo(),
					traceId
				);
				return ResponseEntity.ok(response);
			} else {
				return ResponseEntity.notFound().build();
			}

		} catch (Exception e) {
			log.error("查询回调结果异常: requestId={}, traceId={}", requestId, traceId, e);
			return ResponseEntity.status(500).body(new CallbackResponse(
				"FAILURE",
				"查询异常: " + e.getMessage(),
				null,
				traceId
			));
		} finally {
			MDC.clear();
		}
	}

	// ============= 私有方法 =============

	/**
	 * 解析回调数据
	 */
	private PaymentSuccessCallbackRequest parseCallbackData(Map<String, Object> callbackData) {
		PaymentSuccessCallbackRequest request = new PaymentSuccessCallbackRequest();

		request.setOrderNo((String) callbackData.get("orderNo"));
		request.setPaymentTransactionId((String) callbackData.get("paymentTransactionId"));
		request.setPaymentMethod((String) callbackData.get("paymentMethod"));
		request.setSignature((String) callbackData.get("signature"));

		// 金额处理
		Object amountObj = callbackData.get("amount");
		if (amountObj instanceof String) {
			request.setAmount(new BigDecimal((String) amountObj));
		} else if (amountObj instanceof Number) {
			request.setAmount(BigDecimal.valueOf(((Number) amountObj).doubleValue()));
		}

		request.setExtraData(callbackData);

		// 基本验证
		if (request.getOrderNo() == null || request.getPaymentTransactionId() == null) {
			throw new IllegalArgumentException("回调数据缺少必要字段: orderNo 或 paymentTransactionId");
		}

		return request;
	}

	/**
	 * 生成请求ID (用作幂等键)
	 */
	private String generateRequestId(PaymentSuccessCallbackRequest request) {
		// 使用订单号+支付流水号生成唯一的请求ID
		return String.format("PAY_CALLBACK_%s_%s",
			request.getOrderNo(),
			request.getPaymentTransactionId());
	}

	/**
	 * 生成追踪ID
	 */
	private String generateTraceId() {
		return "TRACE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	// ============= 响应类 =============

	/**
	 * 回调响应
	 */
	public static class CallbackResponse {
		private String status;      // SUCCESS, FAILURE
		private String message;     // 处理结果消息
		private String orderNo;     // 订单号
		private String traceId;     // 追踪ID
		private long timestamp;     // 响应时间戳

		public CallbackResponse() {
			this.timestamp = System.currentTimeMillis();
		}

		public CallbackResponse(String status, String message, String orderNo, String traceId) {
			this();
			this.status = status;
			this.message = message;
			this.orderNo = orderNo;
			this.traceId = traceId;
		}

		// Getters and Setters
		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getOrderNo() {
			return orderNo;
		}

		public void setOrderNo(String orderNo) {
			this.orderNo = orderNo;
		}

		public String getTraceId() {
			return traceId;
		}

		public void setTraceId(String traceId) {
			this.traceId = traceId;
		}

		public long getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}
	}
}
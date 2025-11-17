package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.infrastructure.vo.Response;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import com.github.spud.tinystore.order.interfaces.dto.request.AutoCompleteRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.DeliveredRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.LogisticsPickedRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.MoveToAwaitFulfillmentRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.PaymentSuccessRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.RefundSuccessRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.UnpaidTimeoutRequest;
import com.github.spud.tinystore.order.interfaces.dto.response.BasicAckVO;
import com.github.spud.tinystore.order.interfaces.util.IdempotencyHelper;
import com.github.spud.tinystore.order.infrastructure.acl.SignatureVerifier;
import com.github.spud.tinystore.order.interfaces.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口控制器 - 处理支付回调、物流回调、系统作业等内部调用
 *
 * @author Spud
 * @date 2025/9/22
 */
@Slf4j
@RestController
@RequestMapping("/order/internal")
public class InternalOrderController {

	private final OrderApplicationService applicationService;
	private final SignatureVerifier signatureVerifier;
	private final boolean signatureEnabled;

	public InternalOrderController(
		OrderApplicationService applicationService,
		ObjectProvider<SignatureVerifier> signatureVerifierProvider,
		@Value("${order.signature.enabled:false}") boolean signatureEnabled
	) {
		this.applicationService = applicationService;
		this.signatureVerifier = signatureVerifierProvider.getIfAvailable(() -> new SignatureVerifier() {
			@Override
			public boolean verify(String signature, String timestamp, Object payload) {
				return true;
			}
		});
		this.signatureEnabled = signatureEnabled;
	}

	/**
	 * 支付成功回调（支持定金、尾款、全款）
	 */
	@PostMapping(value = "/payment/success", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> onPaymentSuccess(@Valid @RequestBody PaymentSuccessRequest request,
		HttpServletRequest httpRequest) {
		ensureSignatureIfEnabled(httpRequest, request, "payment.success");
		// 验证幂等性键（由网关强制执行，这里仅记录）
		IdempotencyHelper.validateIdempotencyKey(true);

		// 记录幂等性操作日志
		IdempotencyHelper.logIdempotencyOperation("payment.success",
			request.getOrderId().toString(), "Processing payment success callback");

		// TODO: 签名校验
		log.info("Processing payment success for order: {}, payType: {}, amount: {}",
			request.getOrderId(), request.getPayType(), request.getPayAmount());

		applicationService.onPaymentSuccess(request.toCommand());

		log.info("Payment success processed successfully for order: {}", request.getOrderId());
		return Response.ok(new BasicAckVO("success", "Payment processed", request.getEventId()));
	}

	/**
	 * 物流揽收成功回调
	 */
	@PostMapping(value = "/logistics/picked", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> onLogisticsPicked(@Valid @RequestBody LogisticsPickedRequest request,
		HttpServletRequest httpRequest) {
		ensureSignatureIfEnabled(httpRequest, request, "logistics.picked");
		// TODO: 幂等校验 (eventId)
		// TODO: 实现物流揽收处理
		return Response.ok(new BasicAckVO("success", "Logistics picked", request.getEventId()));
	}

	/**
	 * 物流妥投/签收回调
	 */
	@PostMapping(value = "/logistics/delivered", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> onLogisticsDelivered(@Valid @RequestBody DeliveredRequest request,
		HttpServletRequest httpRequest) {
		ensureSignatureIfEnabled(httpRequest, request, "logistics.delivered");
		// 验证幂等性键（由网关强制执行，这里仅记录）
		IdempotencyHelper.validateIdempotencyKey(true);

		// 记录幂等性操作日志
		IdempotencyHelper.logIdempotencyOperation("logistics.delivered",
			request.getOrderId().toString(), "Processing logistics delivery callback");

		// TODO: 签名校验
		log.info("Processing logistics delivery for order: {}, source: {}",
			request.getOrderId(), request.getSource());

		applicationService.onLogisticsDelivered(request.toCommand());

		log.info("Logistics delivery processed successfully for order: {}", request.getOrderId());
		return Response.ok(new BasicAckVO("success", "Delivery processed", request.getEventId()));
	}

	/**
	 * 退款成功回调
	 */
	@PostMapping(value = "/refund/success", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> onRefundSuccess(@Valid @RequestBody RefundSuccessRequest request,
		HttpServletRequest httpRequest) {
		ensureSignatureIfEnabled(httpRequest, request, "refund.success");
		// 验证幂等性键（由网关强制执行，这里仅记录）
		IdempotencyHelper.validateIdempotencyKey(true);

		// 记录幂等性操作日志
		IdempotencyHelper.logIdempotencyOperation("refund.success",
			request.getOrderId().toString(), "Processing refund success callback");

		// TODO: 签名校验
		log.info("Processing refund success for order: {}, amount: {}",
			request.getOrderId(), request.getAmount());

		applicationService.onRefundSuccess(request.toCommand());

		log.info("Refund success processed successfully for order: {}", request.getOrderId());
		return Response.ok(new BasicAckVO("success", "Refund processed", request.getEventId()));
	}

	/**
	 * 支付超时自动取消
	 */
	@PostMapping(value = "/timeout/unpaid-cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> onUnpaidTimeout(@Valid @RequestBody UnpaidTimeoutRequest request,
		HttpServletRequest httpRequest) {
		// 系统作业触发通常无需验签；如需可开启：
		// ensureSignatureIfEnabled(httpRequest, request, "timeout.unpaid_cancel");
		// 验证幂等性键（由网关强制执行，这里仅记录）
		IdempotencyHelper.validateIdempotencyKey(true);

		// 记录幂等性操作日志
		IdempotencyHelper.logIdempotencyOperation("timeout.unpaid_cancel",
			request.getOrderId().toString(), "Processing unpaid timeout cancellation");

		log.info("Processing unpaid timeout for order: {}, scheduleId: {}",
			request.getOrderId(), request.getScheduleId());

		applicationService.timeoutCancel(request.toCommand());

		log.info("Unpaid timeout processed successfully for order: {}", request.getOrderId());
		return Response.ok(
			new BasicAckVO("success", "Order cancelled due to timeout", request.getEventId()));
	}

	/**
	 * 签收后N天自动完成
	 */
	@PostMapping(value = "/auto/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> autoComplete(@Valid @RequestBody AutoCompleteRequest request,
		HttpServletRequest httpRequest) {
		// 系统作业触发通常无需验签；如需可开启：
		// ensureSignatureIfEnabled(httpRequest, request, "auto.complete");
		// 验证幂等性键（由网关强制执行，这里仅记录）
		IdempotencyHelper.validateIdempotencyKey(true);

		// 记录幂等性操作日志
		IdempotencyHelper.logIdempotencyOperation("auto.complete",
			request.getOrderId().toString(), "Processing auto completion");

		log.info("Processing auto complete for order: {}, graceDays: {}",
			request.getOrderId(), request.getGraceDays());

		applicationService.autoComplete(request.toCommand());

		log.info("Auto complete processed successfully for order: {}", request.getOrderId());
		return Response.ok(new BasicAckVO("success", "Order auto-completed", request.getEventId()));
	}

	/**
	 * 支付后自动转待履约（保障性作业）
	 */
	@PostMapping(value = "/auto/await-fulfillment", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> moveToAwaitFulfillment(
		@Valid @RequestBody MoveToAwaitFulfillmentRequest request,
		HttpServletRequest httpRequest) {
		// 系统保障性作业，默认不验签
		// TODO: 幂等校验 (eventId)
		applicationService.moveToAwaitFulfillment(request.toCommand());
		return Response.ok(
			new BasicAckVO("success", "Moved to awaiting fulfillment", request.getEventId()));
	}

	private void ensureSignatureIfEnabled(HttpServletRequest httpRequest, Object payload, String event) {
		if (!signatureEnabled) return;
		String signature = httpRequest.getHeader("X-Signature");
		String timestamp = httpRequest.getHeader("X-Timestamp");
		if (signature == null || signature.isBlank() || timestamp == null || timestamp.isBlank()) {
			throw new UnauthorizedException("Missing signature headers: X-Signature/X-Timestamp");
		}
		boolean ok = signatureVerifier.verify(signature, timestamp, payload);
		if (!ok) {
			throw new UnauthorizedException("Invalid signature for event: " + event);
		}
	}
}
package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.infrastructure.service.UserIdProvider;
import com.github.spud.tinystore.infrastructure.vo.Response;
import com.github.spud.tinystore.order.application.service.IdempotencyStorage;
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand;
import com.github.spud.tinystore.order.application.command.user.SubmitOrderCommand;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import com.github.spud.tinystore.order.interfaces.dto.request.AfterSaleApplyRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.CancelRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.ConfirmReceiptRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.CreateOrderRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.PreviewOrderRequest;
import com.github.spud.tinystore.order.interfaces.dto.response.BasicAckVO;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderVO;
import com.github.spud.tinystore.order.interfaces.dto.response.PreviewOrderVO;
import com.github.spud.tinystore.order.interfaces.util.IdempotencyHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;

/**
 * 订单控制器(消费者) 1. 创建订单 2. 取消订单 3. 确认收货 4. 订单列表查询 (TODO) 5. 订单详情查询 (TODO) 6. 申请退款/售后 (TODO)
 *
 * @author Spud
 * @date 2025/9/2
 */
@Slf4j
@RestController
@RequestMapping("/order/user")
public class UserOrderController {

	private final OrderApplicationService applicationService;
	private final IdempotencyStorage idempotencyStorage;
	private final OrderMetrics orderMetrics;

	public UserOrderController(OrderApplicationService applicationService,
		ObjectProvider<IdempotencyStorage> idempotencyStorageProvider,
		ObjectProvider<OrderMetrics> orderMetricsProvider) {
		this.applicationService = applicationService;
		this.idempotencyStorage = idempotencyStorageProvider.getIfAvailable(() -> new IdempotencyStorage() {
			@Override public boolean exists(String key) { return false; }
			@Override public <T> T getResponse(String key, Class<T> type) { return null; }
			@Override public void saveResponse(String key, Object value) { }
			@Override public void evict(String key) { }
		});
		this.orderMetrics = orderMetricsProvider.getIfAvailable(() -> null);
	}

	private void ensureTenantInMdc(HttpServletRequest request) {
		if (request == null) return;
		String tenantId = request.getHeader("X-Tenant-Id");
		if (tenantId != null && !tenantId.isBlank()) MDC.put("tenantId", tenantId);
	}

	@PostMapping(value = "/submit/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<PreviewOrderVO> orderPreview(@Valid @RequestBody PreviewOrderRequest previewRequest, HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		ConfirmOrderCommand cmd = previewRequest.toCommand(UserIdProvider.getCurrentUserId());
		PreviewOrderVO vo = applicationService.orderPreview(cmd).toVO();
		return Response.ok(vo);
	}

	@PostMapping(value = "/submit/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<CreateOrderVO> submitOrder(@Valid @RequestBody CreateOrderRequest orderRequest,
		HttpServletRequest httpRequest)
		throws Exception {
		// 注入多租户与操作者上下文到 MDC（最小实现：仅用户）
		String userIdForMdc = UserIdProvider.getCurrentUserId();
		if (userIdForMdc != null) MDC.put("actorId", userIdForMdc);
		String extracted = IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		if (extracted == null || extracted.isBlank()) {
			String userId = UserIdProvider.getCurrentUserId();
			String derived = deriveSubmitIdempotencyKey(userId, orderRequest);
			MDC.put(IdempotencyHelper.MDC_IDEMPOTENCY_KEY, derived);
			log.debug("Derived idempotency key for submit: {}", derived);
		}
		SubmitOrderCommand cmd = orderRequest.toCommand(UserIdProvider.getCurrentUserId());
		CreateOrderVO vo = applicationService.submitOrder(cmd).toVO();
		return Response.ok(vo);
	}

	@PostMapping(value = "/cancel/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Object> cancelPreview(@Valid @RequestBody com.github.spud.tinystore.order.interfaces.dto.request.PreviewCancelRequest request,
		HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		Object o = applicationService.cancelPreview(request.getOrderId().toString());
		return Response.ok(o);
	}

	@PostMapping(value = "/cancel/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Object> cancelOrder(@Valid @RequestBody CancelRequest cancelRequest,
		HttpServletRequest httpRequest) {
		String userIdForMdc = UserIdProvider.getCurrentUserId();
		if (userIdForMdc != null) MDC.put("actorId", userIdForMdc);
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		String idempoKey = IdempotencyHelper.getCurrentIdempotencyKey();
		String userId = UserIdProvider.getCurrentUserId();
		// 如果网关未传递幂等键，则基于核心字段派生一个（保持幂等重放能力）
		if (idempoKey == null || idempoKey.isBlank()) {
			idempoKey = deriveCancelIdempotencyKey(userId, cancelRequest);
			MDC.put(IdempotencyHelper.MDC_IDEMPOTENCY_KEY, idempoKey);
			log.debug("Derived idempotency key for cancel: {}", idempoKey);
		}
		// 回放命中直接返回
		try {
			if (idempotencyStorage.exists(idempoKey)) {
				Object cached = idempotencyStorage.getResponse(idempoKey, Object.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(idempoKey); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			}
		} catch (Exception ignored) { }
		if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(idempoKey); } catch (Exception ignored) {} }
		log.info("User cancelling order: {}, user: {}, correlationId: {}", cancelRequest.getOrderId(), userId,
			IdempotencyHelper.getCurrentCorrelationId());
		Object result = applicationService.cancelOrder(cancelRequest.toCommand());
		log.info("User cancel operation completed for order: {}", cancelRequest.getOrderId());
		try { idempotencyStorage.saveResponse(idempoKey, result); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(idempoKey); } } catch (Exception ignored) { }
		return Response.ok(result);
	}

	/**
	 * 确认收货
	 */
	@PostMapping(value = "/confirm-receipt", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> confirmReceipt(@Valid @RequestBody ConfirmReceiptRequest request,
		HttpServletRequest httpRequest) {
		String userIdForMdc = UserIdProvider.getCurrentUserId();
		if (userIdForMdc != null) MDC.put("actorId", userIdForMdc);
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		String idempoKey = IdempotencyHelper.getCurrentIdempotencyKey();
		if (idempoKey != null && !idempoKey.isBlank()) {
			try {
				BasicAckVO cached = idempotencyStorage.getResponse(idempoKey, BasicAckVO.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(idempoKey); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			} catch (Exception ignored) { }
		}
		if (idempoKey != null && !idempoKey.isBlank()) { if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(idempoKey); } catch (Exception ignored) {} } }
		String userId = UserIdProvider.getCurrentUserId();

		// 记录关联ID追踪日志
		log.info("User confirming receipt for order: {}, user: {}, correlationId: {}",
			request.getOrderId(), userId, IdempotencyHelper.getCurrentCorrelationId());

		// TODO: 验证订单属于当前用户
		applicationService.confirmReceipt(request.toCommand(userId));

		log.info("User confirm receipt operation completed for order: {}", request.getOrderId());
		BasicAckVO ack = new BasicAckVO("success", "Receipt confirmed", null);
		if (idempoKey != null && !idempoKey.isBlank()) {
			try { idempotencyStorage.saveResponse(idempoKey, ack); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(idempoKey); } } catch (Exception ignored) { }
		}
		return Response.ok(ack);
	}

	/**
	 * 申请售后（退款/退货退款/换货）
	 */
	@PostMapping(value = "/after-sale/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Map<String, Object>> applyAfterSale(@RequestBody AfterSaleApplyRequest request,
		HttpServletRequest httpRequest) {
		String userIdForMdc = UserIdProvider.getCurrentUserId();
		if (userIdForMdc != null) MDC.put("actorId", userIdForMdc);
		IdempotencyHelper.extractAndSetContext(httpRequest);
		String idempoKey = IdempotencyHelper.getCurrentIdempotencyKey();
		if (idempoKey != null && !idempoKey.isBlank()) {
			try {
				@SuppressWarnings("unchecked")
				Map<String, Object> cached = idempotencyStorage.getResponse(idempoKey, Map.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(idempoKey); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			} catch (Exception ignored) { }
		}
		if (idempoKey != null && !idempoKey.isBlank()) { if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(idempoKey); } catch (Exception ignored) {} } }
		String userId = UserIdProvider.getCurrentUserId();
		// TODO: 验证订单属于当前用户
		String afterSaleId = applicationService.applyAfterSale(request.toCommand(userId));
		Map<String, Object> result = new HashMap<>();
		result.put("status", "success");
		result.put("afterSaleId", afterSaleId);
		if (idempoKey != null && !idempoKey.isBlank()) {
			try { idempotencyStorage.saveResponse(idempoKey, result); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(idempoKey); } } catch (Exception ignored) { }
		}
		return Response.ok(result);
	}

	/**
	 * 订单自动完成预览（可选）
	 */
	@PostMapping(value = "/complete/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Map<String, Object>> completePreview(@RequestBody Map<String, String> request,
		HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		String orderIdStr = request.get("orderId");
		UUID orderId = UUID.fromString(orderIdStr);

		// 记录关联ID追踪日志
		log.debug("User previewing complete for order: {}, correlationId: {}",
			orderId, IdempotencyHelper.getCurrentCorrelationId());

		// TODO: 实现自动完成预览逻辑
		Map<String, Object> result = new HashMap<>();
		result.put("canComplete", true);
		result.put("expectedCompleteAt", System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L); // 7天后
		return Response.ok(result);
	}

	private String deriveSubmitIdempotencyKey(String userId, CreateOrderRequest request) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String raw = userId + "|" + request.getAddressId() + "|" +
				(request.getPlatformCouponId() == null ? "" : request.getPlatformCouponId()) + "|" +
				request.getMerchantSkuGroups().stream()
					.map(g -> g.getMerchantId() + ":" + g.getSkuItems().stream()
						.map(it -> it.getSkuId() + "#" + it.getQuantity())
						.reduce((a,b)->a+","+b).orElse(""))
					.reduce((a,b)->a+";"+b).orElse("");
			byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) sb.append(String.format("%02x", b));
			return "submit:" + userId + ":" + sb;
		} catch (NoSuchAlgorithmException e) {
			return "submit:" + userId + ":fallback";
		}
	}

	private String deriveCancelIdempotencyKey(String userId, CancelRequest request) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String raw = userId + "|" + request.getOrderId() + "|" +
				(request.getReasonCode() == null ? "" : request.getReasonCode()) + "|" +
				(request.getClientRequestId() == null ? "" : request.getClientRequestId()) + "|" +
				(request.getRemark() == null ? "" : request.getRemark());
			byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) sb.append(String.format("%02x", b));
			return "cancel:" + userId + ":" + sb;
		} catch (NoSuchAlgorithmException e) {
			return "cancel:" + userId + ":fallback";
		}
	}
}

package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.infrastructure.vo.Response;
import com.github.spud.tinystore.order.application.service.IdempotencyStorage;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import com.github.spud.tinystore.order.interfaces.dto.request.CancelApproveRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.CancelRejectRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.DeliveredRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.MerchantAcceptRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.ShipOrderRequest;
import com.github.spud.tinystore.order.interfaces.dto.response.BasicAckVO;
import com.github.spud.tinystore.order.interfaces.util.IdempotencyHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家订单控制器 - 处理商家侧订单操作
 *
 * @author Spud
 * @date 2025/9/9
 */
@Slf4j
@RestController
@RequestMapping("/order/merchant")
public class MerchantOrderController {

	private final OrderApplicationService applicationService;
	private final IdempotencyStorage idempotencyStorage;
	private final OrderMetrics orderMetrics;

	public MerchantOrderController(OrderApplicationService applicationService,
		org.springframework.beans.factory.ObjectProvider<IdempotencyStorage> idempotencyStorageProvider,
		org.springframework.beans.factory.ObjectProvider<OrderMetrics> orderMetricsProvider) {
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
		if (tenantId != null && !tenantId.isBlank()) org.slf4j.MDC.put("tenantId", tenantId);
	}

	/**
	 * 商家接单
	 */
	@PostMapping(value = "/order/receive", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> receiveOrder(@Valid @RequestBody MerchantAcceptRequest request,
		HttpServletRequest httpRequest) {
		if (request.getOperatorId() != null) org.slf4j.MDC.put("actorId", request.getOperatorId());
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		String key = IdempotencyHelper.getCurrentIdempotencyKey();
		if (key == null || key.isBlank()) {
			key = "merchant_accept:" + request.getOrderId() + ":" + request.getOperatorId();
			org.slf4j.MDC.put(IdempotencyHelper.MDC_IDEMPOTENCY_KEY, key);
		}
		try {
			if (idempotencyStorage.exists(key)) {
				BasicAckVO cached = idempotencyStorage.getResponse(key, BasicAckVO.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(key); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			}
		} catch (Exception ignored) { }
		if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(key); } catch (Exception ignored) {} }
		log.info("Merchant accepting order: {}, operator: {}, correlationId: {}",
			request.getOrderId(), request.getOperatorId(), IdempotencyHelper.getCurrentCorrelationId());
		applicationService.merchantAccept(request.toCommand());
		log.info("Merchant accept operation completed for order: {}", request.getOrderId());
		BasicAckVO ack = new BasicAckVO("success", "Order accepted", null);
		try { idempotencyStorage.saveResponse(key, ack); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(key); } } catch (Exception ignored) { }
		return Response.ok(ack);
	}

	/**
	 * 商家同意取消
	 */
	@PostMapping(value = "/cancel/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> approveCancel(@Valid @RequestBody CancelApproveRequest request,
		HttpServletRequest httpRequest) {
		if (request.getOperatorId() != null) org.slf4j.MDC.put("actorId", request.getOperatorId());
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		String key = IdempotencyHelper.getCurrentIdempotencyKey();
		if (key == null || key.isBlank()) {
			key = "merchant_cancel_decision:" + request.getOrderId() + ":" + request.getOperatorId() + ":approve";
			org.slf4j.MDC.put(IdempotencyHelper.MDC_IDEMPOTENCY_KEY, key);
		}
		try {
			if (idempotencyStorage.exists(key)) {
				BasicAckVO cached = idempotencyStorage.getResponse(key, BasicAckVO.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(key); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			}
		} catch (Exception ignored) { }
		if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(key); } catch (Exception ignored) {} }
		applicationService.approveCancelRequest(request.toCommand());
		BasicAckVO ack = new BasicAckVO("success", "Cancel approved", null);
		try { idempotencyStorage.saveResponse(key, ack); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(key); } } catch (Exception ignored) { }
		return Response.ok(ack);
	}

	/**
	 * 商家拒绝取消
	 */
	@PostMapping(value = "/cancel/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> rejectCancel(@Valid @RequestBody CancelRejectRequest request,
		HttpServletRequest httpRequest) {
		if (request.getOperatorId() != null) org.slf4j.MDC.put("actorId", request.getOperatorId());
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		String key = IdempotencyHelper.getCurrentIdempotencyKey();
		if (key == null || key.isBlank()) {
			key = "merchant_cancel_decision:" + request.getOrderId() + ":" + request.getOperatorId() + ":reject";
			org.slf4j.MDC.put(IdempotencyHelper.MDC_IDEMPOTENCY_KEY, key);
		}
		try {
			if (idempotencyStorage.exists(key)) {
				BasicAckVO cached = idempotencyStorage.getResponse(key, BasicAckVO.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(key); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			}
		} catch (Exception ignored) { }
		if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(key); } catch (Exception ignored) {} }
		applicationService.rejectCancelRequest(request.toCommand());
		BasicAckVO ack = new BasicAckVO("success", "Cancel rejected", null);
		try { idempotencyStorage.saveResponse(key, ack); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(key); } } catch (Exception ignored) { }
		return Response.ok(ack);
	}

	/**
	 * 商家发货
	 */
	@PostMapping(value = "/ship", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> shipOrder(@Valid @RequestBody ShipOrderRequest request,
		HttpServletRequest httpRequest) {
		if (request.getOperatorId() != null) org.slf4j.MDC.put("actorId", request.getOperatorId());
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		String key = IdempotencyHelper.getCurrentIdempotencyKey();
		if (key == null || key.isBlank()) {
			String pkg = request.getLogistics() != null ? request.getLogistics().getTrackingNo() : "";
			key = "ship:" + request.getOrderId() + ":" + pkg;
			org.slf4j.MDC.put(IdempotencyHelper.MDC_IDEMPOTENCY_KEY, key);
		}
		try {
			if (idempotencyStorage.exists(key)) {
				BasicAckVO cached = idempotencyStorage.getResponse(key, BasicAckVO.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(key); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			}
		} catch (Exception ignored) { }
		if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(key); } catch (Exception ignored) {} }
		log.info("Merchant shipping order: {}, operator: {}, logistics: {}, correlationId: {}",
			request.getOrderId(), request.getOperatorId(),
			request.getLogistics().getCompanyName(), IdempotencyHelper.getCurrentCorrelationId());
		applicationService.shipOrder(request.toCommand());
		log.info("Merchant ship operation completed for order: {}", request.getOrderId());
		BasicAckVO ack = new BasicAckVO("success", "Order shipped", null);
		try { idempotencyStorage.saveResponse(key, ack); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(key); } } catch (Exception ignored) { }
		return Response.ok(ack);
	}

	/**
	 * 商家确认妥投（可选）
	 */
	@PostMapping(value = "/delivery/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> confirmDelivery(@Valid @RequestBody DeliveredRequest request,
		HttpServletRequest httpRequest) {
		// DeliveredRequest 无 operatorId 字段；使用 source 近似标注操作者类型
		if (request.getSource() != null) org.slf4j.MDC.put("actorId", request.getSource());
		IdempotencyHelper.extractAndSetContext(httpRequest);
		ensureTenantInMdc(httpRequest);
		String key = IdempotencyHelper.getCurrentIdempotencyKey();
		if (key == null || key.isBlank()) {
			String pkg = request.getTrackingNo();
			key = "delivered_confirm:" + request.getOrderId() + ":" + pkg;
			org.slf4j.MDC.put(IdempotencyHelper.MDC_IDEMPOTENCY_KEY, key);
		}
		try {
			if (idempotencyStorage.exists(key)) {
				BasicAckVO cached = idempotencyStorage.getResponse(key, BasicAckVO.class);
				if (cached != null) {
					if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyHit(key); } catch (Exception ignored) {} }
					return Response.ok(cached);
				}
			}
		} catch (Exception ignored) { }
		if (orderMetrics != null) { try { orderMetrics.incrementIdempotencyMiss(key); } catch (Exception ignored) {} }
		applicationService.confirmDelivered(request.toCommand());
		BasicAckVO ack = new BasicAckVO("success", "Delivery confirmed", request.getEventId());
		try { idempotencyStorage.saveResponse(key, ack); if (orderMetrics != null) { orderMetrics.incrementIdempotencyHit(key); } } catch (Exception ignored) { }
		return Response.ok(ack);
	}
}

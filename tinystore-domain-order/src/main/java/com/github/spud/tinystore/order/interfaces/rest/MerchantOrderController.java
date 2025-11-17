package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.infrastructure.vo.Response;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
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

	public MerchantOrderController(OrderApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	/**
	 * 商家接单
	 */
	@PostMapping(value = "/order/receive", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> receiveOrder(@Valid @RequestBody MerchantAcceptRequest request,
		HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		// 记录关联ID追踪日志
		log.info("Merchant accepting order: {}, operator: {}, correlationId: {}",
			request.getOrderId(), request.getOperatorId(), IdempotencyHelper.getCurrentCorrelationId());

		// TODO: 商家身份校验
		applicationService.merchantAccept(request.toCommand());

		log.info("Merchant accept operation completed for order: {}", request.getOrderId());
		return Response.ok(new BasicAckVO("success", "Order accepted", null));
	}

	/**
	 * 商家同意取消
	 */
	@PostMapping(value = "/cancel/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> approveCancel(@Valid @RequestBody CancelApproveRequest request,
		HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		// TODO: 商家身份校验
		applicationService.approveCancelRequest(request.toCommand());
		return Response.ok(new BasicAckVO("success", "Cancel approved", null));
	}

	/**
	 * 商家拒绝取消
	 */
	@PostMapping(value = "/cancel/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> rejectCancel(@Valid @RequestBody CancelRejectRequest request,
		HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		// TODO: 商家身份校验
		applicationService.rejectCancelRequest(request.toCommand());
		return Response.ok(new BasicAckVO("success", "Cancel rejected", null));
	}

	/**
	 * 商家发货
	 */
	@PostMapping(value = "/ship", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> shipOrder(@Valid @RequestBody ShipOrderRequest request,
		HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		// 记录关联ID追踪日志
		log.info("Merchant shipping order: {}, operator: {}, logistics: {}, correlationId: {}",
			request.getOrderId(), request.getOperatorId(),
			request.getLogistics().getCompanyName(), IdempotencyHelper.getCurrentCorrelationId());

		// TODO: 商家身份校验
		applicationService.shipOrder(request.toCommand());

		log.info("Merchant ship operation completed for order: {}", request.getOrderId());
		return Response.ok(new BasicAckVO("success", "Order shipped", null));
	}

	/**
	 * 商家确认妥投（可选）
	 */
	@PostMapping(value = "/delivery/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> confirmDelivery(@Valid @RequestBody DeliveredRequest request,
		HttpServletRequest httpRequest) {
		IdempotencyHelper.extractAndSetContext(httpRequest);
		// TODO: 商家身份校验
		applicationService.confirmDelivered(request.toCommand());
		return Response.ok(new BasicAckVO("success", "Delivery confirmed", request.getEventId()));
	}
}

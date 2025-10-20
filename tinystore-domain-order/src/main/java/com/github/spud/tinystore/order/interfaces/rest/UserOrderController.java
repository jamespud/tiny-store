package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.infrastructure.service.UserIdProvider;
import com.github.spud.tinystore.infrastructure.vo.Response;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	public UserOrderController(OrderApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping(value = "/submit/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<PreviewOrderVO> orderPreview(@RequestBody PreviewOrderRequest previewRequest) {
		ConfirmOrderCommand cmd = previewRequest.toCommand(UserIdProvider.getCurrentUserId());
		PreviewOrderVO vo = applicationService.orderPreview(cmd).toVO();
		return Response.ok(vo);
	}

	@PostMapping(value = "/submit/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<CreateOrderVO> submitOrder(@RequestBody CreateOrderRequest orderRequest)
		throws Exception {
		SubmitOrderCommand cmd = orderRequest.toCommand(UserIdProvider.getCurrentUserId());
		CreateOrderVO vo = applicationService.submitOrder(cmd).toVO();
		return Response.ok(vo);
	}

	@PostMapping(value = "/cancel/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Object> cancelPreview(String orderId) {
		Object o = applicationService.cancelPreview(orderId);
		return Response.ok(o);
	}

	@PostMapping(value = "/cancel/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Object> cancelOrder(CancelRequest cancelRequest) {
		Object o = applicationService.cancelOrder(cancelRequest.toCommand());
		return Response.ok(o);
	}

	/**
	 * 确认收货
	 */
	@PostMapping(value = "/confirm-receipt", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<BasicAckVO> confirmReceipt(@RequestBody ConfirmReceiptRequest request) {
		String userId = UserIdProvider.getCurrentUserId();

		// 记录关联ID追踪日志
		log.info("User confirming receipt for order: {}, user: {}, correlationId: {}",
			request.getOrderId(), userId, IdempotencyHelper.getCurrentCorrelationId());

		// TODO: 验证订单属于当前用户
		applicationService.confirmReceipt(request.toCommand(userId));

		log.info("User confirm receipt operation completed for order: {}", request.getOrderId());
		return Response.ok(new BasicAckVO("success", "Receipt confirmed", null));
	}

	/**
	 * 申请售后（退款/退货退款/换货）
	 */
	@PostMapping(value = "/after-sale/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Map<String, Object>> applyAfterSale(@RequestBody AfterSaleApplyRequest request) {
		String userId = UserIdProvider.getCurrentUserId();
		// TODO: 验证订单属于当前用户
		String afterSaleId = applicationService.applyAfterSale(request.toCommand(userId));
		Map<String, Object> result = new HashMap<>();
		result.put("status", "success");
		result.put("afterSaleId", afterSaleId);
		return Response.ok(result);
	}

	/**
	 * 订单自动完成预览（可选）
	 */
	@PostMapping(value = "/complete/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Response<Map<String, Object>> completePreview(@RequestBody Map<String, String> request) {
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
}

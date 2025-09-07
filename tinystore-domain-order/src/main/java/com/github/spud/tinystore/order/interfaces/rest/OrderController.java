package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.infrastructure.service.UserIdProvider;
import com.github.spud.tinystore.infrastructure.vo.CommonResponse;
import com.github.spud.tinystore.order.application.command.CreateOrderCommand;
import com.github.spud.tinystore.order.application.command.PreviewOrderCommand;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import com.github.spud.tinystore.order.interfaces.dto.request.CreateOrderRequest;
import com.github.spud.tinystore.order.interfaces.dto.request.PreviewOrderRequest;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderVO;
import com.github.spud.tinystore.order.interfaces.dto.response.PreviewOrderVO;
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
@RestController
@RequestMapping("/order/user")
public class OrderController {

	private final OrderApplicationService applicationService;

	public OrderController(OrderApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping(value = "/submit/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CommonResponse<PreviewOrderVO> orderPreview(
		@RequestBody PreviewOrderRequest previewRequest) {
		PreviewOrderCommand cmd = previewRequest.toCommand(UserIdProvider.getCurrentUserId());
		PreviewOrderVO vo = applicationService.orderPreview(cmd).toVO();
		return CommonResponse.success(vo);
	}

	@PostMapping(value = "/submit/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CommonResponse<CreateOrderVO> submitOrder(@RequestBody CreateOrderRequest orderRequest) {
		CreateOrderCommand cmd = orderRequest.toCommand(UserIdProvider.getCurrentUserId());
		CreateOrderVO vo = applicationService.submitOrder(cmd).toVO();
		return CommonResponse.success(vo);
	}

	@PostMapping(value = "/cancel/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CommonResponse<Object> cancelPreview() {
		return CommonResponse.success("");
	}

	@PostMapping(value = "/cancel/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CommonResponse<Object> cancelOrder() {
		return CommonResponse.success("");
	}
}

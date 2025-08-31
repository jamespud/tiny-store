package com.github.spud.tinystore.order.controller.user;

import com.github.spud.tinystore.infrastrucutre.domain.payment.PaymentIntent;
import com.github.spud.tinystore.infrastrucutre.vo.CommonResponse;
import com.github.spud.tinystore.order.api.dto.CancelRequest;
import com.github.spud.tinystore.order.api.dto.Settlement;
import com.github.spud.tinystore.order.api.dto.SettlementRequest;
import com.github.spud.tinystore.order.application.OrderApplicationService;
import com.github.spud.tinystore.order.domain.vo.CancelOrderVo;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单控制器(消费者)
 * 1. 创建订单
 * 2. 取消订单
 * 3. 确认收货
 * 4. 订单列表查询 (TODO)
 * 5. 订单详情查询 (TODO)
 * 6. 申请退款/售后 (TODO)
 *
 * @author Spud
 * @date 2025/8/12
 */
@RestController
public class OrderController {

	@Deprecated
	@Autowired
	private OrderApplicationService orderApplicationService;

	/**
	 * 订单预览
	 *
	 * @param request 结算请求
	 * @return 订单预览结果
	 */
	@Consumes("application/json")
	@PostMapping("/create/confirm")
	public CommonResponse<SettlementPreviewVO> confirmOrderLegacy(
		@Valid @RequestBody SettlementRequest request) {
		SettlementPreviewVO previewVO = orderApplicationService.preCheckSettlement(request);
		return CommonResponse.success(previewVO);
	}

	/**
	 * 下单
	 *
	 * @param settlement 提交订单命令
	 * @return 下单结果
	 */
	@Consumes("application/json")
	@PostMapping("/create/submit")
	public CommonResponse<PaymentIntent> createOrderFromCart(
		@Valid @RequestBody Settlement settlement) {
		PaymentIntent payment = orderApplicationService.executeBySettlement(settlement);
		return CommonResponse.success(payment);
	}

	/**
	 * 取消订单预览
	 *
	 */
	@Consumes("application/json")
	@PostMapping("/cancel/confirm")
	public CommonResponse<String> cancelOrderConfirm() {
		String key = orderApplicationService.preCheckCancel();
		return CommonResponse.success(key);
	}

	/**
	 * 取消订单
	 *
	 * @param request 取消订单请求
	 * @return 取消订单结果
	 */
	@Consumes("application/json")
	@PostMapping("/cancel/submit")
	public CommonResponse<CancelOrderVo> cancelOrder(@Valid @RequestBody CancelRequest request) {
		CancelOrderVo response = orderApplicationService.cancelOrder(request);
		// 设置服务器时间
		response.setServerTime(Instant.now());
		return CommonResponse.success(response);
	}
	
	@Consumes("application/json")
	@PostMapping("/receive")
	public CommonResponse<String> confirmReceive() {
		// TODO: 确认收货
		return CommonResponse.success("");
	}

}
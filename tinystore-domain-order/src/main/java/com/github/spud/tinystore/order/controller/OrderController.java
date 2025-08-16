package com.github.spud.tinystore.order.controller;

import com.github.spud.tinystore.infrastrucutre.domain.payment.PaymentIntent;
import com.github.spud.tinystore.order.api.dto.Settlement;
import com.github.spud.tinystore.order.api.dto.SettlementRequest;
import com.github.spud.tinystore.order.application.OrderApplicationService;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单控制器
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
	@PostMapping("/confirm")
	public ResponseEntity<SettlementPreviewVO> confirmOrderLegacy(
		@Valid @RequestBody SettlementRequest request) {
		SettlementPreviewVO previewVO = orderApplicationService.preCheckSettlement(request);
		return ResponseEntity.ok(previewVO);
	}

	/**
	 * 下单
	 *
	 * @param settlement 提交订单命令
	 * @return 下单结果
	 */
	@Consumes("application/json")
	@PostMapping("/submit")
	public ResponseEntity<PaymentIntent> createOrderFromCart(
		@Valid @RequestBody Settlement settlement) {
		PaymentIntent payment = orderApplicationService.executeBySettlement(settlement);
		return ResponseEntity.ok(payment);
	}

}
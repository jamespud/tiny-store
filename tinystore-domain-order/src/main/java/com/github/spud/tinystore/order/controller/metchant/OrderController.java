package com.github.spud.tinystore.order.controller.metchant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单控制器(商家)
 * 1. 接单(TODO)
 * 2. 拒单(TODO)
 * 3. 发货(TODO)
 * 4. 同意退款/售后(TODO)
 * 5. 拒绝退款/售后(TODO)
 * 6. 签收退货/售后(TODO)
 * 7. 订单列表查询 (TODO)
 * 8. 订单详情查询 (TODO)
 *
 * @author Spud
 * @date 2025/8/30
 */
@RestController
@RequestMapping("/orders/refunds")
public class OrderController {


	@PostMapping("/cancel/approve")
	@PreAuthorize("hasRole('MERCHANT')")
	public void approveRefund() {
		// TODO: 实现商家同意退款接口	
	}

}

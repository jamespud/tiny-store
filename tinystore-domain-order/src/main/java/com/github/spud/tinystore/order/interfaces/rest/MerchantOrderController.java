package com.github.spud.tinystore.order.interfaces.rest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Spud
 * @date 2025/9/9
 */
@RestController("/order/merchant")
public class MerchantOrderController {
	
	@PostMapping("/order/receive")
	public void receiveOrder() {
		
	}
	
	@PostMapping("/cancel/approve")
	public void approveCancel() {
		
	}
	
	@PostMapping("/cancel/reject")
	public void rejectCancel() {
		
	}
	
	@PostMapping("/ship")
	public void shipOrder() {
		
	}
	
	
}

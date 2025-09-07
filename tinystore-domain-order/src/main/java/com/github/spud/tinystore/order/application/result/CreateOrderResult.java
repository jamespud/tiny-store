package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.interfaces.dto.CouponSnapshot;
import com.github.spud.tinystore.order.interfaces.dto.OrderSummary;
import com.github.spud.tinystore.order.interfaces.dto.ProductSnapshot;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderVO;
import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class CreateOrderResult {

	private String orderId;
	private List<ProductSnapshot> lines;
	private List<CouponSnapshot> coupons;
	private OrderSummary summary;
	
	public CreateOrderVO toVO() {
		CreateOrderVO vo = new CreateOrderVO();
		return vo;
	}

}

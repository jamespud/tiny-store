package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.application.result.PreviewOrderResult.OrderSummary;
import com.github.spud.tinystore.order.application.result.PreviewOrderResult.ShopProductSnapshot;
import com.github.spud.tinystore.order.domain.model.OrderAggregate;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderVO;
import lombok.Data;

import java.util.List;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class SubmitOrderResult {

	private String orderId;

	private List<ShopProductSnapshot> lines;

	private OrderSummary summary;

	public static SubmitOrderResult from(OrderAggregate aggregate) {
		return null;
	}

	public CreateOrderVO toVO() {
		CreateOrderVO vo = new CreateOrderVO();
		return vo;
	}

}

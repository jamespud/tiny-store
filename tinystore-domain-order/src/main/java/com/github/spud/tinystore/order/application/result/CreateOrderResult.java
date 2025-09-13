package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.application.result.PreviewOrderResult.OrderSummary;
import com.github.spud.tinystore.order.application.result.PreviewOrderResult.ShopProductSnapshot;
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

	private List<ShopProductSnapshot> lines;

	private OrderSummary summary;

	public CreateOrderVO toVO() {
		CreateOrderVO vo = new CreateOrderVO();
		return vo;
	}

}

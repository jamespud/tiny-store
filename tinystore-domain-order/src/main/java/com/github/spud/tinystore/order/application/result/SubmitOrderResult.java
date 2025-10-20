package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.application.result.ConfirmOrderResult.OrderSummary;
import com.github.spud.tinystore.order.application.result.ConfirmOrderResult.ShopProductSnapshot;
import com.github.spud.tinystore.order.domain.model.vo.OrderNo;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderVO;
import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class SubmitOrderResult {

	/**
	 * 订单编号
	 */
	private OrderNo mainOrderNo;

	private List<ShopProductSnapshot> lines;

	private OrderSummary summary;

	public CreateOrderVO toVO() {
		CreateOrderVO vo = new CreateOrderVO();
		return vo;
	}

}

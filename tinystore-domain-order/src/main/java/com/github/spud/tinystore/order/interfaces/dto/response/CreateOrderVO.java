package com.github.spud.tinystore.order.interfaces.dto.response;

import com.github.spud.tinystore.order.application.result.ConfirmOrderResult;
import com.github.spud.tinystore.order.application.result.ConfirmOrderResult.ShopProductSnapshot;
import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/4
 */
@Data
public class CreateOrderVO {

	private List<ShopProductSnapshot> lines;
	private ConfirmOrderResult.OrderSummary summary;
	private String idempotencyKey;
}

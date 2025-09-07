package com.github.spud.tinystore.order.interfaces.dto.response;

import com.github.spud.tinystore.order.interfaces.dto.OrderSummary;
import com.github.spud.tinystore.order.interfaces.dto.ProductSnapshot;
import java.util.List;

/**
 * @author Spud
 * @date 2025/9/4
 */
public class CreateOrderVO {

	private List<ProductSnapshot> lines;
	private OrderSummary summary;
	private String idempotencyKey;
}

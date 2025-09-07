package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.interfaces.dto.CouponSnapshot;
import com.github.spud.tinystore.order.interfaces.dto.OrderSummary;
import com.github.spud.tinystore.order.interfaces.dto.ProductSnapshot;
import com.github.spud.tinystore.order.interfaces.dto.response.PreviewOrderVO;
import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class PreviewOrderResult {

	private List<ProductSnapshot> lines;
	private List<CouponSnapshot> coupons;
	private OrderSummary summary;
	private String idempotencyKey;
	private long expireAt;
	
	public PreviewOrderVO toVO() {
		PreviewOrderVO vo = new PreviewOrderVO("");
		return vo;
	}
}

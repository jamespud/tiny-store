package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.interfaces.dto.response.PreviewOrderVO;
import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class PreviewOrderResult {

	private List<ShopProductSnapshot> lines;

	private OrderSummary summary;

	// ttl in seconds
	private long expireAt;

	public PreviewOrderVO toVO() {
		return new PreviewOrderVO("");
	}

	public record ProductSnapshot(String spuId, String skuId, Money unitPrice, Money payable,
	                              int quantity) {

	}

	public record ShopProductSnapshot(ShopSnapshot shop, List<ProductSnapshot> products,
	                                  Money price) {

	}

	public record ShopSnapshot(String shopId, String shopName) {

	}

	public record CouponSnapshot(String couponId, String description,
	                             Money amount, String strategy) {

	}

	public record OrderSummary(Money total, List<ChargeItemSnapshot> charges, List<DiscountSnapshot> discounts,
	                           List<CouponSnapshot> coupons,
	                           Money payable) {

	}

	public record DiscountSnapshot(String description, Money amount) {

	}
	
	public record ChargeItemSnapshot(String type, String description, Money amount) {

	}

}

package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.domain.model.*;
import com.github.spud.tinystore.order.interfaces.dto.response.PreviewOrderVO;
import lombok.Data;

import java.util.List;

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

	private PreviewOrderResult() {
	}

	public PreviewOrderResult(List<ShopProductSnapshot> lines, OrderSummary summary, long expireAt) {
		this.lines = lines;
		this.summary = summary;
		this.expireAt = expireAt;
	}

	public static PreviewOrderResult fromOrder(OrderAggregate order) {
		return null;
	}

	public PreviewOrderVO toVO() {
		return new PreviewOrderVO(lines, summary, expireAt);
	}

	public record ShopProductSnapshot(String shopId, List<ProductSnapshot> products,
	                                  Money total) {

		public static ShopProductSnapshot fromSuborder(SubOrder subOrder) {
			return null;
		}

	}

	/**
	 *
	 * @param skuId
	 * @param unitPrice 单价
	 * @param payable   应付金额
	 * @param quantity  数量
	 */
	public record ProductSnapshot(String skuId, Money unitPrice, Money payable,
	                              int quantity) {

	}

	public record OrderSummary(Money total, Money payable,
	                           List<ChargeItemSnapshot> charges,
	                           List<DiscountSnapshot> discounts,
	                           List<CouponSnapshot> coupons) {

	}

	public record CouponSnapshot(String couponId, String description,
	                             Money amount) {
		public static CouponSnapshot fromCoupon(Coupon coupon) {
			return new CouponSnapshot(coupon.couponId(), coupon.description(), coupon.amount());
		}
	}

	public record DiscountSnapshot(String description, Money amount) {
		public static DiscountSnapshot fromDiscount(Discount discount) {
			return new DiscountSnapshot(discount.description(), discount.amount());
		}
	}

	public record ChargeItemSnapshot(String type, String description, Money amount) {
		public static ChargeItemSnapshot fromChargeItem(ChargeItem chargeItem) {
			return new ChargeItemSnapshot(chargeItem.type().toString(), chargeItem.description(), chargeItem.amount());
		}
	}

}

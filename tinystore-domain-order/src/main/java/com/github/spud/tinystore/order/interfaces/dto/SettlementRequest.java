package com.github.spud.tinystore.order.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.List;

/**
 * @author Spud
 * @date 2025/8/16
 */
public class SettlementRequest {

	@Size(min = 1, message = "结算单中缺少商品清单")
	private Collection<Item> items;

	@NotNull(message = "结算单中缺少配送信息")
	private Purchase purchase;

	private List<Coupon> coupons;

	public Settlement toSettlement() {
		Settlement settlement = new Settlement();
		settlement.setItems(items);
		settlement.setPurchase(purchase);
		settlement.setCoupons(coupons);
		return settlement;
	}
}

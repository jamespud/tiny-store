package com.github.spud.tinystore.promotion.domain.model;

/**
 * 券归属范围类型
 * 区分平台券与店铺券的范围，独立于 coupon_type（优惠计算类型）
 */
public enum CouponScopeType {
	/**
	 * 平台券 - 跨店铺通用
	 */
	PLATFORM,
	
	/**
	 * 店铺券 - 仅特定店铺可用
	 */
	STORE
}

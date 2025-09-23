package com.github.spud.tinystore.order.domain.model;

/**
 * 优惠券分摊
 *
 * @author Spud
 * @date 2025/9/13
 */
public record CouponAllocation(String couponId, String orderId, Money amount) {

}

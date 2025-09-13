package com.github.spud.tinystore.order.domain.model;

/**
 * @author Spud
 * @date 2025/9/13
 */
public record CouponAllocation(String couponId, String orderId, Money amount) {

}

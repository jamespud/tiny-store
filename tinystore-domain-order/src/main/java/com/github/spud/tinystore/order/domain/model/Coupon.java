package com.github.spud.tinystore.order.domain.model;

/**
 * 优惠券
 *
 * @param couponId       优惠券ID
 * @param description    描述
 * @param discountAmount 优惠金额
 */
public record Coupon(String couponId, String description, Money discountAmount) {

}
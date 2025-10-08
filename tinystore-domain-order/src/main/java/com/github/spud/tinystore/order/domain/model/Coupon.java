package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.CouponType;

/**
 * 优惠券
 *
 * @param couponId    优惠券ID
 * @param description 描述
 * @param amount      优惠金额
 */
public record Coupon(String couponId, CouponType type, String description, Money threshold, Money amount,
                     String startTime, String endTime, Boolean available) {

}
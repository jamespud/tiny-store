package com.github.spud.tinystore.order.interfaces.dto;

/**
 * @author Spud
 * @date 2025/9/4
 */
public record CouponSnapshot(String couponId, String description, long discount,
                             String strategy) {

}
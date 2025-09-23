package com.github.spud.tinystore.order.domain.model;

/**
 * 促销分摊
 *
 * @author Spud
 * @date 2025/9/6
 */
public record DiscountAllocation(String source, String target, Money amount) {

}
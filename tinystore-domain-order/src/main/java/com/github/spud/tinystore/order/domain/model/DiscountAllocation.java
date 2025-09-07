package com.github.spud.tinystore.order.domain.model;

/**
 * @author Spud
 * @date 2025/9/6
 */
public record DiscountAllocation(String source, Money amount, String targetLineId) {

}
package com.github.spud.tinystore.order.domain.model;

/**
 * @author Spud
 * @date 2025/9/6
 */
public record PricingSummary(Money itemsTotal, Money discountTotal, Money chargesTotal,
                             Money taxTotal,
                             Money grandTotal, Money payable) {

}
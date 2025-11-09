package com.github.spud.tinystore.order.domain.model;

import java.util.List;

/**
 * @author Spud
 * @date 2025/9/6
 */
public record PricingSummary(Money total, Money discountTotal, List<ChargeItem> charges,
														 Money payable) {

}
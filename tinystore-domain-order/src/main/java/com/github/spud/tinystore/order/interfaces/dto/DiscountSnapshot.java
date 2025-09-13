package com.github.spud.tinystore.order.interfaces.dto;

import com.github.spud.tinystore.order.domain.model.Money;

/**
 * @author Spud
 * @date 2025/9/4
 */
public record DiscountSnapshot(String description, Money amount) {

}

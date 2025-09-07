package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.ChargeType;

/**
 * @author Spud
 * @date 2025/9/6
 */
public record ChargeItem(ChargeType type, Money amount) {

}
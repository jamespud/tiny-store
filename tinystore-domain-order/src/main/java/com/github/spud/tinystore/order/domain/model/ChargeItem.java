package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.ChargeType;

/**
 * 运费、手续费等费用项
 */
public record ChargeItem(ChargeType type, Money amount, String description) {

}
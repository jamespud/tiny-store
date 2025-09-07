package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.model.Order.BuyerType;

/**
 * @author Spud
 * @date 2025/9/6
 */
public record Buyer(String userId, BuyerType buyerType, Integer level) {

}
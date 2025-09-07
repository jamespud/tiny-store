package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.PaymentType;

/**
 * @author Spud
 * @date 2025/9/2
 */
public record Payment(String paymentId, String orderId, Money amount, PaymentType type,
                      String status) {

}

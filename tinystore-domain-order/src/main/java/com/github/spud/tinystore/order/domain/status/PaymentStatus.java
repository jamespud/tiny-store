package com.github.spud.tinystore.order.domain.status;

/**
 * @author Spud
 * @date 2025/9/6
 */
public enum PaymentStatus {
	UNPAID, INTENT_CREATED, AUTHORIZED, PAID, FAILED, EXPIRED, REFUND_PENDING, PARTIAL_REFUNDED, REFUNDED
}
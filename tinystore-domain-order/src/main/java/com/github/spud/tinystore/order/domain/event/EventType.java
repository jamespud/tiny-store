package com.github.spud.tinystore.order.domain.event;

/**
 * @author Spud
 * @date 2025/9/3
 */
public enum EventType {

	ORDER_CREATED,

	ORDER_CANCELLED,

	ORDER_PAID,

	ORDER_SHIPPED,

	ORDER_COMPLETED,

	AFTERSALE_REQUESTED,

	AFTERSALE_APPROVED,

	AFTERSALE_REJECTED,

	AFTERSALE_COMPLETED
}

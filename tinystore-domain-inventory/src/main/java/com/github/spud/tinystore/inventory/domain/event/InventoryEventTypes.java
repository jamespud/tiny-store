package com.github.spud.tinystore.inventory.domain.event;

/**
 * 事件类型常量
 */
public final class InventoryEventTypes {

	private InventoryEventTypes() {
	}

	public static final String RESERVATION_CONFIRMED = "reservation.confirmed";
	public static final String RESERVATION_RELEASED = "reservation.released";
	public static final String RESERVATION_EXPIRED = "reservation.expired";
	public static final String STOCK_ADJUSTED = "stock.adjusted";
}


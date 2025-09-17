package com.github.spud.tinystore.inventory.domain.event;

/**
 * 预留过期事件
 */
public final class ReservationExpiredEvent extends AbstractDomainEvent {

	private final String reservationId;
	private final long quantity;

	public ReservationExpiredEvent(String shopId, String skuId, String reservationId, long quantity) {
		super(shopId, skuId);
		this.reservationId = reservationId;
		this.quantity = quantity;
	}

	public String getReservationId() {
		return reservationId;
	}

	public long getQuantity() {
		return quantity;
	}

	@Override
	public String getType() {
		return InventoryEventTypes.RESERVATION_EXPIRED;
	}
}


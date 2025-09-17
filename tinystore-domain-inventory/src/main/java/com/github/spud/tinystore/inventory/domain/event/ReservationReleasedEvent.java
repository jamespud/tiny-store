package com.github.spud.tinystore.inventory.domain.event;

/**
 * 预留释放事件
 */
public final class ReservationReleasedEvent extends AbstractDomainEvent {

	private final String reservationId;
	private final long quantity;
	private final String reason;

	public ReservationReleasedEvent(String shopId, String skuId, String reservationId, long quantity,
		String reason) {
		super(shopId, skuId);
		this.reservationId = reservationId;
		this.quantity = quantity;
		this.reason = reason;
	}

	public String getReservationId() {
		return reservationId;
	}

	public long getQuantity() {
		return quantity;
	}

	public String getReason() {
		return reason;
	}

	@Override
	public String getType() {
		return InventoryEventTypes.RESERVATION_RELEASED;
	}
}


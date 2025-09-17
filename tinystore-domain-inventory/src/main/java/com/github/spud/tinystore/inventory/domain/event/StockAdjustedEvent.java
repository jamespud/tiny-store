package com.github.spud.tinystore.inventory.domain.event;

/**
 * 库存调整事件
 */
public final class StockAdjustedEvent extends AbstractDomainEvent {

	private final long delta;
	private final String reason;

	public StockAdjustedEvent(String shopId, String skuId, long delta, String reason) {
		super(shopId, skuId);
		this.delta = delta;
		this.reason = reason;
	}

	public long getDelta() {
		return delta;
	}

	public String getReason() {
		return reason;
	}

	@Override
	public String getType() {
		return InventoryEventTypes.STOCK_ADJUSTED;
	}
}


package com.github.spud.tinystore.inventory.interfaces.dto;

/**
 * 不可变库存快照 DTO （读模型）
 */
public final class StockSnapshotDTO {

	private final String shopId;
	private final String skuId;
	private final long totalQuantity;
	private final long reservedQuantity;
	private final long version;

	public StockSnapshotDTO(String shopId, String skuId, long totalQuantity, long reservedQuantity,
		long version) {
		this.shopId = shopId;
		this.skuId = skuId;
		this.totalQuantity = totalQuantity;
		this.reservedQuantity = reservedQuantity;
		this.version = version;
	}

	public String getShopId() {
		return shopId;
	}

	public String getSkuId() {
		return skuId;
	}

	public long getTotalQuantity() {
		return totalQuantity;
	}

	public long getReservedQuantity() {
		return reservedQuantity;
	}

	public long getVersion() {
		return version;
	}

	public long getAvailable() {
		return totalQuantity - reservedQuantity;
	}
}


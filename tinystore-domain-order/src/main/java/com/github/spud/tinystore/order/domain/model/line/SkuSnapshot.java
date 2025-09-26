package com.github.spud.tinystore.order.domain.model.line;

import com.github.spud.tinystore.order.domain.model.Money;

import java.time.Instant;

/**
 * SKU快照值对象
 * <p>
 * 记录下单时刻的商品信息，用于价格冻结与历史追溯
 * 避免商品信息变更影响已下单的订单
 *
 * @param skuId        SKU标识符
 * @param title        商品标题（下单时快照）
 * @param specJson     商品规格JSON（下单时快照）
 * @param unitPrice    单价（下单时快照，默认CNY）
 * @param currency     币种（当前固定CNY）
 * @param snapshotTime 快照时间
 */
public record SkuSnapshot(
	String skuId,
	String title,
	String specJson,
	Money unitPrice,
	String currency,
	Instant snapshotTime
) {

	public SkuSnapshot {
		if (skuId == null || skuId.trim().isEmpty()) {
			throw new IllegalArgumentException("SKU ID cannot be null or empty");
		}
		if (title == null || title.trim().isEmpty()) {
			throw new IllegalArgumentException("Title cannot be null or empty");
		}
		if (unitPrice == null) {
			throw new IllegalArgumentException("Unit price cannot be null");
		}
		if (currency == null || !currency.equals("CNY")) {
			throw new IllegalArgumentException("Currency must be CNY for now");
		}
		if (snapshotTime == null) {
			throw new IllegalArgumentException("Snapshot time cannot be null");
		}
	}

	/**
	 * 创建SKU快照的工厂方法
	 *
	 * @param skuId            SKU标识
	 * @param title            商品标题
	 * @param specJson         规格JSON
	 * @param unitPriceInCents 单价（分）
	 * @return SKU快照实例
	 */
	public static SkuSnapshot create(String skuId, String title, String specJson, long unitPriceInCents) {
		return new SkuSnapshot(
			skuId,
			title,
			specJson,
			Money.ofCents(unitPriceInCents, "CNY"),
			"CNY",
			Instant.now()
		);
	}
}
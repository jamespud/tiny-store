package com.github.spud.tinystore.order.interfaces.dto;

import com.github.spud.tinystore.order.interfaces.vo.SettlementPreviewVO.Snapshot;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/8/16
 */
@Data
public class Product {

	private long spuId; // 商品ID
	private long skuId; // SKU ID
	private String title; // 商品标题
	private String spec; // 商品规格
	private long unitPrice; // 单价（分）
	private int quantity; // 数量
	private long promoPrice; // 促销价（分）
	private long lineTotal; // 小计（分）

	public Snapshot toLineSnapshot() {
		return new Snapshot(spuId, skuId, title, spec, unitPrice, quantity, lineTotal);
	}
}

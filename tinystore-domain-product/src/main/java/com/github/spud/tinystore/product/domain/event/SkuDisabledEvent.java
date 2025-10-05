package com.github.spud.tinystore.product.domain.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SKU禁用事件 当SKU被禁用时触发，通知下游系统（库存、搜索等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuDisabledEvent {

	private String skuId;
	private String productId;
	private Instant occurredAt;

	public SkuDisabledEvent(String skuId, String productId) {
		this.skuId = skuId;
		this.productId = productId;
		this.occurredAt = Instant.now();
	}
}

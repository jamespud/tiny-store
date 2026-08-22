package com.github.spud.tinystore.infrastructure.rpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 商品 SKU 权威价格 DTO（共享 RPC）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSkuPrice {

	private String skuId;

	/** 是否可售（AVAILABLE）。 */
	private boolean available;

	/** 目录标价（分）。 */
	private long unitPrice;

	/** 活动推广价（分），0 表示无推广价。 */
	private long promotePrice;

	private double weight;
}

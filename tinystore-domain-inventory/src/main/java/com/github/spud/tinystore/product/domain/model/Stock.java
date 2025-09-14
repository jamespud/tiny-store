package com.github.spud.tinystore.product.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存聚合根（简化，仅字段与访问器）
 */
@Data
@NoArgsConstructor
public class Stock {
	private String shopId;
	private String skuId;
	private Integer totalQuantity;
	private Integer reservedQuantity;
	private Integer version;
}

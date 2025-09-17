package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

/**
 * 旧DTO（保留）
 */
@Data
public class DeductStockDTO {

	private String shopId;
	private String skuId;
	private Integer quantity;
}

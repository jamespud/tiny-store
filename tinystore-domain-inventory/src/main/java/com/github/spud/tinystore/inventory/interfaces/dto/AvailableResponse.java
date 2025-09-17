package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

@Data
public class AvailableResponse {

	private String shopId;
	private String skuId;
	private Integer total;
	private Integer reserved;
	private Integer available;
	private Integer version;
}


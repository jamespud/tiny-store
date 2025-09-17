package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.Data;

@Data
public class ReserveRequest {

	private String shopId;
	private String skuId;
	private Integer quantity;
	private Integer expireSeconds;
	private String operationId;
}

package com.github.spud.tinystore.product.interfaces.dto;

import lombok.Data;

@Data
public class ReserveRequest {
	private String shopId;
	private String skuId;
	private Integer quantity;
	private Integer expireSeconds;
	private String operationId;
}


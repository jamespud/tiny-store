package com.github.spud.tinystore.product.interfaces.dto;

import lombok.Data;

@Data
public class AdjustRequest {
	private String shopId;
	private String skuId;
	private Integer deltaTotal;
	private String reason;
	private String correlationId;
}


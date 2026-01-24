package com.github.spud.tinystore.product.interfaces.internal.dto;

import lombok.Data;

@Data
public class InternalSkuDTO {

	private String skuId;
	private String merchantId;
	private boolean available;
	private long promotePrice;
	private long unitPrice;
	private double weight;
	private String skuName;
	private String specJson;
}


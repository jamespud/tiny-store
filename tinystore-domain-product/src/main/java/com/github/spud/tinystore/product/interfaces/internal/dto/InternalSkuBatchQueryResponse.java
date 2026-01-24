package com.github.spud.tinystore.product.interfaces.internal.dto;

import java.util.Map;
import lombok.Data;

@Data
public class InternalSkuBatchQueryResponse {

	private Map<String, InternalSkuDTO> skuMap;
}


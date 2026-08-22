package com.github.spud.tinystore.infrastructure.rpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.Data;

/**
 * 批量 SKU 权威价格响应（共享 RPC）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSkuPriceResponse {

	private Map<String, ProductSkuPrice> skuMap;
}

package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.infrastructure.acl.ProductClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/5
 */
@Service
public class ProductService {

	private ProductClient productClient;

	public List<Product> getProductsByIds(List<String> productIds) {
		productClient.getProductsByIds(productIds);
		return List.of();
	}

	public SkuBatchQueryResponse batchGetSkuInfo(Set<String> productIds) {
		return null;
	}

	@Data

	public static class SkuDTO {

		private String skuId;
		private String skuName;
		private String mainImageUrl;
		private String secJson;
		private String merchantId;
		// 是否上架
		private boolean available;
		// 促销价(CNY分)
		private long promotePrice;
		// 单价(CNY分)
		private long unitPrice;
		// 重量(kg)
		private double weight;

		public SkuDTO(String s, Integer quantity, long unitPrice) {

		}
	}

	@Data
	public static class SkuBatchQueryResponse {

		/**
		 * SKU信息映射，key为skuId
		 */
		private Map<String, SkuDTO> skuMap;
	}
}
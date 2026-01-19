package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.infrastructure.acl.ProductClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import com.github.spud.tinystore.order.infrastructure.tenant.TenantContext;
import java.util.HashMap;

/**
 * @author Spud
 * @date 2025/9/5
 */
@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductClient productClient;

	public List<Product> getProductsByIds(List<String> productIds) {
		productClient.getProductsByIds(productIds);
		return List.of();
	}

	public SkuBatchQueryResponse batchGetSkuInfo(Set<String> productIds) {
		SkuBatchQueryResponse resp = new SkuBatchQueryResponse();
		Map<String, SkuDTO> resultMap = new HashMap<>();
		resp.setSkuMap(resultMap);
		if (productIds == null || productIds.isEmpty()) {
			return resp;
		}
		ProductClient.InternalSkuBatchQueryResponse raw;
		try {
			raw = productClient.batchGetSkuInfo(
				TenantContext.getTenantId(),
				TenantContext.getUserId(),
				productIds,
				MDC.get("traceId")
			);
		} catch (Exception e) {
			return resp;
		}
		if (raw == null || raw.getSkuMap() == null || raw.getSkuMap().isEmpty()) {
			return resp;
		}
		for (Map.Entry<String, ProductClient.InternalSkuDTO> en : raw.getSkuMap().entrySet()) {
			if (en.getKey() == null || en.getValue() == null) {
				continue;
			}
			ProductClient.InternalSkuDTO v = en.getValue();
			SkuDTO dto = new SkuDTO();
			dto.setSkuId(v.getSkuId());
			dto.setMerchantId(v.getMerchantId());
			dto.setAvailable(v.isAvailable());
			dto.setUnitPrice(v.getUnitPrice());
			dto.setPromotePrice(v.getPromotePrice());
			dto.setWeight(v.getWeight());
			dto.setSkuName(v.getSkuName());
			dto.setSecJson(v.getSpecJson());
			resultMap.put(en.getKey(), dto);
		}
		return resp;
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

		public SkuDTO() {
		}

		public SkuDTO(String s, Integer quantity, long unitPrice) {
			this.skuId = s;
			this.unitPrice = unitPrice;
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

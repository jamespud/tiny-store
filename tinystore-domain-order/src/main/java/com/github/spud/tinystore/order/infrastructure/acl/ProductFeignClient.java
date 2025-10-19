package com.github.spud.tinystore.order.infrastructure.acl;

import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface ProductFeignClient {

	SkuBatchQueryResponse batchGetSkuInfo(List<String> skuIds);

	@Data
	class SkuDTO {

		private String skuName;
		private String mainImage;
		private String specCombination;
		private String merchantId;
		private String status;
		private long salePrice;
		private double weight;
	}

	@Data
	class SkuBatchQueryResponse {

		private Map<String, SkuDTO> skuMap;

	}
}

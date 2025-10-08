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
public interface MerchantFeignClient {

	MerchantBatchQueryResponse batchGetMerchantInfo(List<String> merchantIds);

	class MerchantBatchQueryResponse{

		private Map<String, MerchantDTO> merchantMap;

		public Map<String, MerchantDTO> getMerchantMap() {
			return merchantMap;
		}

		public void setMerchantMap(Map<String, MerchantDTO> merchantMap) {
			this.merchantMap = merchantMap;
		}
	}

	@Data
	class MerchantDTO{

		private String merchantName;
		private long freeFreightThreshold;
		private long baseFreight;
		
	}
}

package com.github.spud.tinystore.order.infrastructure.acl;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface RiskControlFeignClient {

	RiskCheckResponse checkOrderRisk(RiskCheckRequest request);

	MultiShopRiskCheckResponse checkMultiShopOrderRisk(MultiShopRiskCheckRequest build);

	@Data
	class MultiShopRiskCheckResponse {
		private boolean pass;
		private Object reason;
	}

	@Builder
	class MultiShopRiskCheckRequest {

		private String userId;
		private Integer merchantCount;
		private List<SkuRiskDTO> skuList;
		private String addressId;
	}

	@Data
	@AllArgsConstructor
	class SkuRiskDTO {
		private String skuId;
		private Integer quantity;
	}

	@Builder
	class RiskCheckRequest {

		private String userId;
		private List<Object> skuList;
		private String addressId;
	}

	@Data
	class RiskCheckResponse {
		private boolean pass;
		private Object reason;
	}
}

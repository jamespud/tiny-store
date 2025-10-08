package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.infrastructure.acl.RiskControlFeignClient;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/10/6
 */
@Service
public class RiskControlService {

	RiskControlFeignClient riskControlFeignClient;

	public OrderRiskCheckResponse checkOrderRisk(OrderRiskCheckRequest request) {
		return null;
	}

	@Builder
	public static class OrderRiskCheckRequest {

		private String userId;
		private String address;
		private List<ShopRiskDto> shopList;
	}

	public static class ShopRiskDto {

		private String shopId;
		private List<SkuRiskDTO> skuList;
	}

	public class SkuRiskDTO {

		private String skuId;
		private Integer quantity;
	}

	@Getter
	public static class OrderRiskCheckResponse {

		private boolean pass;
		private String reason;
	}
}

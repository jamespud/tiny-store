package com.github.spud.tinystore.order.infrastructure.acl;

import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface LogisticsFeignClient {

	CalculateFreightResponse calculateMerchantFreight(CalculateMerchantFreightRequest request);

	@Builder
	class CalculateMerchantFreightRequest {

		private String merchantId;
		private String addressId;
		private double totalWeight;
		private double freeFreightThreshold;
		private double baseFreight;
	}

	class CalculateFreightResponse {

		private int freightAmount;

		public int getFreightAmount() {
			return freightAmount;
		}

		public void setFreightAmount(int freightAmount) {
			this.freightAmount = freightAmount;
		}
	}
}

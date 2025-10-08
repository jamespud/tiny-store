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
public interface PromotionFeignClient {

	PreUseCouponResponse preUseCoupon(PreUseCouponRequest request);

	void rollbackCouponUse(Object lockId);

	PreUseCouponResponse preUsePlatformCoupon(PreUsePlatformCouponRequest request);

	PreUseCouponResponse preUseMerchantCoupon(PreUseMerchantCouponRequest request);

	@Builder
	class PreUseMerchantCouponRequest {

		private String userId;
		private String merchantId;
		private String couponIds;
		private List<SkuDTO> skuList;
	}

	@Builder
	class PreUsePlatformCouponRequest {

		private String userId;
		private String couponId;
		private List<SkuDTO> skuList;
	}

	@AllArgsConstructor
	class SkuDTO {

		private String skuId;
		private Integer quantity;
		private double price;
		private String merchantId;

		public SkuDTO(String skuId, Integer quantity, double price) {
			this.skuId = skuId;
			this.quantity = quantity;
			this.price = price;
		}
	}

	@Data
	class PreUseCouponResponse {

		private boolean valid;
		private String invalidReason;
		private String lockId;
		private String couponId;
		private int totalDiscount;

		public int getTotalDiscount() {
			return totalDiscount;
		}

		public void setTotalDiscount(int totalDiscount) {
			this.totalDiscount = totalDiscount;
		}
	}

	@Builder
	class PreUseCouponRequest {

		private String userId;
		private List<String> couponId;
		private List<SkuDTO> skuList;
		private String tenantId;
	}
}

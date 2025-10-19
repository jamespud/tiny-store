package com.github.spud.tinystore.order.infrastructure.acl;

import com.github.spud.tinystore.order.infrastructure.acl.PromotionFeignClient.SkuDTO;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "promotion-service", path = "/api/promotion")
public interface PromotionClient {

	@Data
	class CalculateFreightResponse {

		private long freightAmount;
	}

	@Builder
	class CalculateMerchantFreightRequest {

		private String merchantId;
		private String addressId;
		private double totalWeight;
		private long freeFreightThreshold;
		private long baseFreight;
	}

	@Builder
	class PreUseMerchantCouponRequest {

		public String userId;
		public String merchantId;
		public String couponId;
		public List<SkuDetail> skus;
	}

	@Builder
	class PreUsePlatformCouponRequest {

		public String userId;
		public String couponId;
		public List<SkuDetail> skus;
	}

	@Data
	class MerchantInfo {

		public String merchantId;
		public String merchantName;
		public BigDecimal freeFreightThreshold;
		public BigDecimal baseFreight;
	}

	class PreUseRequest {

		public String userId;
		public List<String> couponIds;
		public List<SkuDetail> skus;
		public long orderAmount;
		public String userTags;
		public String traceId;
	}

	@AllArgsConstructor
	class SkuDetail {

		public String skuId;
		public Integer quantity;
		
	}

	@Data
	class PreUseCouponResponse {

		public boolean valid;
		public String invalidReason;
		public long totalDiscount;
		public String lockId;
		private String couponId;
	}

	class AppliedCoupon {

		public String couponId;
		public long discountAmount;
		public String ruleTrace;
	}

	class ConfirmUseRequest {

		public String orderNo;
		public String lockId;
		public String payNo;
		public Long paidAt;
	}

	class ConfirmUseResponse {

		public boolean success;
		public long totalDiscount;
		public String message;
	}

	class RollbackRequest {

		public String orderNo;
		public RefundType refundType;
		public String lockId;
		public String reason;
	}

	enum RefundType {
		FULL,
		PARTIAL
	}

	class RollbackResponse {

		public boolean success;
		public String message;
	}
}

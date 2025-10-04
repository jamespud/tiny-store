package com.github.spud.tinystore.order.infrastructure.acl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "promotion-service", path = "/api/promotion")
public interface PromotionClient {

	@PostMapping("/coupon/pre-use")
	PreUseResponse preUse(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody PreUseRequest request);

	@PostMapping("/coupon/confirm-use")
	ConfirmUseResponse confirmUse(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody ConfirmUseRequest request);

	@PostMapping("/coupon/rollback")
	RollbackResponse rollback(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody RollbackRequest request);

	class PreUseRequest {
		public String userId;
		public List<String> couponIds;
		public List<SkuDetail> skus;
		public BigDecimal orderAmount;
		public String userTags;
		public String traceId;
	}

	class SkuDetail {
		public String skuId;
		public Integer quantity;
		public BigDecimal price;
	}

	class PreUseResponse {
		public boolean valid;
		public List<AppliedCoupon> appliedCoupons;
		public BigDecimal totalDiscount;
		public String lockId;
		public String invalidReason;
	}

	class AppliedCoupon {
		public String couponId;
		public BigDecimal discountAmount;
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
		public List<AppliedCoupon> appliedCoupons;
		public BigDecimal totalDiscount;
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

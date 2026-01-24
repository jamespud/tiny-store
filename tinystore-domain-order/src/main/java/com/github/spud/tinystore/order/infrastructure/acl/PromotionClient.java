package com.github.spud.tinystore.order.infrastructure.acl;

import java.util.List;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "promotion-service", path = "/api/promotion")
public interface PromotionClient {

	@PostMapping("/checkout/quote")
	CheckoutQuoteResponse checkoutQuote(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody CheckoutQuoteRequest request);

	@PostMapping("/checkout/commit")
	CheckoutCommitResponse checkoutCommit(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody CheckoutCommitRequest request);

	@PostMapping("/checkout/release")
	CheckoutReleaseResponse checkoutRelease(@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody CheckoutReleaseRequest request);

	enum CheckoutResultStatus {
		OK,
		OK_WITH_CHANGE,
		REQUOTE_REQUIRED
	}

	@Data
	class ChangeReason {
		private String code;
		private String detail;
	}

	@Data
	class PricingSnapshot {
		private List<PricedLine> lines;
		private long itemsTotalCents;
		private long promotionDiscountTotalCents;
		private long couponDiscountTotalCents;
		private long shippingFeeCents;
		private long payableCents;
		private List<AppliedBenefit> appliedBenefits;
		private SnapshotVersion version;

		@Data
		public static class PricedLine {
			private String skuId;
			private String shopId;
			private int quantity;
			private long baseUnitPriceCents;
			private long finalUnitPriceCents;
			private long lineSubtotalCents;
			private long lineDiscountAllocatedCents;
			private long linePayableCents;
		}

		@Data
		public static class AppliedBenefit {
			private String benefitType;
			private String benefitId;
			private String groupKey;
			private String lockId;
			private long amountCents;
			private String ruleTrace;
		}

		@Data
		public static class SnapshotVersion {
			private String pricingRulesVersion;
			private String shippingRulesVersion;
			private String inputHash;
		}
	}

	@Data
	class CheckoutQuoteRequest {
		private String userId;
		private String traceId;
		private String addressId;
		private List<Line> lines;
		private AppliedIntent appliedIntent;

		@Data
		public static class Line {
			private String skuId;
			private String shopId;
			private Integer quantity;
			private Long baseUnitPriceCents;
			private Long weightGrams;
		}

		@Data
		public static class AppliedIntent {
			private List<String> platformCouponIds;
			private java.util.Map<String, List<String>> shopCouponIdsByShop;
		}
	}

	@Data
	class CheckoutQuoteResponse {
		private CheckoutResultStatus status;
		private String quoteId;
		private long expiresAtEpochMs;
		private PricingSnapshot snapshot;
		private List<ChangeReason> changeReasons;
	}

	@Data
	class CheckoutCommitRequest {
		private String quoteId;
		private String orderNo;
		private String inputHash;
		private String payNo;
		private Long paidAt;
	}

	@Data
	class CheckoutCommitResponse {
		private CheckoutResultStatus status;
		private String finalQuoteId;
		private PricingSnapshot snapshot;
		private List<ChangeReason> changeReasons;
		private String message;
	}

	@Data
	class CheckoutReleaseRequest {
		private String quoteId;
		private String orderNo;
		private String reason;
	}

	@Data
	class CheckoutReleaseResponse {
		private boolean success;
		private String message;
	}
}

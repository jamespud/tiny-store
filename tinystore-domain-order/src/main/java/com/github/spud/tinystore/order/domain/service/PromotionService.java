package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Coupon;
import com.github.spud.tinystore.order.domain.model.Discount;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.domain.model.SubOrder;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CalculateFreightResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CalculateMerchantFreightRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutCommitRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutCommitResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutQuoteRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutQuoteResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutReleaseRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutReleaseResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.MerchantInfo;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PreUseCouponResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PreUseMerchantCouponRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PreUsePlatformCouponRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/10/5
 */
@Service
public class PromotionService {

	@Autowired
	private PromotionClient promotionClient;

	public List<Coupon> getAvailableCoupons(String userId) {
		return List.of();
	}

	public List<Coupon> getAvailableCoupons(String userId, List<String> couponIds) {
		return List.of();
	}

	public List<Discount> findDiscountByShopId(Set<String> shopIds) {
		return List.of();
	}

	public Object calculateOrderPrice(Map<String, Product> products,
		Map<String, Integer> productQuantities,
		List<Coupon> coupons,
		List<Discount> discounts) {
		return null;
	}

	public Map<String, MerchantInfo> batchGetMerchantInfo(List<String> merchantIds) {
		return null;
	}

	public PreUseCouponResponse preUsePlatformCoupon(PreUsePlatformCouponRequest request) {
		return null;
	}

	public PreUseCouponResponse preUseMerchantCoupon(PreUseMerchantCouponRequest request) {
		return null;
	}

	public void rollbackCouponUse(String lockId) {
		CheckoutReleaseRequest req = new CheckoutReleaseRequest();
		req.setQuoteId(lockId);
		req.setReason("rollback");
		promotionClient.checkoutRelease("order:promotion:release:" + lockId, req);
	}

	public CheckoutQuoteResponse checkoutQuote(String idempotencyKey, CheckoutQuoteRequest request) {
		return promotionClient.checkoutQuote(idempotencyKey, request);
	}

	public CheckoutCommitResponse checkoutCommit(String idempotencyKey, CheckoutCommitRequest request) {
		return promotionClient.checkoutCommit(idempotencyKey, request);
	}

	public CheckoutReleaseResponse checkoutRelease(String idempotencyKey, CheckoutReleaseRequest request) {
		return promotionClient.checkoutRelease(idempotencyKey, request);
	}

	public List<SubOrder> allocatePlatformDiscount(List<SubOrder> subOrderList,
		Money platformDiscountTotal) {
		return null;
	}

	public CalculateFreightResponse calculateMerchantFreight(
		CalculateMerchantFreightRequest request) {
		;
		return null;
	}
}


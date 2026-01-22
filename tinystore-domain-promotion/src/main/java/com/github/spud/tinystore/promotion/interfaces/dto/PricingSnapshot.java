package com.github.spud.tinystore.promotion.interfaces.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PricingSnapshot {

	private List<PricedLine> lines = new ArrayList<>();
	private long itemsTotalCents;
	private long promotionDiscountTotalCents;
	private long couponDiscountTotalCents;
	private long shippingFeeCents;
	private long payableCents;
	private List<AppliedBenefit> appliedBenefits = new ArrayList<>();
	private SnapshotVersion version;

	public List<PricedLine> getLines() {
		return lines;
	}

	public void setLines(List<PricedLine> lines) {
		this.lines = lines == null ? Collections.emptyList() : lines;
	}

	public long getItemsTotalCents() {
		return itemsTotalCents;
	}

	public void setItemsTotalCents(long itemsTotalCents) {
		this.itemsTotalCents = itemsTotalCents;
	}

	public long getPromotionDiscountTotalCents() {
		return promotionDiscountTotalCents;
	}

	public void setPromotionDiscountTotalCents(long promotionDiscountTotalCents) {
		this.promotionDiscountTotalCents = promotionDiscountTotalCents;
	}

	public long getCouponDiscountTotalCents() {
		return couponDiscountTotalCents;
	}

	public void setCouponDiscountTotalCents(long couponDiscountTotalCents) {
		this.couponDiscountTotalCents = couponDiscountTotalCents;
	}

	public long getShippingFeeCents() {
		return shippingFeeCents;
	}

	public void setShippingFeeCents(long shippingFeeCents) {
		this.shippingFeeCents = shippingFeeCents;
	}

	public long getPayableCents() {
		return payableCents;
	}

	public void setPayableCents(long payableCents) {
		this.payableCents = payableCents;
	}

	public List<AppliedBenefit> getAppliedBenefits() {
		return appliedBenefits;
	}

	public void setAppliedBenefits(List<AppliedBenefit> appliedBenefits) {
		this.appliedBenefits = appliedBenefits == null ? Collections.emptyList() : appliedBenefits;
	}

	public SnapshotVersion getVersion() {
		return version;
	}

	public void setVersion(SnapshotVersion version) {
		this.version = version;
	}

	public static class PricedLine {
		private String skuId;
		private String shopId;
		private int quantity;
		private long baseUnitPriceCents;
		private long finalUnitPriceCents;
		private long lineSubtotalCents;
		private long lineDiscountAllocatedCents;
		private long linePayableCents;

		public String getSkuId() {
			return skuId;
		}

		public void setSkuId(String skuId) {
			this.skuId = skuId;
		}

		public String getShopId() {
			return shopId;
		}

		public void setShopId(String shopId) {
			this.shopId = shopId;
		}

		public int getQuantity() {
			return quantity;
		}

		public void setQuantity(int quantity) {
			this.quantity = quantity;
		}

		public long getBaseUnitPriceCents() {
			return baseUnitPriceCents;
		}

		public void setBaseUnitPriceCents(long baseUnitPriceCents) {
			this.baseUnitPriceCents = baseUnitPriceCents;
		}

		public long getFinalUnitPriceCents() {
			return finalUnitPriceCents;
		}

		public void setFinalUnitPriceCents(long finalUnitPriceCents) {
			this.finalUnitPriceCents = finalUnitPriceCents;
		}

		public long getLineSubtotalCents() {
			return lineSubtotalCents;
		}

		public void setLineSubtotalCents(long lineSubtotalCents) {
			this.lineSubtotalCents = lineSubtotalCents;
		}

		public long getLineDiscountAllocatedCents() {
			return lineDiscountAllocatedCents;
		}

		public void setLineDiscountAllocatedCents(long lineDiscountAllocatedCents) {
			this.lineDiscountAllocatedCents = lineDiscountAllocatedCents;
		}

		public long getLinePayableCents() {
			return linePayableCents;
		}

		public void setLinePayableCents(long linePayableCents) {
			this.linePayableCents = linePayableCents;
		}
	}

	public static class AppliedBenefit {
		private String benefitType;
		private String benefitId;
		private String groupKey;
		private String lockId;
		private long amountCents;
		private String ruleTrace;

		public String getBenefitType() {
			return benefitType;
		}

		public void setBenefitType(String benefitType) {
			this.benefitType = benefitType;
		}

		public String getBenefitId() {
			return benefitId;
		}

		public void setBenefitId(String benefitId) {
			this.benefitId = benefitId;
		}

		public String getGroupKey() {
			return groupKey;
		}

		public void setGroupKey(String groupKey) {
			this.groupKey = groupKey;
		}

		public String getLockId() {
			return lockId;
		}

		public void setLockId(String lockId) {
			this.lockId = lockId;
		}

		public long getAmountCents() {
			return amountCents;
		}

		public void setAmountCents(long amountCents) {
			this.amountCents = amountCents;
		}

		public String getRuleTrace() {
			return ruleTrace;
		}

		public void setRuleTrace(String ruleTrace) {
			this.ruleTrace = ruleTrace;
		}
	}

	public static class SnapshotVersion {
		private String pricingRulesVersion;
		private String shippingRulesVersion;
		private String inputHash;

		public String getPricingRulesVersion() {
			return pricingRulesVersion;
		}

		public void setPricingRulesVersion(String pricingRulesVersion) {
			this.pricingRulesVersion = pricingRulesVersion;
		}

		public String getShippingRulesVersion() {
			return shippingRulesVersion;
		}

		public void setShippingRulesVersion(String shippingRulesVersion) {
			this.shippingRulesVersion = shippingRulesVersion;
		}

		public String getInputHash() {
			return inputHash;
		}

		public void setInputHash(String inputHash) {
			this.inputHash = inputHash;
		}
	}
}

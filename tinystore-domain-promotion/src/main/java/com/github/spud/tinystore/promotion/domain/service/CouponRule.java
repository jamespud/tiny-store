package com.github.spud.tinystore.promotion.domain.service;

import com.github.spud.tinystore.promotion.domain.model.Coupon;
import com.github.spud.tinystore.promotion.domain.model.CouponEvaluationContext;
import com.github.spud.tinystore.promotion.domain.model.CouponRuleMetadata;

public interface CouponRule {

	int getPriority();

	String getMutexGroup();

	boolean isApplicable(Coupon coupon, CouponEvaluationContext context);

	CouponRuleMetadata getMetadata();
}

package com.github.spud.tinystore.promotion.domain.service;

import com.github.spud.tinystore.promotion.domain.model.CouponEvaluationContext;
import com.github.spud.tinystore.promotion.domain.model.CouponEvaluationResult;

public interface CouponRuleEngine {

	CouponEvaluationResult evaluate(CouponEvaluationContext context);
}

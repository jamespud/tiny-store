package com.github.spud.tinystore.order.domain.service;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/4
 */
@Service
public class CouponService {

	public List<Object> getAvailableCoupons(String userId) {
		return List.of();
	}

	public List<Object> getBestCoupons(String userId, Object order) {
		return getBestCoupons(getAvailableCoupons(userId), order);
	}

	public List<Object> getBestCoupons(List<Object> coupons, Object order) {
		return List.of();
	}

}

package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Coupon;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/4
 */
@Service
public class CouponService {

    public List<Coupon> getAvailableCoupons(String userId) {
        return List.of();
    }

    public List<Coupon> getBestCoupons(String userId, Object order) {
        return getBestCoupons(getAvailableCoupons(userId), order);
    }

    public List<Coupon> getBestCoupons(List<Coupon> coupons, Object order) {
        return List.of();
    }

}

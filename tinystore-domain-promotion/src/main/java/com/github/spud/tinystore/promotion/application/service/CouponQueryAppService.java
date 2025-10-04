package com.github.spud.tinystore.promotion.application.service;

import com.github.spud.tinystore.promotion.interfaces.dto.CouponListResponse;

public class CouponQueryAppService {

	public CouponListResponse list(String userId) {
		// TODO: Load coupon list, evaluate canReceive flag, map to DTO.
		return new CouponListResponse();
	}
}

package com.github.spud.tinystore.promotion.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.spud.tinystore.promotion.application.service.CouponAssetAppService;
import com.github.spud.tinystore.promotion.interfaces.dto.CouponReceiveRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CouponReceiveResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.UserCouponListResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/promotion/coupons")
@Validated
public class CouponAssetController {

	private final CouponAssetAppService appService;

	public CouponAssetController(CouponAssetAppService appService) {
		this.appService = appService;
	}

	@PostMapping("/receive")
	public ResponseEntity<CouponReceiveResponse> receive(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid CouponReceiveRequest request) {
		return ResponseEntity.ok(appService.receive(idempotencyKey, request));
	}

	@GetMapping
	public ResponseEntity<UserCouponListResponse> list(@RequestParam("userId") String userId) {
		return ResponseEntity.ok(appService.listByUser(userId));
	}
}


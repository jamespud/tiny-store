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

import com.github.spud.tinystore.promotion.application.service.ConfirmUseAppService;
import com.github.spud.tinystore.promotion.application.service.PreUseAppService;
import com.github.spud.tinystore.promotion.application.service.RollbackAppService;
import com.github.spud.tinystore.promotion.interfaces.dto.ConfirmUseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.ConfirmUseResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CouponListResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.PreUseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.PreUseResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.ReceiveCouponRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.ReceiveCouponResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.RollbackRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.RollbackResponse;

@RestController
@RequestMapping("/coupon")
@Validated
public class CouponController {

	private final PreUseAppService preUseAppService;
	private final ConfirmUseAppService confirmUseAppService;
	private final RollbackAppService rollbackAppService;

	public CouponController(PreUseAppService preUseAppService, ConfirmUseAppService confirmUseAppService,
		RollbackAppService rollbackAppService) {
		this.preUseAppService = preUseAppService;
		this.confirmUseAppService = confirmUseAppService;
		this.rollbackAppService = rollbackAppService;
	}

	@PostMapping("/receive")
	public ResponseEntity<ReceiveCouponResponse> receiveCoupon(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Validated ReceiveCouponRequest request) {
		// TODO: Delegate to ReceiveCouponAppService.
		return ResponseEntity.ok(new ReceiveCouponResponse());
	}

	@PostMapping("/pre-use")
	public ResponseEntity<PreUseResponse> preUse(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Validated PreUseRequest request) {
		return ResponseEntity.ok(preUseAppService.preUse(idempotencyKey, request));
	}

	@PostMapping("/confirm-use")
	public ResponseEntity<ConfirmUseResponse> confirmUse(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Validated ConfirmUseRequest request) {
		return ResponseEntity.ok(confirmUseAppService.confirm(idempotencyKey, request));
	}

	@PostMapping("/rollback")
	public ResponseEntity<RollbackResponse> rollback(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Validated RollbackRequest request) {
		return ResponseEntity.ok(rollbackAppService.rollback(idempotencyKey, request));
	}

	@GetMapping("/list")
	public ResponseEntity<CouponListResponse> list(@RequestParam("userId") String userId) {
		// TODO: Delegate to CouponQueryAppService.
		return ResponseEntity.ok(new CouponListResponse());
	}
}

package com.github.spud.tinystore.promotion.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.spud.tinystore.promotion.application.service.CheckoutAppService;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutCommitResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutQuoteResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/promotion/checkout")
@Validated
public class CheckoutController {

	private final CheckoutAppService checkoutAppService;

	public CheckoutController(CheckoutAppService checkoutAppService) {
		this.checkoutAppService = checkoutAppService;
	}

	@PostMapping("/quote")
	public ResponseEntity<CheckoutQuoteResponse> quote(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid CheckoutQuoteRequest request) {
		return ResponseEntity.ok(checkoutAppService.quote(idempotencyKey, request));
	}

	@PostMapping("/commit")
	public ResponseEntity<CheckoutCommitResponse> commit(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid CheckoutCommitRequest request) {
		return ResponseEntity.ok(checkoutAppService.commit(idempotencyKey, request));
	}

	@PostMapping("/release")
	public ResponseEntity<CheckoutReleaseResponse> release(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid CheckoutReleaseRequest request) {
		return ResponseEntity.ok(checkoutAppService.release(idempotencyKey, request));
	}
}


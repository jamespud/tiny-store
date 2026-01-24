package com.github.spud.tinystore.product.interfaces.internal.rest;

import com.github.spud.tinystore.product.application.service.InternalSkuQueryService;
import com.github.spud.tinystore.product.interfaces.internal.dto.InternalSkuBatchQueryResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/restful/sku")
@RequiredArgsConstructor
public class InternalSkuController {

	private final InternalSkuQueryService internalSkuQueryService;

	@GetMapping("/batch")
	public ResponseEntity<InternalSkuBatchQueryResponse> batchGetSkuInfo(
		@RequestParam(name = "skuIds", required = false) Set<String> skuIds) {
		return ResponseEntity.ok(internalSkuQueryService.batchGetSkuInfo(skuIds));
	}
}


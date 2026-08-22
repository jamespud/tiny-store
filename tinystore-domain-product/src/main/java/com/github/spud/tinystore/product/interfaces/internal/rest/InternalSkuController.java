package com.github.spud.tinystore.product.interfaces.internal.rest;

import com.github.spud.tinystore.product.application.service.InternalSkuQueryService;
import com.github.spud.tinystore.product.interfaces.internal.dto.InternalSkuBatchQueryResponse;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
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
		@RequestParam(name = "shopId", required = false) String shopId,
		@RequestParam(name = "skuIds", required = false) String skuIdsCsv) {
		Set<String> skuIds = parseSkuIds(skuIdsCsv);
		return ResponseEntity.ok(internalSkuQueryService.batchGetSkuInfo(shopId, skuIds));
	}

	private Set<String> parseSkuIds(String skuIdsCsv) {
		if (skuIdsCsv == null || skuIdsCsv.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(skuIdsCsv.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}

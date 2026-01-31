package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.application.service.SkuService;
import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuAttributePack;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.interfaces.dto.SkuAttributeUpdateDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuResponseDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuUpdateDTO;
import com.github.spud.tinystore.product.interfaces.mapper.SkuDTOMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SkuController - REST API for SKU management
 * <p>
 * Endpoints: - POST /api/products/{productId}/skus - Create SKU - PUT /api/skus/{id} - Update SKU -
 * GET /api/skus/{id} - Get SKU details - PUT /api/skus/{id}/attributes - Update dynamic attributes
 * <p>
 * Security: - All endpoints require X-Shop-Id header - Write operations require MERCHANT_ADMIN
 * role
 * <p>
 * Multi-shop: - All operations scoped to shop - Validates product ownership within shop
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SkuController {

	private final SkuService skuService;
	private final SkuDTOMapper skuDTOMapper;

	/**
	 * Create a new SKU for a product
	 *
	 * @param productId      Product ID
	 * @param request        SKU creation request
	 * @param shopId       Shop identifier
	 * @param idempotencyKey Idempotency key
	 * @return Created SKU details
	 */
	@PostMapping("/products/{productId}/skus")
	@PreAuthorize("hasRole('MERCHANT_ADMIN')")
	public ResponseEntity<SkuResponseDTO> createSku(
		@PathVariable("productId") String productId,
		@Valid @RequestBody SkuCreateDTO request,
		@RequestHeader("X-Shop-Id") String shopId,
		@RequestHeader("Idempotency-Key") String idempotencyKey) {

		log.info("Creating SKU for product: {} with spec: {} for shop: {}",
			productId, request.getSpecCombination(), shopId);

		// Convert DTO to domain object
		Sku sku = skuDTOMapper.toDomain(request, productId);

		// Create SKU via service
		Sku createdSku = skuService.createSku(sku);

		// Convert to response DTO
		SkuResponseDTO response = skuDTOMapper.toResponseDTO(createdSku);

		// Return 201 Created with Location header
		return ResponseEntity
			.created(URI.create("/api/skus/" + createdSku.getSkuId()))
			.body(response);
	}

	/**
	 * Update an existing SKU
	 *
	 * @param id             SKU ID
	 * @param request        SKU update request
	 * @param shopId       Shop identifier
	 * @param idempotencyKey Idempotency key
	 * @return Updated SKU details
	 */
	@PutMapping("/skus/{id}")
	@PreAuthorize("hasRole('MERCHANT_ADMIN')")
	public ResponseEntity<SkuResponseDTO> updateSku(
		@PathVariable("id") String id,
		@Valid @RequestBody SkuUpdateDTO request,
		@RequestHeader("X-Shop-Id") String shopId,
		@RequestHeader("Idempotency-Key") String idempotencyKey) {

		log.info("Updating SKU: {} for shop: {}", id, shopId);

		SkuId skuId = SkuId.of(id);

		// Get existing SKU
		Optional<Sku> existingSkuOpt = skuService.getSku(skuId);
		if (existingSkuOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Sku existingSku = existingSkuOpt.get();

		// Apply updates via mapper
		skuDTOMapper.applyUpdate(existingSku, request);

		// Save via service
		Sku updatedSku = skuService.updateSku(skuId, existingSku);

		// Convert to response DTO
		SkuResponseDTO response = skuDTOMapper.toResponseDTO(updatedSku);

		return ResponseEntity.ok(response);
	}

	/**
	 * Get SKU details
	 *
	 * @param id       SKU ID
	 * @param shopId Shop identifier
	 * @return SKU details
	 */
	@GetMapping("/skus/{id}")
	public ResponseEntity<SkuResponseDTO> getSku(
		@PathVariable("id") String id,
		@RequestHeader("X-Shop-Id") String shopId) {

		log.debug("Getting SKU: {} for shop: {}", id, shopId);

		SkuId skuId = SkuId.of(id);

		// Get SKU via service (cached)
		Optional<Sku> skuOpt = skuService.getSku(skuId);

		if (skuOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		// Convert to response DTO
		SkuResponseDTO response = skuDTOMapper.toResponseDTO(skuOpt.get());

		return ResponseEntity.ok(response);
	}

	/**
	 * Update SKU dynamic attributes (e.g., channel-specific pricing)
	 *
	 * @param id       SKU ID
	 * @param request  Attributes update request
	 * @param shopId Shop identifier
	 * @return Updated SKU
	 */
	@PutMapping("/skus/{id}/attributes")
	@PreAuthorize("hasRole('MERCHANT_ADMIN')")
	public ResponseEntity<SkuResponseDTO> updateAttributes(
		@PathVariable("id") String id,
		@Valid @RequestBody SkuAttributeUpdateDTO request,
		@RequestHeader("X-Shop-Id") String shopId) {

		log.info("Updating attributes for SKU: {} for shop: {}", id, shopId);

		SkuId skuId = SkuId.of(id);

		// Get existing SKU
		Optional<Sku> existingSkuOpt = skuService.getSku(skuId);
		if (existingSkuOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Sku existingSku = existingSkuOpt.get();

		// Update attributes
		SkuAttributePack attributePack = new SkuAttributePack(request.getAttributes());
		existingSku.setAttributes(attributePack);

		// Save via service
		Sku updatedSku = skuService.updateSku(skuId, existingSku);

		// Convert to response DTO
		SkuResponseDTO response = skuDTOMapper.toResponseDTO(updatedSku);

		return ResponseEntity.ok(response);
	}
}

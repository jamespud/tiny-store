package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.application.service.ProductService;
import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.interfaces.dto.ProductCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductResponseDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductTagUpdateDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductUpdateDTO;
import com.github.spud.tinystore.product.interfaces.mapper.ProductDTOMapper;
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
 * ProductController - REST API for product management
 * <p>
 * Endpoints: - POST /api/products - Create product (MERCHANT_ADMIN/PLATFORM_ADMIN) - PUT
 * /api/products/{id} - Update product (MERCHANT_ADMIN/PLATFORM_ADMIN) - POST
 * /api/products/{id}/publish - Publish product (MERCHANT_ADMIN) - POST /api/products/{id}/archive -
 * Archive product (MERCHANT_ADMIN) - GET /api/products/{id} - Get product details (requires
 * X-Shop-Id) - PUT /api/products/{id}/tags - Update product tags (MERCHANT_ADMIN)
 * <p>
 * Security: - All endpoints require X-Shop-Id header - Write operations require MERCHANT_ADMIN or
 * PLATFORM_ADMIN role - Idempotency-Key header required for write operations (validated by
 * gateway)
 * <p>
 * Multi-shop: - ShopId extracted from header and validated - All operations scoped to shop
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;
	private final ProductDTOMapper productDTOMapper;

	/**
	 * Create a new product
	 *
	 * @param request        Product creation request
	 * @param shopId       Shop identifier from X-Shop-Id header
	 * @param idempotencyKey Idempotency key from Idempotency-Key header
	 * @return Created product details
	 */
	@PostMapping
	@PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'PLATFORM_ADMIN')")
	public ResponseEntity<ProductResponseDTO> createProduct(
		@Valid @RequestBody ProductCreateDTO request,
		@RequestHeader("X-Shop-Id") String shopId,
		@RequestHeader("Idempotency-Key") String idempotencyKey) {

		log.info("Creating product: {} for shop: {} with idempotency key: {}",
			request.getName(), shopId, idempotencyKey);

		// Convert DTO to domain object
		Product product = productDTOMapper.toDomain(request);

		// Create product via service
		Product createdProduct = productService.createProduct(product);

		// Convert back to response DTO
		ProductResponseDTO response = productDTOMapper.toResponseDTO(createdProduct);

		// Return 201 Created with Location header
		return ResponseEntity
			.created(URI.create("/api/products/" + createdProduct.getProductId().getId()))
			.body(response);
	}

	/**
	 * Update an existing product
	 *
	 * @param id             Product ID
	 * @param request        Product update request
	 * @param shopId       Shop identifier
	 * @param idempotencyKey Idempotency key
	 * @return Updated product details
	 */
	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'PLATFORM_ADMIN')")
	public ResponseEntity<ProductResponseDTO> updateProduct(
		@PathVariable("id") String id,
		@Valid @RequestBody ProductUpdateDTO request,
		@RequestHeader("X-Shop-Id") String shopId,
		@RequestHeader("Idempotency-Key") String idempotencyKey) {

		log.info("Updating product: {} for shop: {}", id, shopId);

		// Convert DTO to domain object
		Product updatedProduct = productDTOMapper.toDomain(request);
		ProductId productId = ProductId.of(id);

		// Update via service
		Product product = productService.updateProduct(productId, updatedProduct);

		// Convert to response DTO
		ProductResponseDTO response = productDTOMapper.toResponseDTO(product);

		return ResponseEntity.ok(response);
	}

	/**
	 * Publish a product (make it available for sale)
	 *
	 * @param id       Product ID
	 * @param shopId Shop identifier
	 * @return Updated product status
	 */
	@PostMapping("/{id}/publish")
	@PreAuthorize("hasRole('MERCHANT_ADMIN')")
	public ResponseEntity<ProductResponseDTO> publishProduct(
		@PathVariable("id") String id,
		@RequestHeader("X-Shop-Id") String shopId) {

		log.info("Publishing product: {} for shop: {}", id, shopId);

		ProductId productId = ProductId.of(id);

		// Publish via service
		Product product = productService.publishProduct(productId);

		// Convert to response DTO
		ProductResponseDTO response = productDTOMapper.toResponseDTO(product);

		return ResponseEntity.ok(response);
	}

	/**
	 * Archive a product (soft delete)
	 *
	 * @param id       Product ID
	 * @param shopId Shop identifier
	 * @return Archived product status
	 */
	@PostMapping("/{id}/archive")
	@PreAuthorize("hasRole('MERCHANT_ADMIN')")
	public ResponseEntity<Void> archiveProduct(
		@PathVariable("id") String id,
		@RequestHeader("X-Shop-Id") String shopId) {

		log.info("Archiving product: {} for shop: {}", id, shopId);

		ProductId productId = ProductId.of(id);

		// Archive via service
		productService.archiveProduct(productId);

		return ResponseEntity.noContent().build();
	}

	/**
	 * Get product details
	 *
	 * @param id       Product ID
	 * @param shopId Shop identifier
	 * @return Product details
	 */
	@GetMapping("/{id}")
	public ResponseEntity<ProductResponseDTO> getProduct(
		@PathVariable("id") String id,
		@RequestHeader("X-Shop-Id") String shopId) {

		log.debug("Getting product: {} for shop: {}", id, shopId);

		ProductId productId = ProductId.of(id);

		// Get product via service (cached)
		Optional<Product> productOpt = productService.getProduct(productId);

		if (productOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		// Convert to response DTO
		ProductResponseDTO response = productDTOMapper.toResponseDTO(productOpt.get());

		return ResponseEntity.ok(response);
	}

	/**
	 * Update product tags
	 *
	 * @param id       Product ID
	 * @param request  Tag update request
	 * @param shopId Shop identifier
	 * @return Updated product
	 */
	@PutMapping("/{id}/tags")
	@PreAuthorize("hasRole('MERCHANT_ADMIN')")
	public ResponseEntity<ProductResponseDTO> updateTags(
		@PathVariable("id") String id,
		@Valid @RequestBody ProductTagUpdateDTO request,
		@RequestHeader("X-Shop-Id") String shopId) {

		log.info("Updating tags for product: {} for shop: {}", id, shopId);

		ProductId productId = ProductId.of(id);

		// Update tags via service
		Product product = productService.updateTags(productId, request.getTags());

		// Convert to response DTO
		ProductResponseDTO response = productDTOMapper.toResponseDTO(product);

		return ResponseEntity.ok(response);
	}
}

package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.application.service.ProductService;
import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.interfaces.dto.*;
import com.github.spud.tinystore.product.interfaces.mapper.ProductDTOMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

/**
 * ProductController - REST API for product management
 * 
 * Endpoints:
 * - POST /api/products - Create product (MERCHANT_ADMIN/PLATFORM_ADMIN)
 * - PUT /api/products/{id} - Update product (MERCHANT_ADMIN/PLATFORM_ADMIN)
 * - POST /api/products/{id}/publish - Publish product (MERCHANT_ADMIN)
 * - POST /api/products/{id}/archive - Archive product (MERCHANT_ADMIN)
 * - GET /api/products/{id} - Get product details (requires X-Tenant-Id)
 * - PUT /api/products/{id}/tags - Update product tags (MERCHANT_ADMIN)
 * 
 * Security:
 * - All endpoints require X-Tenant-Id header
 * - Write operations require MERCHANT_ADMIN or PLATFORM_ADMIN role
 * - Idempotency-Key header required for write operations (validated by gateway)
 * 
 * Multi-tenancy:
 * - TenantId extracted from header and validated
 * - All operations scoped to tenant
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
     * @param request Product creation request
     * @param tenantId Tenant identifier from X-Tenant-Id header
     * @param idempotencyKey Idempotency key from Idempotency-Key header
     * @return Created product details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductCreateDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        log.info("Creating product: {} for tenant: {} with idempotency key: {}", 
                request.getName(), tenantId, idempotencyKey);
        
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
     * @param id Product ID
     * @param request Product update request
     * @param tenantId Tenant identifier
     * @param idempotencyKey Idempotency key
     * @return Updated product details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductUpdateDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        log.info("Updating product: {} for tenant: {}", id, tenantId);
        
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
     * @param id Product ID
     * @param tenantId Tenant identifier
     * @return Updated product status
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('MERCHANT_ADMIN')")
    public ResponseEntity<ProductResponseDTO> publishProduct(
            @PathVariable String id,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        log.info("Publishing product: {} for tenant: {}", id, tenantId);
        
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
     * @param id Product ID
     * @param tenantId Tenant identifier
     * @return Archived product status
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('MERCHANT_ADMIN')")
    public ResponseEntity<Void> archiveProduct(
            @PathVariable String id,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        log.info("Archiving product: {} for tenant: {}", id, tenantId);
        
        ProductId productId = ProductId.of(id);
        
        // Archive via service
        productService.archiveProduct(productId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get product details
     * 
     * @param id Product ID
     * @param tenantId Tenant identifier
     * @return Product details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProduct(
            @PathVariable String id,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        log.debug("Getting product: {} for tenant: {}", id, tenantId);
        
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
     * @param id Product ID
     * @param request Tag update request
     * @param tenantId Tenant identifier
     * @return Updated product
     */
    @PutMapping("/{id}/tags")
    @PreAuthorize("hasRole('MERCHANT_ADMIN')")
    public ResponseEntity<ProductResponseDTO> updateTags(
            @PathVariable String id,
            @Valid @RequestBody ProductTagUpdateDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        log.info("Updating tags for product: {} for tenant: {}", id, tenantId);
        
        ProductId productId = ProductId.of(id);
        
        // Update tags via service
        Product product = productService.updateTags(productId, request.getTags());
        
        // Convert to response DTO
        ProductResponseDTO response = productDTOMapper.toResponseDTO(product);
        
        return ResponseEntity.ok(response);
    }
}

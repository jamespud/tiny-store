package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.interfaces.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
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
        // TODO: Implement product creation
        // 1. Validate tenant access
        // 2. Call ProductService.createProduct
        // 3. Return created product
        throw new UnsupportedOperationException("ProductController.createProduct not yet implemented");
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
        // TODO: Implement product update
        throw new UnsupportedOperationException("ProductController.updateProduct not yet implemented");
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
        // TODO: Implement product publishing
        throw new UnsupportedOperationException("ProductController.publishProduct not yet implemented");
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
        // TODO: Implement product archiving
        throw new UnsupportedOperationException("ProductController.archiveProduct not yet implemented");
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
        // TODO: Implement product retrieval with caching
        throw new UnsupportedOperationException("ProductController.getProduct not yet implemented");
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
        // TODO: Implement tag update
        throw new UnsupportedOperationException("ProductController.updateTags not yet implemented");
    }
}

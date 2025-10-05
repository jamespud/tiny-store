package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.application.service.PricingService;
import com.github.spud.tinystore.product.application.service.SkuService;
import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.id.SkuId;
import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import com.github.spud.tinystore.product.interfaces.dto.*;
import com.github.spud.tinystore.product.interfaces.mapper.PricingDTOMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * PricingController - REST API for pricing calculations
 * 
 * Endpoints:
 * - POST /api/products/calculate-price - Calculate final price with rules
 * 
 * Security:
 * - Requires X-Tenant-Id header
 * - Public endpoint (no authentication required for price calculation)
 * 
 * Response includes:
 * - Final price
 * - Applied adjustments (rule by rule)
 * - SKU version
 * - Rule versions (for order recalculation)
 * - Price signature (for tampering detection)
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class PricingController {
    
    private final PricingService pricingService;
    private final SkuService skuService;
    private final PricingDTOMapper pricingDTOMapper;
    
    /**
     * Calculate price for a SKU with applied pricing rules
     * 
     * @param request Pricing context (SKU, user tags, channel, etc.)
     * @param tenantId Tenant identifier
     * @return Pricing result with final price, adjustments, and versions
     */
    @PostMapping("/calculate-price")
    public ResponseEntity<PricingResultDTO> calculatePrice(
            @Valid @RequestBody PricingContextDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        log.info("Calculating price for SKU: {} for tenant: {}", request.getSkuId(), tenantId);
        
        SkuId skuId = new SkuId(request.getSkuId());
        
        // Load SKU
        Optional<Sku> skuOpt = skuService.getSku(skuId);
        if (skuOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Sku sku = skuOpt.get();
        
        // Convert DTO to pricing context
        PricingContext context = pricingDTOMapper.toDomain(request, sku);
        
        // Calculate price via service
        PricingResult result = pricingService.calculatePrice(context);
        
        // Convert to response DTO
        PricingResultDTO response = pricingDTOMapper.toResponseDTO(result);
        
        return ResponseEntity.ok(response);
    }
}

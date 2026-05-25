package com.github.spud.tinystore.inventory.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for POST /api/inventory/reservations/confirm
 * <p>
 * Triggered on payment success. The idempotency key must be derived from paymentId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryConfirmRequest {

    @NotBlank
    private String paymentId;

    @NotBlank
    private String tradeId;

    @NotBlank
    private String orderId;

    private String traceId;

    @NotEmpty
    private List<OccupyPairDto> occupyPairs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OccupyPairDto {
        @NotBlank
        private String shopId;
        @NotBlank
        private String skuId;
        @NotBlank
        private String occupyId;
    }
}

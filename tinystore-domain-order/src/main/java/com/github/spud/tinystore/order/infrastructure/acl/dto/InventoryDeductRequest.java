package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Inventory 域 deduct 请求 DTO（对齐 DeductRequest）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDeductRequest {
    private String orderId;
    private String tradeId;

    /**
     * The reservation window this caller wants, in minutes (review round-3 P1).
     *
     * <p>Inventory enforces {@code <= inventory.uncommit.max-reservation-ttl} before touching Redis: the
     * orphan reclaim assumes every legitimate reservation window has closed by the time a Redis uncommit
     * member is old enough to be reclaimed, and that only holds if the caller's window fits the budget. Sent
     * explicitly so the invariant cannot be broken by deployment configuration drift (the two services read
     * different properties).
     */
    private Long reservationTtlMinutes;

    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String shopId;
        private String skuId;
        private Integer quantity;
    }
}

package com.github.spud.tinystore.inventory.domain.value;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Result of an inventory adjustment.
 * <p>
 * newlyAdjustedItems contains ONLY items actually written in this call
 * (idempotent-skipped and concurrent-duplicate items are excluded).
 * It is the input to Redis post-commit compensation so Redis is never double-bumped.
 */
@Data
@Builder
public class AdjustmentResult {
    private boolean success;
    private String message;
    @Builder.Default
    private List<AdjustedItem> newlyAdjustedItems = Collections.emptyList();

    @Data
    @Builder
    public static class AdjustedItem {
        private String shopId;
        private String skuId;
        private long delta;
    }

    public static AdjustmentResult ok(List<AdjustedItem> newlyAdjustedItems) {
        return AdjustmentResult.builder()
                .success(true)
                .message("ok")
                .newlyAdjustedItems(newlyAdjustedItems == null ? Collections.emptyList() : newlyAdjustedItems)
                .build();
    }

    public static AdjustmentResult fail(String message) {
        return AdjustmentResult.builder()
                .success(false)
                .message(message)
                .newlyAdjustedItems(Collections.emptyList())
                .build();
    }
}

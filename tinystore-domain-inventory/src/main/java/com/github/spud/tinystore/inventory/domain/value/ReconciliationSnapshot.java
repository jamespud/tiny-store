package com.github.spud.tinystore.inventory.domain.value;

import lombok.Builder;
import lombok.Data;

/**
 * Read-only reconciliation snapshot for one (shopId, skuId).
 * Redis fields are null when the Redis key is absent (never initialized).
 */
@Data
@Builder
public class ReconciliationSnapshot {
    private String shopId;
    private String skuId;
    private long dbTotalQuantity;
    private long dbConfirmedQuantity;
    private long dbPreDeductedQuantity;
    private Long redisTotal;    // null if key absent
    private Long redisDeducted; // null if key absent

    /** Invariant violation: CHECK(total>=0) should prevent this. */
    public boolean isDbOversold() {
        return dbTotalQuantity < 0;
    }

    /** Redis total cache disagrees with authoritative DB total (either direction). */
    public boolean isTotalDesync() {
        return redisTotal != null && redisTotal != dbTotalQuantity;
    }

    /** OVERSELL RISK: Redis thinks less is deducted than DB has reserved -> may over-admit. */
    public boolean isAdmissionUnderCounted() {
        return redisDeducted != null && redisDeducted < dbPreDeductedQuantity;
    }

    /** LOST-SALES RISK: Redis thinks more is deducted than DB has reserved -> over-rejects. */
    public boolean isAdmissionOverCounted() {
        return redisDeducted != null && redisDeducted > dbPreDeductedQuantity;
    }

    /** Redis-layer oversell: available (total - deducted) already negative. */
    public boolean isNegativeAvailable() {
        return redisTotal != null && redisDeducted != null && (redisTotal - redisDeducted < 0);
    }
}

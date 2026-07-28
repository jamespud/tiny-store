package com.github.spud.tinystore.inventory.domain.value;

import lombok.Builder;
import lombok.Data;

/**
 * Read-only reconciliation snapshot for one (shopId, skuId).
 * <p>
 * Redis counters include CONFIRMED amounts (confirm does not touch Redis), while
 * DB inventory_stock.total_quantity excludes confirmed and inventory_reservation
 * PRE_DEDUCTED excludes confirmed. Therefore the authoritative Redis targets are:
 * <ul>
 *   <li>redisTotal should == dbTotalQuantity + dbConfirmedQuantity</li>
 *   <li>redisDeducted should == dbPreDeductedQuantity + dbConfirmedQuantity</li>
 * </ul>
 * Redis fields are null when the Redis key is absent (never initialized); null is
 * not treated as a desync (cannot determine direction).
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

    /** Authoritative Redis total target: DB remaining + confirmed (confirm does not decrement Redis). */
    public long getTargetTotal() {
        return dbTotalQuantity + dbConfirmedQuantity;
    }

    /** Authoritative Redis deducted target: in-flight + confirmed (confirm does not decrement Redis). */
    public long getTargetDeducted() {
        return dbPreDeductedQuantity + dbConfirmedQuantity;
    }

    /** Invariant violation: CHECK(total>=0) should prevent this. */
    public boolean isDbOversold() {
        return dbTotalQuantity < 0;
    }

    /** Oversell risk: Redis cap higher than authoritative target. */
    public boolean isTotalTooHigh() {
        return redisTotal != null && redisTotal > getTargetTotal();
    }

    /** Lost-sales risk: Redis cap lower than authoritative target. */
    public boolean isTotalTooLow() {
        return redisTotal != null && redisTotal < getTargetTotal();
    }

    /** Oversell risk: Redis admission count lower than target (available looks too high). */
    public boolean isDeductedTooLow() {
        return redisDeducted != null && redisDeducted < getTargetDeducted();
    }

    /** Lost-sales risk: Redis admission count higher than target (over-rejecting). */
    public boolean isDeductedTooHigh() {
        return redisDeducted != null && redisDeducted > getTargetDeducted();
    }

    /** Redis-layer oversell: available (total - deducted) already negative. */
    public boolean isNegativeAvailable() {
        return redisTotal != null && redisDeducted != null && (redisTotal - redisDeducted < 0);
    }

    /** Any oversell-risk desync (auto-repair candidate for the reconcile job). */
    public boolean isOversellRisk() {
        return isTotalTooHigh() || isDeductedTooLow() || isNegativeAvailable();
    }

    /** Any lost-sales-risk desync (alert only; repairing would risk oversell). */
    public boolean isLostSalesRisk() {
        return isTotalTooLow() || isDeductedTooHigh();
    }

    /** Redis counters match authoritative targets (null keys count as in-sync). */
    public boolean isRedisInSync() {
        boolean totalOk = redisTotal == null || redisTotal == getTargetTotal();
        boolean deductedOk = redisDeducted == null || redisDeducted == getTargetDeducted();
        return totalOk && deductedOk;
    }
}

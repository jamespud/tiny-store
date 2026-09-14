package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeIdempotencyRecordEntity;

public interface JpaTradeIdempotencyRecordRepository
    extends JpaRepository<TradeIdempotencyRecordEntity, String> {

    /**
     * Take the durable create-trade claim for one Idempotency-Key (review round 3 P0).
     *
     * <p>Runs inside the caller's transaction, which is the whole point: PostgreSQL makes a concurrent
     * {@code INSERT ... ON CONFLICT DO NOTHING} on the same key wait for the transaction that already holds
     * the row. So the request that gets {@code 1} owns the saga, and a request that gets {@code 0} only
     * returns after the owner committed (it must then read the committed row and replay it) or rolled back
     * (the claim disappeared, so a later request can try again). Redis is no longer the only thing keeping
     * two replicas from executing the same create.
     *
     * @return 1 when this transaction inserted the claim, 0 when the key was already claimed
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO tinystore_order.trade_idempotency_record "
            + "(idempotency_key, scope, fingerprint, state, created_at) "
            + "VALUES (:key, :scope, :fingerprint, 'PROCESSING', :createdAt) "
            + "ON CONFLICT (idempotency_key) DO NOTHING", nativeQuery = true)
    int claimProcessing(@Param("key") String key,
                        @Param("scope") String scope,
                        @Param("fingerprint") String fingerprint,
                        @Param("createdAt") LocalDateTime createdAt);
}

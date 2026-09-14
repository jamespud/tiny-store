package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Durable create-trade idempotency record (review P0-2).
 *
 * <p>Written in the <b>same transaction</b> as the trade, so "trade committed" and "this key produced this
 * response" can never disagree. Redis keeps the PROCESSING/IN_PROGRESS lock, but is no longer the only place
 * a successful outcome lives: if Redis fails after the commit, or loses the key, a retry with the same key
 * and body replays this row instead of creating a second trade.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trade_idempotency_record", schema = "tinystore_order")
public class TradeIdempotencyRecordEntity {

    @Id
    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "scope", length = 64, nullable = false)
    private String scope;

    @Column(name = "fingerprint", length = 128, nullable = false)
    private String fingerprint;

    @Column(name = "state", length = 20, nullable = false)
    private String state;

    @Column(name = "trade_id", length = 64, nullable = false)
    private String tradeId;

    @Column(name = "response_json", nullable = false, columnDefinition = "text")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

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
 * Durable create-trade idempotency record (review P0-2, extended by review round 3 P0).
 *
 * <p>Written in the <b>same transaction</b> as the trade, so "trade committed" and "this key produced this
 * response" can never disagree.
 *
 * <p>The row is also the durable <b>claim</b>: createTrade inserts it with {@code state=PROCESSING} before
 * calling promotion or inventory, so two concurrent replicas with one Idempotency-Key cannot both run the
 * saga even if Redis loses the key. {@code tradeId}/{@code responseJson} are filled in when the saga
 * completes and the row moves to {@code state=COMMITTED} -- still inside the same transaction -- hence they
 * are nullable (a PROCESSING claim has neither yet).
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

    /** Null until the saga completes and the row becomes COMMITTED. */
    @Column(name = "trade_id", length = 64)
    private String tradeId;

    /** Null until the saga completes and the row becomes COMMITTED. */
    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

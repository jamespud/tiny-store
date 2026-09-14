package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbox 事件投递 JPA 实体
 */
@Entity
@Table(name = "order_outbox", schema = "tinystore_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /** 认领该事件的实例标识（claim 协议，多副本互斥）。 */
    @Column(name = "claimed_by", length = 128)
    private String claimedBy;

    /** 认领时间，用于回收僵尸认领（实例崩溃后长期停留在 PROCESSING）。 */
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /**
     * 本次认领的 fencing token（每次 claim 生成）。
     *
     * <p>claimed_by 只能说明"谁"，不能区分同一个实例的旧 lease 与新 lease；过期回收后旧 owner 仍可能
     * 覆盖新 owner 的状态。所有完成类更新都必须带上本 token（{@code WHERE event_id=? AND claim_token=?}），
     * 影响行数为 0 即表示 lease 已失效，旧 worker 不得再改状态。
     */
    @Column(name = "claim_token", length = 64)
    private String claimToken;

    /** 下次可认领时间；NULL 表示立即可认领（传输失败后的退避重试，见 C12）。 */
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}

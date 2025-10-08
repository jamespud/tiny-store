package com.github.spud.tinystore.order.infrastructure.persistence.po;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outbox 事件持久化对象
 * 实现 Outbox Pattern 确保事件的最终一致性
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "order_outbox_event")
public class OrderOutboxEventPO {

	/**
	 * 事件唯一标识
	 */
	@Id
	@UuidGenerator
	@Column(name = "id")
	private String id;

	/**
	 * 订单号
	 */
	@Column(name = "order_no", nullable = false, length = 64)
	private String orderId;

	/**
	 * 事件类型
	 */
	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	/**
	 * 事件负载 (JSON)
	 */
	@Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
	private String eventPayload;

	/**
	 * 分布式追踪ID
	 */
	@Column(name = "trace_id", length = 128)
	private String traceId;

	/**
	 * 事件状态
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private OutboxEventStatus status = OutboxEventStatus.PENDING;

	/**
	 * 重试次数
	 */
	@Column(name = "retry_count", nullable = false)
	private Integer retryCount = 0;

	/**
	 * 变更ID (预留给 CDC)
	 */
	@Column(name = "change_id")
	private Long changeId;

	/**
	 * 创建时间
	 */
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	/**
	 * 更新时间
	 */
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt = OffsetDateTime.now();

	/**
	 * Outbox 事件状态枚举
	 */
	public enum OutboxEventStatus {
		PENDING,    // 待发送
		SENT,       // 已发送
		FAILED      // 发送失败
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
	}
}
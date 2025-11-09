package com.github.spud.tinystore.order.infrastructure.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.UuidGenerator;

/**
 * 订单状态审计持久化对象 记录订单状态变更的完整审计链路
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "order_status_audit")
public class OrderStatusAuditPO {

	/**
	 * 审计记录唯一标识
	 */
	@Id
	@UuidGenerator
	@Column(name = "id")
	private UUID id;

	/**
	 * 订单号
	 */
	@Column(name = "order_no", nullable = false, length = 64)
	private String orderNo;

	/**
	 * 变更前状态
	 */
	@Column(name = "from_status", length = 64)
	private String fromStatus;

	/**
	 * 变更后状态
	 */
	@Column(name = "to_status", nullable = false, length = 64)
	private String toStatus;

	/**
	 * 操作者类型
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false)
	private ActorType actorType;

	/**
	 * 操作者ID
	 */
	@Column(name = "actor_id", nullable = false, length = 64)
	private String actorId;

	/**
	 * 变更原因
	 */
	@Column(name = "reason", length = 255)
	private String reason;

	/**
	 * 关联的领域事件ID
	 */
	@Column(name = "event_id")
	private String eventId;

	/**
	 * 分布式追踪ID
	 */
	@Column(name = "trace_id", length = 128)
	private String traceId;

	/**
	 * 创建时间
	 */
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	/**
	 * 操作者类型枚举
	 */
	public enum ActorType {
		USER,       // 用户操作
		MERCHANT,   // 商户操作
		SYSTEM      // 系统操作
	}
}
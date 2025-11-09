package com.github.spud.tinystore.order.infrastructure.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 订单幂等性控制持久化对象 基于 requestId 确保接口调用的幂等性
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Entity
@Table(name = "order_idempotency")
public class OrderIdempotencyPO {

	/**
	 * 请求唯一标识 (幂等键)
	 */
	@Id
	@Column(name = "request_id", length = 128)
	private String requestId;

	/**
	 * 订单号
	 */
	@Column(name = "order_no", length = 64)
	private String orderNo;

	/**
	 * 操作类型
	 */
	@Column(name = "operation", nullable = false, length = 64)
	private String operation;

	/**
	 * 响应数据 (JSON)
	 */
	@Column(name = "response_data", columnDefinition = "jsonb")
	private String responseData;

	/**
	 * 处理状态
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private IdempotencyStatus status = IdempotencyStatus.PROCESSING;

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
	 * 过期时间 (默认24小时)
	 */
	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

	/**
	 * 幂等性状态枚举
	 */
	public enum IdempotencyStatus {
		PROCESSING,  // 处理中
		COMPLETED,   // 已完成
		FAILED       // 处理失败
	}
}
package com.github.spud.tinystore.infrastrucutre.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "order_outbox", schema = "order_db")
public class OrderOutbox {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Size(max = 50)
	@NotNull
	@Column(name = "type", nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	private Type type;

	@Size(max = 50)
	@NotNull
	@Column(name = "status", nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	private OrderOutbox.Status status;

	@NotNull
	@Column(name = "payload_json", nullable = false)
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> payloadJson;

	@Column(name = "retry_count")
	private int retryCount;

	@Column(name = "next_retry_time")
	private OffsetDateTime nextRetryTime;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
	@NotNull
	@ColumnDefault("now()")
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

/*
 TODO [Reverse Engineering] create field to map the 'status' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @ColumnDefault("'NEW'")
    @Column(name = "status", columnDefinition = "outbox_status not null")
    private Object status;
*/

	@PrePersist
	public void prePersist() {
		this.createdAt = OffsetDateTime.from(LocalDateTime.now());
		this.updatedAt = OffsetDateTime.from(LocalDateTime.now());
		this.status = this.status == null ? Status.PENDING : this.status;
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = OffsetDateTime.from(LocalDateTime.now());
	}
	
	public enum Type {
		OrderCreated,
		PaymentCompleted,
		OrderCancelled,
		StockFrozen,
		StockReleased
	}

	public enum Status {
		PENDING,    // 待处理
		PROCESSING, // 处理中（避免并发重复处理）
		SENT,       // 已发送
		FAILED      // 发送失败（达到最大重试次数）
	}
}
package com.github.spud.tinystore.order.infrastructure.compensation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 资源释放补偿任务
 */
@Getter
@Setter
@Entity
@Table(name = "order_resource_release_task", schema = "order_db")
public class ResourceReleaseTask {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@Column(nullable = false)
	private UUID orderId;

	@Column(nullable = false, length = 40)
	private String orderStatusAtFail;

	@Column(nullable = false, length = 64)
	private String resourceType; // STOCK / COUPON / POINTS / ALL

	@Column(nullable = false)
	private int retryCount;

	@Column
	private OffsetDateTime nextRetryTime;

	@Column(length = 400)
	private String lastError;

	@CreationTimestamp
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	private OffsetDateTime updatedAt;

	@Column(nullable = false)
	private boolean completed;
}
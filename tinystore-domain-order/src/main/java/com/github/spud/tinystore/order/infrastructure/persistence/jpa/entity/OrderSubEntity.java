package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Entity
@Table(name = "order_sub", uniqueConstraints = {
	@UniqueConstraint(name = "uk_order_sub_order_no", columnNames = {"sub_order_no"})
})
public class OrderSubEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "sub_order_no", nullable = false, length = 64)
	private String subOrderNo;

	@Column(name = "main_order_no", nullable = false, length = 64)
	private String mainOrderNo;

	@Column(name = "tenant_id", nullable = false, length = 100)
	private String tenantId;

	@Column(name = "merchant_id", length = 64)
	private String merchantId;

	@Column(name = "merchant_name", length = 128)
	private String merchantName;

	@Column(name = "core_flow_status", nullable = false, length = 32)
	private String coreFlowStatus;

	@Column(name = "payment_status", nullable = false, length = 32)
	private String paymentStatus;

	@Column(name = "fulfillment_status", nullable = false, length = 32)
	private String fulfillmentStatus;

	@Column(name = "after_sale_status", nullable = false, length = 32)
	private String afterSaleStatus;

	@Column(name = "logistics_company_code", length = 64)
	private String logisticsCompanyCode;

	@Column(name = "logistics_company_name", length = 128)
	private String logisticsCompanyName;

	@Column(name = "tracking_no", length = 128)
	private String trackingNo;

	@Column(name = "shipped_at")
	private OffsetDateTime shippedAt;

	@Column(name = "delivered_at")
	private OffsetDateTime deliveredAt;

	@Column(name = "received_at")
	private OffsetDateTime receivedAt;

	@Column(name = "stock_pre_occupy_ids", length = 2000)
	private String stockPreOccupyIds;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@PrePersist
	public void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.version == null) this.version = 0L;
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
		if (this.version == null) this.version = 0L;
	}
}

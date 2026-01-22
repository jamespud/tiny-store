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
@Table(name = "order_main", uniqueConstraints = {
	@UniqueConstraint(name = "uk_order_main_order_no", columnNames = {"order_no"})
})
public class OrderMainEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "order_no", nullable = false, length = 64)
	private String orderNo;

	@Column(name = "tenant_id", nullable = false, length = 100)
	private String tenantId;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(name = "core_flow_status", nullable = false, length = 32)
	private String coreFlowStatus;

	@Column(name = "payment_status", nullable = false, length = 32)
	private String paymentStatus;

	@Column(name = "fulfillment_status", nullable = false, length = 32)
	private String fulfillmentStatus;

	@Column(name = "after_sale_status", nullable = false, length = 32)
	private String afterSaleStatus;

	@Column(name = "total_amount", nullable = false)
	private Long totalAmount = 0L;

	@Column(name = "discount_amount", nullable = false)
	private Long discountAmount = 0L;

	@Column(name = "payable_amount", nullable = false)
	private Long payableAmount = 0L;

	@Column(name = "currency", nullable = false, length = 8)
	private String currency = "CNY";

	@Column(name = "address_id", length = 64)
	private String addressId;

	@Column(name = "address_snapshot", columnDefinition = "jsonb")
	private String addressSnapshot;

	@Column(name = "promotion_quote_id", length = 64)
	private String promotionQuoteId;

	@Column(name = "promotion_input_hash", length = 64)
	private String promotionInputHash;

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
		if (this.totalAmount == null) this.totalAmount = 0L;
		if (this.discountAmount == null) this.discountAmount = 0L;
		if (this.payableAmount == null) this.payableAmount = 0L;
		if (this.currency == null || this.currency.isBlank()) this.currency = "CNY";
		if (this.version == null) this.version = 0L;
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
		if (this.totalAmount == null) this.totalAmount = 0L;
		if (this.discountAmount == null) this.discountAmount = 0L;
		if (this.payableAmount == null) this.payableAmount = 0L;
		if (this.currency == null || this.currency.isBlank()) this.currency = "CNY";
		if (this.version == null) this.version = 0L;
	}
}

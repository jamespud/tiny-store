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
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Entity
@Table(name = "order_item", uniqueConstraints = {
	@UniqueConstraint(name = "uk_order_item_line", columnNames = {"main_order_no", "line_no"})
})
public class OrderItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "main_order_no", nullable = false, length = 64)
	private String mainOrderNo;

	@Column(name = "sub_order_no", length = 64)
	private String subOrderNo;

	@Column(name = "line_no", nullable = false)
	private Integer lineNo;

	@Column(name = "sku_id", nullable = false, length = 100)
	private String skuId;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@Column(name = "unit_price_amount", nullable = false)
	private Long unitPriceAmount = 0L;

	@Column(name = "line_total_amount", nullable = false)
	private Long lineTotalAmount = 0L;

	@Column(name = "line_discount_amount", nullable = false)
	private Long lineDiscountAmount = 0L;

	@Column(name = "line_payable_amount", nullable = false)
	private Long linePayableAmount = 0L;

	@Column(name = "currency", nullable = false, length = 8)
	private String currency = "CNY";

	@Column(name = "sku_snapshot", nullable = false, columnDefinition = "jsonb")
	private String skuSnapshot;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@PrePersist
	public void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.unitPriceAmount == null) this.unitPriceAmount = 0L;
		if (this.lineTotalAmount == null) this.lineTotalAmount = 0L;
		if (this.lineDiscountAmount == null) this.lineDiscountAmount = 0L;
		if (this.linePayableAmount == null) this.linePayableAmount = 0L;
		if (this.currency == null || this.currency.isBlank()) this.currency = "CNY";
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
		if (this.unitPriceAmount == null) this.unitPriceAmount = 0L;
		if (this.lineTotalAmount == null) this.lineTotalAmount = 0L;
		if (this.lineDiscountAmount == null) this.lineDiscountAmount = 0L;
		if (this.linePayableAmount == null) this.linePayableAmount = 0L;
		if (this.currency == null || this.currency.isBlank()) this.currency = "CNY";
	}
}


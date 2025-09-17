package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventory_stock", uniqueConstraints = {
	@UniqueConstraint(name = "uk_stock_shop_sku", columnNames = {"shop_id", "sku_id"})
})
@Getter
@Setter
public class StockEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "shop_id", nullable = false)
	private String shopId;

	@Column(name = "sku_id", nullable = false)
	private String skuId;

	@Column(name = "total_quantity", nullable = false)
	private long totalQuantity;

	@Column(name = "reserved_quantity", nullable = false)
	private long reservedQuantity;

	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}


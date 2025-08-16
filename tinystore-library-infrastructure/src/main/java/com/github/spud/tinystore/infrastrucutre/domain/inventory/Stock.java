package com.github.spud.tinystore.infrastrucutre.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "stock", schema = "inventory_db")
public class Stock {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@Column(name = "sku_id", nullable = false)
	private UUID skuId;

	@NotNull
	@ColumnDefault("'00000000-0000-0000-0000-000000000001'")
	@Column(name = "location_id", nullable = false)
	private UUID locationId;

	@NotNull
	@Column(name = "total", nullable = false)
	private Integer total;

	@NotNull
	@ColumnDefault("0")
	@Column(name = "reserved", nullable = false)
	private Integer reserved;

	@NotNull
	@ColumnDefault("1")
	@Column(name = "version", nullable = false)
	private Integer version;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
	
	public void freeze(Integer amount) {
		if (amount <= 0 || amount > total - reserved) {
			throw new IllegalArgumentException("Invalid freeze amount: " + amount);
		}
		reserved += amount;
	}
	
	public void thaw(Integer amount) {
		if (amount <= 0 || amount > reserved) {
			throw new IllegalArgumentException("Invalid thaw amount: " + amount);
		}
		reserved -= amount;
	}
	
	public void deductFrozenStock(Integer amount) {
		if (amount <= 0 || amount > reserved) {
			throw new IllegalArgumentException("Invalid deduction amount: " + amount);
		}
		reserved -= amount;
		total -= amount;
	}

	public void increase(Integer amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Invalid increase amount: " + amount);
		}
		total += amount;
	}
	
	public void decrease(Integer amount) {
		if (amount <= 0 || amount > total) {
			throw new IllegalArgumentException("Invalid decrease amount: " + amount);
		}
		total -= amount;
	}
}
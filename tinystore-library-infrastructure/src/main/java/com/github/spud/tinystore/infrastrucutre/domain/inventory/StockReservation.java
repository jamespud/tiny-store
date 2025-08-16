package com.github.spud.tinystore.infrastrucutre.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "stock_reservation", schema = "inventory_db")
public class StockReservation {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@NotNull
	@Column(name = "sku_id", nullable = false)
	private UUID skuId;

	@NotNull
	@ColumnDefault("'00000000-0000-0000-0000-000000000001'")
	@Column(name = "location_id", nullable = false)
	private UUID locationId;

	@NotNull
	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@NotNull
	@Column(name = "expire_at", nullable = false)
	private OffsetDateTime expireAt;

	@Size(max = 100)
	@NotNull
	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

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
    @ColumnDefault("'PENDING'")
    @Column(name = "status", columnDefinition = "reservation_status not null")
    private Object status;
*/
}
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
@Table(name = "stock_movement", schema = "inventory_db")
public class StockMovement {

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
	@Column(name = "delta", nullable = false)
	private Integer delta;

	@Size(max = 255)
	@NotNull
	@Column(name = "reason", nullable = false)
	private String reason;

	@Column(name = "ref_id")
	private UUID refId;
	@NotNull
	@ColumnDefault("now()")
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

/*
 TODO [Reverse Engineering] create field to map the 'type' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @Column(name = "type", columnDefinition = "movement_type not null")
    private Object type;
*/
}
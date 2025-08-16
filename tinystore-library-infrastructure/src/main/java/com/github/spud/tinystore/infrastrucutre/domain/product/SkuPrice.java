package com.github.spud.tinystore.infrastrucutre.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "sku_price", schema = "product_db")
public class SkuPrice {

	@Id
	@Column(name = "sku_id", nullable = false)
	private UUID id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "sku_id", nullable = false)
	private ProductSku productSku;

	@NotNull
	@Column(name = "price", nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Size(max = 3)
	@NotNull
	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@NotNull
	@ColumnDefault("1")
	@Column(name = "price_version", nullable = false)
	private Integer priceVersion;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "effective_at", nullable = false)
	private OffsetDateTime effectiveAt;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}
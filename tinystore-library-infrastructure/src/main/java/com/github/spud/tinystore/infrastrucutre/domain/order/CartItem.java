package com.github.spud.tinystore.infrastrucutre.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "cart_item", schema = "order_db")
public class CartItem {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "cart_id", nullable = false)
	private Cart cart;

	@NotNull
	@Column(name = "sku_id", nullable = false)
	private UUID skuId;

	@NotNull
	@Column(name = "qty", nullable = false)
	private Integer qty;

	@Column(name = "price_snapshot", precision = 10, scale = 2)
	private BigDecimal priceSnapshot;

	@Size(max = 3)
	@Column(name = "currency", length = 3)
	private String currency;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "added_at", nullable = false)
	private OffsetDateTime addedAt;

}
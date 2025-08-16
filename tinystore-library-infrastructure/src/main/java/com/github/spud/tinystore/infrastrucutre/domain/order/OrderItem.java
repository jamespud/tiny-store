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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "order_item", schema = "order_db")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@NotNull
	@Column(name = "sku_id", nullable = false)
	private UUID skuId;

	@NotNull
	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Size(max = 255)
	@NotNull
	@Column(name = "product_name_snapshot", nullable = false)
	private String productNameSnapshot;

	@Size(max = 255)
	@NotNull
	@Column(name = "sku_title_snapshot", nullable = false)
	private String skuTitleSnapshot;

	@NotNull
	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Size(max = 3)
	@NotNull
	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@NotNull
	@Column(name = "price_version", nullable = false)
	private Integer priceVersion;

	@NotNull
	@Column(name = "qty", nullable = false)
	private Integer qty;

	@NotNull
	@Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal subtotalAmount;

	@NotNull
	@ColumnDefault("0")
	@Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal discountAmount;

	@NotNull
	@ColumnDefault("0")
	@Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal taxAmount;

	@NotNull
	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

}
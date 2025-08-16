package com.github.spud.tinystore.infrastrucutre.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "order", schema = "order_db")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Size(max = 3)
	@NotNull
	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@NotNull
	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount; // 订单总金额

	@NotNull
	@Column(name = "items_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal itemsAmount; // 商品总价

	@NotNull
	@ColumnDefault("0")
	@Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal discountAmount; // 折扣金额

	@NotNull
	@ColumnDefault("0")
	@Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal taxAmount; // 税费金额

	@NotNull
	@ColumnDefault("0")
	@Column(name = "shipping_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal shippingAmount; // 运费金额

	@NotNull
	@ColumnDefault("'NONE'")
	@Lob
	@Column(name = "payment_status", nullable = false)
	private String paymentStatus;

	@NotNull
	@Column(name = "shipping_address_json", nullable = false)
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> shippingAddressJson;

	@Column(name = "billing_address_json")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> billingAddressJson;

	@Column(name = "payment_intent_id")
	private UUID paymentIntentId;

	@Size(max = 100)
	@NotNull
	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

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

/*
 TODO [Reverse Engineering] create field to map the 'status' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @ColumnDefault("'PENDING'")
    @Column(name = "status", columnDefinition = "order_status not null")
    private Object status;
*/
}
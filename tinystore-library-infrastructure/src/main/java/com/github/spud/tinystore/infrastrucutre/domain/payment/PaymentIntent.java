package com.github.spud.tinystore.infrastrucutre.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "payment_intent", schema = "payment_db")
public class PaymentIntent {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@NotNull
	@Column(name = "amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Size(max = 3)
	@NotNull
	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Size(max = 50)
	@NotNull
	@Column(name = "provider", nullable = false, length = 50)
	private String provider;

	@Size(max = 255)
	@Column(name = "provider_client_secret")
	private String providerClientSecret;

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
    @ColumnDefault("'REQUIRES_PAYMENT'")
    @Column(name = "status", columnDefinition = "intent_status not null")
    private Object status;
*/
}
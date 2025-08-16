package com.github.spud.tinystore.infrastrucutre.domain.order;

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
@Table(name = "coupon", schema = "order_db")
public class Coupon {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@Size(max = 50)
	@NotNull
	@Column(name = "code", nullable = false, length = 50)
	private String code;

	@Column(name = "description", length = Integer.MAX_VALUE)
	private String description;

	@NotNull
	@Column(name = "discount", nullable = false, precision = 10, scale = 2)
	private BigDecimal discount;

	@Size(max = 20)
	@NotNull
	@Column(name = "type", nullable = false, length = 20)
	private String type;

	@Size(max = 20)
	@NotNull
	@ColumnDefault("'ACTIVE'")
	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@NotNull
	@Column(name = "start_at", nullable = false)
	private OffsetDateTime startAt;

	@NotNull
	@Column(name = "end_at", nullable = false)
	private OffsetDateTime endAt;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}
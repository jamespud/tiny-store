package com.github.spud.tinystore.infrastrucutre.domain.product;

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
@Table(name = "product_sku", schema = "product_db")
public class ProductSku {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Size(max = 50)
	@NotNull
	@Column(name = "sku_code", nullable = false, length = 50)
	private String skuCode;

	@Size(max = 255)
	@NotNull
	@Column(name = "title", nullable = false)
	private String title;

	@Size(max = 50)
	@Column(name = "barcode", length = 50)
	private String barcode;

	@Column(name = "weight", precision = 10, scale = 2)
	private BigDecimal weight;

	@Size(max = 50)
	@Column(name = "dimensions", length = 50)
	private String dimensions;

	@Size(max = 50)
	@Column(name = "tax_class", length = 50)
	private String taxClass;

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
    @ColumnDefault("'ENABLED'")
    @Column(name = "status", columnDefinition = "sku_status not null")
    private Object status;
*/
}
package com.github.spud.tinystore.infrastrucutre.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product", schema = "product_db")
public class Product {

	@Id
	@ColumnDefault("uuid_generate_v7_sql()")
	@Column(name = "id", nullable = false)
	private UUID id;

	@Size(max = 255)
	@NotNull
	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "brand_id")
	private UUID brandId;

	@Column(name = "category_id")
	private UUID categoryId;

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
    @Column(name = "status", columnDefinition = "product_status not null")
    private Object status;
*/
}
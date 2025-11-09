package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product_sku")
@Comment("库存单元（具体规格商品，如iPhone 15 128G 黑色）")
public class ProductSku {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("SKU ID")
	private Long skuId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "spu_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联SPU ID")
	private ProductSpu spu;

	@Column(nullable = false, length = 128)
	@Comment("规格值ID组合（逗号分隔，如\"1,5\"）")
	private String specValueIds;

	@Column(nullable = false, length = 128)
	@Comment("SKU名称（SPU名称+规格值）")
	private String skuName;

	@Column(nullable = false, precision = 10, scale = 2)
	@Comment("销售价（元）")
	private BigDecimal price;

	@Column(precision = 10, scale = 2)
	@Comment("市场价（划线价，元）")
	private BigDecimal marketPrice;

	@Column(nullable = false)
	@Comment("可售库存数量")
	private Integer stock;

	@Column(nullable = false)
	@Comment("锁定库存数量（下单未支付）")
	private Integer lockStock;

	@Column(length = 255)
	@Comment("SKU图片URL")
	private String imageUrl;

	@Column(length = 64)
	@Comment("商品条形码")
	private String barcode;

	@Column(nullable = false)
	@Comment("状态（0=禁用,1=正常）")
	private Integer status;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("创建时间")
	private LocalDateTime createTime;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("更新时间")
	private LocalDateTime updateTime;

	@PrePersist
	public void prePersist() {
		if (createTime == null) createTime = LocalDateTime.now();
		if (updateTime == null) updateTime = LocalDateTime.now();
	}

	@PreUpdate
	public void preUpdate() {
		updateTime = LocalDateTime.now();
	}
}
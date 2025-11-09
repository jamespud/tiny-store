package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "product_image")
@Comment("SPU/SKU的多图存储表（支持轮播）")
public class ProductImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("图片ID")
	private Long imageId;

	@Column(nullable = false)
	@Comment("关联类型（1=SPU,2=SKU）")
	private Integer refType;

	@Column(nullable = false)
	@Comment("关联ID（对应spu_id或sku_id）")
	private Long refId;

	@Column(nullable = false, length = 255)
	@Comment("图片URL")
	private String imageUrl;

	@Column(nullable = false)
	@Comment("排序权重（值越大越靠前）")
	private Integer sort;

	@Column(nullable = false)
	@Comment("是否主图（1=是,0=否）")
	private Integer isMain;
}
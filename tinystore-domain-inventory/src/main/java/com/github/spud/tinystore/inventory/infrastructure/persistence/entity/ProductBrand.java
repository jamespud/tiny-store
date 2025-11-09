package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "product_brand")
@Comment("商品品牌表（如苹果、华为）")
public class ProductBrand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("品牌ID")
	private Long brandId;

	@Column(nullable = false, length = 64)
	@Comment("品牌名称")
	private String brandName;

	@Column(nullable = false, length = 255)
	@Comment("品牌logo URL")
	private String logoUrl;

	@Column(length = 512)
	@Comment("品牌简介")
	private String brandDesc;

	@Column(length = 1)
	@Comment("品牌首字母（用于字母筛选）")
	private String firstLetter;

	@Column(nullable = false)
	@Comment("排序权重（值越大越靠前）")
	private Integer sort;

	@Column(nullable = false)
	@Comment("状态（0=禁用,1=启用）")
	private Integer status;
}
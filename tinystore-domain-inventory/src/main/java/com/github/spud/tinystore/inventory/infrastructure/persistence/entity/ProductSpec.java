package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "product_spec")
@Comment("商品规格名称表（如颜色、内存、尺码）")
public class ProductSpec {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("规格ID")
	private Long specId;

	@Column(nullable = false, length = 32)
	@Comment("规格名称（如颜色、内存）")
	private String specName;

	@Column(nullable = false)
	@Comment("排序权重（规格展示顺序）")
	private Integer sort;
}
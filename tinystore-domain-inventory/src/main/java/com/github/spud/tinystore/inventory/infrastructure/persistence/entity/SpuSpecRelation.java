package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "spu_spec_relation")
@Comment("SPU与规格的关联表（绑定商品包含的规格）")
public class SpuSpecRelation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("关联ID")
	private Long relationId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "spu_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联SPU ID")
	private ProductSpu spu;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "spec_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联规格ID")
	private ProductSpec spec;

	@Column(nullable = false)
	@Comment("该SPU下的规格展示顺序")
	private Integer sort;
}
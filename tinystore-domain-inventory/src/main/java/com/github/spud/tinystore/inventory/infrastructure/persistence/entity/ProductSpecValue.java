package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "product_spec_value")
@Comment("规格具体值表（如颜色=黑色、内存=128G）")
public class ProductSpecValue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("规格值ID")
	private Long specValueId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "spec_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联规格ID")
	private ProductSpec spec;

	@Column(nullable = false, length = 32)
	@Comment("规格值名称（如黑色、128G）")
	private String valueName;

	@Column(length = 255)
	@Comment("规格值图片（如色卡图）")
	private String valueImage;

	@Column(nullable = false)
	@Comment("排序权重（同规格下的展示顺序）")
	private Integer sort;
}
package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "product_category")
@Comment("商品多级分类表（平台统一维护）")
public class ProductCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("分类ID")
	private Long categoryId;

	@Column(nullable = false)
	@Comment("父分类ID（0为顶级）")
	private Long parentId;

	@Column(nullable = false, length = 64)
	@Comment("分类名称")
	private String categoryName;

	@Column(nullable = false)
	@Comment("分类层级（1-顶级,2-二级,3-三级）")
	private Integer level;

	@Column(nullable = false)
	@Comment("排序权重（值越大越靠前）")
	private Integer sort;

	@Column(length = 255)
	@Comment("分类图标URL")
	private String iconUrl;

	@Column(nullable = false)
	@Comment("是否叶子节点（1=是,0=否）")
	private Integer isLeaf;

	@Column(nullable = false)
	@Comment("状态（0=禁用,1=启用）")
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
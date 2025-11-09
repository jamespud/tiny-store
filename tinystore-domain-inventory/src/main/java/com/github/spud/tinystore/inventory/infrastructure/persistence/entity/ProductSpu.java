package com.github.spud.tinystore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product_spu")
@Comment("标准化产品单元（抽象商品信息）")
public class ProductSpu {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("SPU ID")
	private Long spuId;

//	@ManyToOne(fetch = FetchType.LAZY)
//	@JoinColumn(name = "merchant_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
//	@Comment("所属商家ID")
//	private MerchantCore merchant;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("所属分类ID")
	private ProductCategory category;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "brand_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("所属品牌ID")
	private ProductBrand brand;

	@Column(nullable = false, length = 128)
	@Comment("SPU名称")
	private String spuName;

	@Column(columnDefinition = "TEXT")
	@Comment("商品详情（富文本）")
	private String spuDesc;

	@Column(nullable = false, length = 255)
	@Comment("主图URL")
	private String mainImage;

	@Column(columnDefinition = "JSONB")
	@Comment("规格模板JSON（记录规格组合）")
	private String specJson; // 用String接收JSONB，需解析时可配合@Convert

	@Column(nullable = false)
	@Comment("审核状态（0=待审核,1=通过,2=驳回）")
	private Integer auditStatus;

	@Column(nullable = false)
	@Comment("销售状态（0=下架,1=上架）")
	private Integer saleStatus;

	@Column(length = 255)
	@Comment("驳回原因（仅审核状态为2时有效）")
	private String rejectReason;

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
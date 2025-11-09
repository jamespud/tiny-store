package com.github.spud.tinystore.account.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "merchant_shop")
@Comment("商家店铺展示信息，与商家主体资质分离")
public class MerchantShop {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("店铺ID")
	private Long shopId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "merchant_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联商家ID（级联删除）")
	private MerchantCore merchant;

	@Column(nullable = false, length = 64)
	@Comment("店铺名称（对外展示）")
	private String shopName;

	@Column(nullable = false, length = 255)
	@Comment("店铺logo URL")
	private String shopLogo;

	@Column(length = 512)
	@Comment("店铺简介（不超过500字）")
	private String shopDesc;

	@Column(nullable = false, length = 20)
	@Comment("店铺客服电话（展示给用户）")
	private String customerService;

	@Column(length = 128)
	@Comment("营业时间（如“9:00-22:00”）")
	private String businessHours;

	@Column(precision = 2, scale = 1)
	@Comment("店铺评分（1-5分）")
	private BigDecimal shopScore;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("信息更新时间")
	private LocalDateTime updateTime;

	@PrePersist
	public void prePersist() {
		if (updateTime == null) {
			updateTime = LocalDateTime.now();
		}
	}

	@PreUpdate
	public void preUpdate() {
		updateTime = LocalDateTime.now();
	}
}
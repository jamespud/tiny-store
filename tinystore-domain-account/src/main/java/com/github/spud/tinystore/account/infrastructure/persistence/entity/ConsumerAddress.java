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
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "consumer_address")
@Comment("普通用户收货地址表（中间4位脱敏/加密在应用层处理）")
public class ConsumerAddress {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("地址ID")
	private Long addrId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联用户ID（级联删除）")
	private UserCore user;

	@Column(nullable = false, length = 32)
	@Comment("收货人姓名")
	private String receiverName;

	@Column(nullable = false, length = 20)
	@Comment("收货人电话（建议加密存储）")
	private String receiverPhone;

	@Column(nullable = false, length = 32)
	@Comment("省份")
	private String province;

	@Column(nullable = false, length = 32)
	@Comment("城市")
	private String city;

	@Column(nullable = false, length = 32)
	@Comment("区/县")
	private String district;

	@Column(nullable = false, length = 255)
	@Comment("详细地址（街道/门牌号）")
	private String detailAddr;

	@Column(nullable = false)
	@Comment("是否默认地址：0-否；1-是（每用户仅一条）")
	private Integer isDefault;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("创建时间")
	private LocalDateTime createTime;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("更新时间")
	private LocalDateTime updateTime;

	@PrePersist
	public void prePersist() {
		if (createTime == null) {
			createTime = LocalDateTime.now();
		}
		if (updateTime == null) {
			updateTime = LocalDateTime.now();
		}
	}

	@PreUpdate
	public void preUpdate() {
		updateTime = LocalDateTime.now();
	}
}
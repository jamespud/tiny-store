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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "consumer_detail")
@Comment("普通用户消费相关信息（会员/积分/偏好）")
public class ConsumerDetail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("详情记录ID")
	private Long consumerId;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联用户ID（一对一，级联删除）")
	private UserCore user;

	@Column(nullable = false)
	@Comment("会员等级：0-普通；1-白银；2-黄金...")
	private Integer memberLevel;

	@Column(nullable = false, precision = 12, scale = 2)
	@Comment("累计消费金额（元）")
	private BigDecimal totalConsume;

	@Column(nullable = false)
	@Comment("积分余额")
	private Integer pointsBalance;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "default_addr_id")
	@Comment("默认收货地址ID（关联地址表）")
	private ConsumerAddress defaultAddr;

	@Column(length = 255)
	@Comment("消费偏好标签（逗号分隔）")
	private String preferenceTags;

	@Column(columnDefinition = "TIMESTAMPTZ")
	@Comment("最后下单时间")
	private LocalDateTime lastPurchaseTime;
}
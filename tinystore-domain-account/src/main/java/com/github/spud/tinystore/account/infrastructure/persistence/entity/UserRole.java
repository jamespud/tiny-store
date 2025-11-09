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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "user_role")
@Comment("用户与角色关联表（支持一用户多角色与商家内角色）")
public class UserRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("关联记录ID")
	private Long urId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联用户ID（级联删除）")
	private UserCore user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联角色ID")
	private RoleDict role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "merchant_id", foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("所属商家ID（仅商家角色需填）")
	private MerchantCore merchant;

	@Column(nullable = false)
	@Comment("创建者用户ID（子账号由主账号创建）")
	private Long createUserId;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("角色绑定时间")
	private LocalDateTime createTime;

	@Column(nullable = false)
	@Comment("角色状态：0-禁用；1-启用")
	private Integer roleStatus;

	@PrePersist
	public void prePersist() {
		if (createTime == null) {
			createTime = LocalDateTime.now();
		}
	}
}
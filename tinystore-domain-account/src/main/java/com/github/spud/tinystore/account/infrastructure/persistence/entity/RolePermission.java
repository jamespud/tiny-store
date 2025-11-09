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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(
	name = "role_permission",
	uniqueConstraints = {@UniqueConstraint(name = "idx_role_permission_unique", columnNames = {"roleId", "permId"})}
)
@Comment("角色预设权限，子账号继承后可微调")
public class RolePermission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("关联记录ID")
	private Long rpId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联角色ID（级联删除）")
	private RoleDict role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "perm_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联权限ID（级联删除）")
	private PermissionDict permission;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("权限绑定时间")
	private LocalDateTime createTime;

	@PrePersist
	public void prePersist() {
		if (createTime == null) {
			createTime = LocalDateTime.now();
		}
	}
}
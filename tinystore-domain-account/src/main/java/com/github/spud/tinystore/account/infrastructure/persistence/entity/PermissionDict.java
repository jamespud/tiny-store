package com.github.spud.tinystore.account.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "permission_dict")
@Comment("权限字典，支持权限树结构")
public class PermissionDict {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("权限ID")
	private Integer permId;

	@Column(nullable = false, length = 64)
	@Comment("权限名称")
	private String permName;

	@Column(nullable = false, length = 64)
	@Comment("权限编码（前端/后端判定用）")
	private String permCode;

	@Column(nullable = false)
	@Comment("权限类型：1-菜单；2-按钮；3-接口")
	private Integer permType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_perm_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
	@Comment("父权限ID（0表示顶级）")
	private PermissionDict parentPerm;

	@Column(nullable = false)
	@Comment("是否有效：0-废弃；1-在用")
	private Integer isValid;
}
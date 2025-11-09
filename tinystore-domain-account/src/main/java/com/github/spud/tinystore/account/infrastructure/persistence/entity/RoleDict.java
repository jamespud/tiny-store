package com.github.spud.tinystore.account.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "role_dict")
@Comment("角色字典表（如普通用户/商家主账号/运营/客服/财务/仓储）")
public class RoleDict {

	@Id
	@Comment("角色ID（1-普通用户；2-商家主账号...）")
	private Integer roleId;

	@Column(nullable = false, length = 32)
	@Comment("角色名称")
	private String roleName;

	@Column(length = 255)
	@Comment("角色描述")
	private String roleDesc;

	@Column(nullable = false)
	@Comment("是否有效：0-废弃；1-在用")
	private Integer isValid;
}
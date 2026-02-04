package com.github.spud.tinystore.account.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "user_core")
@Comment("所有用户的核心表（普通用户/商家人员共享）")
public class UserCore {

	@Id
	@Column(name = "user_id", length = 64, nullable = false)
	@Comment("用户唯一ID（支持雪花等分布式ID）")
	private String userId;

	@Column(nullable = false, length = 64)
	@Comment("登录账号（手机号/邮箱/抖音号/第三方映射）")
	private String account;

	@Column(nullable = false, length = 128)
	@Comment("bcrypt等哈希存储（禁止明文）")
	private String password;

	@Column(nullable = false, length = 32)
	@Comment("用户昵称")
	private String nickname;

	@Column(length = 255)
	@Comment("头像URL")
	private String avatarUrl;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("注册时间")
	private LocalDateTime registerTime;

	@Column(columnDefinition = "TIMESTAMPTZ")
	@Comment("最后登录时间")
	private LocalDateTime lastLoginTime;

	@Column(length = 32)
	@Comment("最后登录IP")
	private String loginIp;

	@Column(nullable = false)
	@Comment("账号状态：0-禁用；1-正常；2-待验证(新注册未实名)")
	private Integer accountStatus;

	@Column(nullable = false)
	@Comment("逻辑删除：0-未删；1-已删")
	private Integer isDelete;

	@Column(nullable = false)
	@Comment("凭证版本：改密/冻结等变更递增")
	private Long credentialVersion;

	@Column(columnDefinition = "JSONB")
	@JdbcTypeCode(SqlTypes.JSON)
	@Comment("扩展字段（设备/端信息等）")
	private String extJson; // 用String接收JSONB，如需解析可配合@Convert

	// 初始化注册时间默认值
	@PrePersist
	public void prePersist() {
		if (registerTime == null) {
			registerTime = LocalDateTime.now();
		}
		if (credentialVersion == null) {
			credentialVersion = 1L;
		}
	}
}

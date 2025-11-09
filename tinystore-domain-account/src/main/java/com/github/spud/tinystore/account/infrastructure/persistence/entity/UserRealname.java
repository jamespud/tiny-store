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
@Table(name = "user_realname")
@Comment("用户实名认证记录，敏感信息隔离存储")
public class UserRealname {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("认证记录ID")
	private Long realnameId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
	@Comment("关联用户ID（级联删除）")
	private UserCore user;

	@Column(nullable = false, length = 32)
	@Comment("真实姓名（建议加密存储）")
	private String realName;

	@Column(nullable = false, length = 64)
	@Comment("身份证号（建议加密/脱敏存储）")
	private String idCard;

	@Column(nullable = false, length = 255)
	@Comment("身份证正面照URL")
	private String idCardFrontUrl;

	@Column(nullable = false, length = 255)
	@Comment("身份证反面照URL")
	private String idCardBackUrl;

	@Column(nullable = false)
	@Comment("认证状态：0-待审核；1-通过；2-驳回")
	private Integer authStatus;

	@Column(columnDefinition = "TIMESTAMPTZ")
	@Comment("审核通过时间")
	private LocalDateTime authTime;

	@Column(length = 255)
	@Comment("驳回原因（仅状态为2时有效）")
	private String rejectReason;

	@Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
	@Comment("创建时间")
	private LocalDateTime createTime;

	@PrePersist
	public void prePersist() {
		if (createTime == null) {
			createTime = LocalDateTime.now();
		}
	}
}
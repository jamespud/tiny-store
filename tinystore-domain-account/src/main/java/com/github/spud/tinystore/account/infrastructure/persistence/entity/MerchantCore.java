package com.github.spud.tinystore.account.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "merchant_core")
@Comment("商家主体资质信息（企业/个体工商户）")
public class MerchantCore {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("商家唯一ID")
	private Long merchantId;

	@Column(nullable = false, length = 64)
	@Comment("商家名称（与营业执照一致）")
	private String merchantName;

	@Column(nullable = false)
	@Comment("商家类型：1-企业；2-个体工商户；3-旗舰店")
	private Integer merchantType;

	@Column(nullable = false, length = 64)
	@Comment("营业执照编号（唯一验证用）")
	private String businessLicense;

	@Column(nullable = false, length = 255)
	@Comment("营业执照照片URL")
	private String licensePicUrl;

	@Column(nullable = false, length = 32)
	@Comment("法定代表人姓名（建议加密存储）")
	private String legalPerson;

	@Column(nullable = false, length = 20)
	@Comment("法人电话（建议加密存储）")
	private String legalPhone;

	@Column(nullable = false, length = 255)
	@Comment("注册地址（与营业执照一致）")
	private String registerAddr;

	@Column(nullable = false, length = 512)
	@Comment("经营范围（限制商品类目）")
	private String businessScope;

	@Column(nullable = false, length = 64)
	@Comment("结算银行名称")
	private String settleBank;

	@Column(nullable = false, length = 64)
	@Comment("结算银行卡号（建议加密存储）")
	private String settleCardNo;

	@Column(nullable = false, length = 32)
	@Comment("结算账户名")
	private String settleAccountName;

	@Column(nullable = false)
	@Comment("审核状态：0-待提交；1-审核中；2-通过；3-驳回")
	private Integer auditStatus;

	@Column(columnDefinition = "TIMESTAMPTZ")
	@Comment("审核通过时间")
	private LocalDateTime auditTime;

	@Column(length = 255)
	@Comment("驳回原因（仅状态为3时有效）")
	private String rejectReason;

	@Column(columnDefinition = "TIMESTAMPTZ")
	@Comment("店铺开通时间（审核通过后生效）")
	private LocalDateTime openTime;

	@Column(nullable = false)
	@Comment("是否营业：0-停业；1-营业")
	private Integer isOperate;
}
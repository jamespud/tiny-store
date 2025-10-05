package com.github.spud.tinystore.product.domain.model.valueobject;

/**
 * 内容审核状态
 */
public enum ContentStatus {
	DRAFT,           // 草稿
	PENDING_REVIEW,  // 待审核
	APPROVED,        // 已审核通过
	REJECTED         // 已驳回
}

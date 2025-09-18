package com.github.spud.tinystore.product.domain.model.aggregate;

import com.github.spud.tinystore.product.domain.model.valueobject.AfterSalePolicy;
import com.github.spud.tinystore.product.domain.model.valueobject.ContentStatus;
import com.github.spud.tinystore.product.domain.model.valueobject.RichText;

import java.util.List;

public class ProductContent {
    private String contentId;          // 领域标识
    private String productId;          // 关联商品
    private RichText detail;              // 详情页富文本（值对象）
    private RichText description;         // 商品简介
    private List<AfterSalePolicy> afterSalePolicies; // 售后政策
    private Integer version;       // 版本号（用于并发控制）
    private ContentStatus status;         // 状态（编辑中/已审核/已驳回）

    // 领域行为：更新详情页（自动升级版本）
    public void updateDetail(RichText newDetail) {
        this.detail = newDetail;
        this.version = this.version.next(); // 版本自增（如V1→V2）
        this.status = ContentStatus.PENDING_REVIEW; // 需重新审核
    }

    // 领域行为：审核通过
    public void approve() {
        this.status = ContentStatus.APPROVED;
        DomainEventPublisher.publish(new ContentApprovedEvent(this.productId, this.version));
    }
}
package com.github.spud.tinystore.product.domain.model.aggregate;

import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductStatus;

import java.time.LocalDateTime;
import java.util.List;

public class ProductStatusFlow {
    private FlowId flowId;                // 领域标识
    private ProductId productId;          // 关联商品
    private ProductStatus currentStatus;  // 当前状态
    private List<StatusRecord> history;   // 状态变更历史（值对象）

    // 领域行为：提交审核（从草稿→审核中）
    public void submitForReview(Operator operator) {
        if (this.currentStatus != ProductStatus.DRAFT) {
            throw new InvalidStateException("仅草稿状态可提交审核");
        }
        this.currentStatus = ProductStatus.UNDER_REVIEW;
        this.history.add(new StatusRecord(
                ProductStatus.DRAFT, ProductStatus.UNDER_REVIEW,
                operator, LocalDateTime.now(), "提交审核"
        ));
        DomainEventPublisher.publish(new ProductSubmittedEvent(this.productId));
    }

    // 领域行为：审核通过（从审核中→上架）
    public void passReview(Operator operator, String comment) {
        if (this.currentStatus != ProductStatus.UNDER_REVIEW) {
            throw new InvalidStateException("仅审核中状态可通过");
        }
        this.currentStatus = ProductStatus.ONLINE;
        this.history.add(new StatusRecord(
                ProductStatus.UNDER_REVIEW, ProductStatus.ONLINE,
                operator, LocalDateTime.now(), comment
        ));
        DomainEventPublisher.publish(new ProductApprovedEvent(this.productId));
    }
}
package com.github.spud.tinystore.product.domain.model.aggregate;

import com.github.spud.tinystore.product.domain.model.valueobject.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Sku {
    private String skuId;                   // 领域标识（值对象）
    private String productId;           // 关联商品
    private SpecificationCombination specs; // 规格组合（值对象，如["颜色=红","尺寸=XL"]）
    private String barCode;                // 条形码
    private SkuAttributePack attributes;   // SKU专属属性（如重量、体积）
    private SkuStatus status;              // 状态（在售/下架）
    private LocalDateTime createTime;

    // 领域行为：创建SKU（依赖商品存在且规格组合唯一）
    public static Sku create(String skuId, String productId,
                             SpecificationCombination specs, String barCode) {
        // 校验规格组合非空且不重复（依赖规格上下文的领域服务）
        SpecificationValidator.validateUnique(specs, productId);

        Sku sku = new Sku();
        sku.skuId = skuId;
        sku.productId = productId;
        sku.specs = specs;
        sku.barCode = barCode;
        sku.status = SkuStatus.AVAILABLE; // 初始状态为可用
        sku.createTime = LocalDateTime.now();
        return sku;
    }

    // 领域行为：禁用SKU（触发事件，通知库存/搜索服务）
    public void disable() {
        this.status = SkuStatus.DISABLED;
        DomainEventPublisher.publish(new SkuDisabledEvent(this.skuId, this.productId));
    }
}
package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.PackageStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * FulfillmentPackage 聚合根 - 履约包裹
 * 
 * 职责：
 * - 管理包裹发货/签收状态
 * - 关联订单（通过中间表）
 */
@Getter
@Builder
public class FulfillmentPackage {
    
    private Long id;
    private String packageId;
    private String tradeId;
    
    private PackageStatus packageStatus;
    
    private String waybillNo;
    private String logisticsCompany;
    
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * 发货
     */
    public void ship(String waybillNo, String logisticsCompany) {
        if (this.packageStatus != PackageStatus.CREATED) {
            throw new IllegalStateException("Package must be CREATED to ship: " + packageId);
        }
        this.packageStatus = PackageStatus.SHIPPED;
        this.waybillNo = waybillNo;
        this.logisticsCompany = logisticsCompany;
        this.shippedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 签收
     */
    public void deliver() {
        if (this.packageStatus != PackageStatus.SHIPPED) {
            throw new IllegalStateException("Package must be SHIPPED to deliver: " + packageId);
        }
        this.packageStatus = PackageStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}

package com.github.spud.tinystore.order.application.command;

import com.github.spud.tinystore.order.domain.model.Address;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.status.AfterSaleCaseType;
import com.github.spud.tinystore.order.domain.status.AfterSaleScope;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 申请售后命令
 * 
 * 支持行级/部分数量的精细化售后申请
 */
@Data
@Builder
public class ApplyAfterSaleCommand {
    
    /**
     * 订单ID（必填）
     */
    private String orderId;
    
    /**
     * 买家ID（必填）
     */
    private String buyerId;
    
    /**
     * 售后类型：退款或换货（必填）
     */
    private AfterSaleCaseType caseType;
    
    /**
     * 售后范围：当前限制为行级（必填）
     */
    private AfterSaleScope scope;
    
    /**
     * 售后商品明细（必填，非空）
     */
    private List<Item> items;
    
    /**
     * 申请退款金额（可选，为空时按规则计算上限）
     */
    private Money requestedAmount;
    
    /**
     * 售后原因代码（必填）
     */
    private String reasonCode;
    
    /**
     * 售后原因描述（必填）
     */
    private String reasonText;
    
    /**
     * @deprecated 保留字段，向后兼容
     * 使用 reasonCode 和 reasonText 替代
     */
    @Deprecated(since = "2025-09-22", forRemoval = true)
    private String reason;
    
    /**
     * 证据材料URL列表（可选）
     */
    private List<String> evidenceUrls;
    
    /**
     * 退货物流信息（可选）
     */
    private ReturnLogisticsInfo returnLogistics;
    
    /**
     * 幂等键（必填）
     */
    private String idempotencyKey;
    
    /**
     * 售后商品明细项
     */
    @Data
    @Builder
    public static class Item {
        /**
         * 订单行ID（必填）
         */
        private String lineId;
        
        /**
         * 申请售后数量（必填，正整数）
         */
        private int requestedQty;
    }
    
    /**
     * 退货物流信息
     */
    @Data
    @Builder
    public static class ReturnLogisticsInfo {
        /**
         * 承运商
         */
        private String carrier;
        
        /**
         * 物流单号
         */
        private String trackingNo;
        
        /**
         * 退货地址快照
         */
        private Address addressSnapshot;
    }
}
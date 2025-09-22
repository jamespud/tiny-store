package com.github.spud.tinystore.order.domain.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbox 实体 - 用于确保事件发布的事务一致性
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {
    
    /**
     * 事件唯一ID（幂等键）
     */
    private UUID eventId;
    
    /**
     * 事件类型
     */
    private String eventType;
    
    /**
     * 聚合根ID（订单ID）
     */
    private UUID aggregateId;
    
    /**
     * 聚合根类型
     */
    private String aggregateType;
    
    /**
     * 事件载荷（JSON）
     */
    private String payload;
    
    /**
     * 事件时间戳
     */
    private Instant occurredAt;
    
    /**
     * 发布状态
     */
    private PublishStatus status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 下次重试时间
     */
    private Instant nextRetryAt;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private Instant createdAt;
    
    /**
     * 发布时间
     */
    private Instant publishedAt;
    
    /**
     * 发布状态枚举
     */
    public enum PublishStatus {
        PENDING,    // 待发布
        PUBLISHED,  // 已发布
        FAILED      // 发布失败
    }
}
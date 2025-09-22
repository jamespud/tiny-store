package com.github.spud.tinystore.order.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.github.spud.tinystore.order.domain.model.Outbox;

/**
 * Outbox 仓储接口
 * 
 * @author Spud
 * @date 2025/9/22
 */
public interface OutboxRepository {
    
    /**
     * 保存 Outbox 事件
     * 
     * @param outbox Outbox 实体
     */
    void save(Outbox outbox);
    
    /**
     * 查找待发布的事件
     * 
     * @param limit 数量限制
     * @return 待发布事件列表
     */
    List<Outbox> findPendingEvents(int limit);
    
    /**
     * 查找需要重试的事件
     * 
     * @param beforeTime 重试时间之前
     * @param limit 数量限制
     * @return 需要重试的事件列表
     */
    List<Outbox> findEventsForRetry(Instant beforeTime, int limit);
    
    /**
     * 更新事件状态
     * 
     * @param eventId 事件ID
     * @param status 新状态
     * @param retryCount 重试次数
     * @param nextRetryAt 下次重试时间
     * @param errorMessage 错误信息
     */
    void updateStatus(UUID eventId, Outbox.PublishStatus status, 
                     Integer retryCount, Instant nextRetryAt, String errorMessage);
    
    /**
     * 标记事件为已发布
     * 
     * @param eventId 事件ID
     */
    void markAsPublished(UUID eventId);
    
    /**
     * 删除已发布的老事件（清理任务）
     * 
     * @param beforeTime 时间之前
     * @return 删除的数量
     */
    int deletePublishedEventsBefore(Instant beforeTime);
}
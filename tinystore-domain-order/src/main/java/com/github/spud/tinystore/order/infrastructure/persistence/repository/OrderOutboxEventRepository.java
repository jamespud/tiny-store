package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 事件数据访问接口
 */
@Repository
public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEventPO, UUID> {

	/**
	 * 查询待发送的事件
	 */
	@Query("SELECT e FROM OrderOutboxEventPO e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
	List<OrderOutboxEventPO> findPendingEvents();

	/**
	 * 查询待发送的事件 (分页)
	 */
	@Query("SELECT e FROM OrderOutboxEventPO e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC LIMIT :limit")
	List<OrderOutboxEventPO> findPendingEvents(@Param("limit") int limit);

	/**
	 * 查询重试失败的事件
	 */
	@Query("SELECT e FROM OrderOutboxEventPO e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetries ORDER BY e.updatedAt ASC")
	List<OrderOutboxEventPO> findRetryableEvents(@Param("maxRetries") int maxRetries);

	/**
	 * 批量更新事件状态
	 */
	@Modifying
	@Query("UPDATE OrderOutboxEventPO e SET e.status = :status, e.updatedAt = :updatedAt WHERE e.id IN :ids")
	void updateStatusBatch(@Param("ids") List<UUID> ids,
	                       @Param("status") OrderOutboxEventPO.OutboxEventStatus status,
	                       @Param("updatedAt") OffsetDateTime updatedAt);

	/**
	 * 增加重试次数
	 */
	@Modifying
	@Query("UPDATE OrderOutboxEventPO e SET e.retryCount = e.retryCount + 1, e.updatedAt = :updatedAt WHERE e.id = :id")
	void incrementRetryCount(@Param("id") UUID id, @Param("updatedAt") OffsetDateTime updatedAt);

	/**
	 * 根据订单号查询事件
	 */
	List<OrderOutboxEventPO> findByOrderNoOrderByCreatedAtDesc(String orderNo);

	/**
	 * 根据追踪ID查询事件
	 */
	List<OrderOutboxEventPO> findByTraceIdOrderByCreatedAtDesc(String traceId);

	/**
	 * 清理过期的已完成事件 (保留N天)
	 */
	@Modifying
	@Query("DELETE FROM OrderOutboxEventPO e WHERE e.status = 'SENT' AND e.updatedAt < :cutoffTime")
	void cleanCompletedEvents(@Param("cutoffTime") OffsetDateTime cutoffTime);
}
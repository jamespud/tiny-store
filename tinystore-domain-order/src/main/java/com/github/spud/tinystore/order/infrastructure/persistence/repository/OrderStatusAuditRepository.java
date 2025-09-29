package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderStatusAuditPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单状态审计数据访问接口
 */
@Repository
public interface OrderStatusAuditRepository extends JpaRepository<OrderStatusAuditPO, UUID> {

	/**
	 * 根据订单号查询审计记录
	 */
	List<OrderStatusAuditPO> findByOrderNoOrderByCreatedAtDesc(String orderNo);

	/**
	 * 根据追踪ID查询审计记录
	 */
	List<OrderStatusAuditPO> findByTraceIdOrderByCreatedAtDesc(String traceId);

	/**
	 * 根据操作者查询审计记录
	 */
	List<OrderStatusAuditPO> findByActorTypeAndActorIdOrderByCreatedAtDesc(
		OrderStatusAuditPO.ActorType actorType, String actorId);

	/**
	 * 查询时间范围内的审计记录
	 */
	@Query("SELECT a FROM OrderStatusAuditPO a WHERE a.orderNo = :orderNo " +
		"AND a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
	List<OrderStatusAuditPO> findByOrderNoAndTimeRange(
		@Param("orderNo") String orderNo,
		@Param("startTime") OffsetDateTime startTime,
		@Param("endTime") OffsetDateTime endTime);

	/**
	 * 统计指定时间段内的状态变更次数
	 */
	@Query("SELECT COUNT(a) FROM OrderStatusAuditPO a WHERE a.orderNo = :orderNo " +
		"AND a.createdAt >= :since")
	Long countStatusChangesSince(@Param("orderNo") String orderNo,
	                             @Param("since") OffsetDateTime since);

	/**
	 * 查询订单的最后一次状态变更
	 */
	@Query("SELECT a FROM OrderStatusAuditPO a WHERE a.orderNo = :orderNo " +
		"ORDER BY a.createdAt DESC LIMIT 1")
	OrderStatusAuditPO findLastStatusChange(@Param("orderNo") String orderNo);
}
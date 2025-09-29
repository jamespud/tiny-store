package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderIdempotencyPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单幂等性控制数据访问接口
 */
@Repository
public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotencyPO, String> {

	/**
	 * 根据订单号和操作类型查询幂等性记录
	 */
	List<OrderIdempotencyPO> findByOrderNoAndOperation(String orderNo, String operation);

	/**
	 * 查询指定状态的幂等性记录
	 */
	List<OrderIdempotencyPO> findByStatusOrderByCreatedAtDesc(
		OrderIdempotencyPO.IdempotencyStatus status);

	/**
	 * 根据追踪ID查询幂等性记录
	 */
	List<OrderIdempotencyPO> findByTraceIdOrderByCreatedAtDesc(String traceId);

	/**
	 * 查询处理中的超时记录
	 */
	@Query("SELECT i FROM OrderIdempotencyPO i WHERE i.status = 'PROCESSING' " +
		"AND i.createdAt < :timeoutThreshold")
	List<OrderIdempotencyPO> findTimeoutProcessingRecords(
		@Param("timeoutThreshold") OffsetDateTime timeoutThreshold);

	/**
	 * 清理过期的幂等性记录
	 */
	@Modifying
	@Query("DELETE FROM OrderIdempotencyPO i WHERE i.expiresAt < :now")
	void cleanExpiredRecords(@Param("now") OffsetDateTime now);

	/**
	 * 更新处理状态和响应数据
	 */
	@Modifying
	@Query("UPDATE OrderIdempotencyPO i SET i.status = :status, i.responseData = :responseData " +
		"WHERE i.requestId = :requestId")
	void updateStatusAndResponse(@Param("requestId") String requestId,
	                             @Param("status") OrderIdempotencyPO.IdempotencyStatus status,
	                             @Param("responseData") String responseData);

	/**
	 * 检查幂等键是否存在且未过期
	 */
	@Query("SELECT i FROM OrderIdempotencyPO i WHERE i.requestId = :requestId " +
		"AND i.expiresAt > :now")
	Optional<OrderIdempotencyPO> findValidIdempotencyRecord(@Param("requestId") String requestId,
	                                                        @Param("now") OffsetDateTime now);
}
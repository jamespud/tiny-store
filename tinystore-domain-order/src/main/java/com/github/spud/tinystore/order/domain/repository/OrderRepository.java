package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.infrastrucutre.domain.order.Order;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * @author Spud
 * @date 2025/8/13
 */
public interface OrderRepository extends CrudRepository<Order, UUID> {

	/**
	 * 根据ID和用户ID查询订单
	 *
	 * @param id     订单ID
	 * @param userId 用户ID
	 * @return 订单对象
	 */
	Optional<Order> findByIdAndUserId(UUID id, UUID userId);

	/**
	 * 使用乐观锁更新订单状态
	 *
	 * @param id              订单ID
	 * @param expectedVersion 期望的版本号
	 * @param newStatus       新状态
	 * @param updatedAt       更新时间
	 * @return 更新的行数
	 */
	@Modifying
	@Query("UPDATE Order o SET o.paymentStatus = :newStatus, o.version = o.version + 1, o.updatedAt = :updatedAt WHERE o.id = :id AND o.version = :expectedVersion")
	int updateStatusWithVersion(@Param("id") UUID id,
		@Param("expectedVersion") Integer expectedVersion,
		@Param("newStatus") String newStatus,
		@Param("updatedAt") OffsetDateTime updatedAt);

	/**
	 * 取消订单：基于版本与当前状态（主状态字段）
	 */
	@Modifying
	@Query(
		"UPDATE Order o SET o.orderStatus = :targetStatus, o.cancelReason=:reason, o.cancelTime=:cancelTime, o.version = o.version + 1, o.updatedAt = :updatedAt "
			+
			"WHERE o.id = :id AND o.version = :expectedVersion AND o.orderStatus = :expectedStatus")
	int cancelWithVersion(@Param("id") UUID id,
		@Param("expectedVersion") Integer expectedVersion,
		@Param("expectedStatus") String expectedStatus,
		@Param("targetStatus") String targetStatus,
		@Param("reason") String reason,
		@Param("cancelTime") OffsetDateTime cancelTime,
		@Param("updatedAt") OffsetDateTime updatedAt);

}

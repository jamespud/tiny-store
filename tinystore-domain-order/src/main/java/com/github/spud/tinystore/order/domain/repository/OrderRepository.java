package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.event.OutboxEventEnvelope;
import com.github.spud.tinystore.order.domain.model.OrderAggregate;
import com.github.spud.tinystore.order.domain.model.OrderItem;
import com.github.spud.tinystore.order.domain.model.MainOrder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Order Repository with CRUD operations and optimistic locking
 *
 * @author Spud
 * @date 2025/9/3
 */
@Repository
public interface OrderRepository {

	/**
	 * Find order by ID
	 *
	 * @param orderId Order ID
	 * @return Optional order, empty if not found
	 */
	Optional<OrderItem> findById(String orderId);

	/**
	 * Save order (create or update)
	 * Uses optimistic locking based on version field
	 *
	 * @param order Order to save
	 * @return Saved order with updated version
	 * @throws OptimisticLockException if version conflict occurs
	 */
	OrderItem save(OrderItem order);

	/**
	 * Save order with outbox events in single transaction
	 * This is the primary method for aggregate persistence with events
	 *
	 * @param order     Order to save
	 * @param envelopes Outbox envelopes to publish
	 * @return Saved order with updated version
	 * @throws OptimisticLockException if version conflict occurs
	 */
	OrderAggregate saveWithOutbox(OrderAggregate order, List<OutboxEventEnvelope> envelopes);

	/**
	 * Check if order exists
	 *
	 * @param orderId Order ID
	 * @return true if order exists
	 */
	boolean exists(String orderId);

	/**
	 * Find orders by buyer ID (for query scenarios)
	 *
	 * @param buyerId Buyer ID
	 * @param limit   Maximum number of orders to return
	 * @return List of orders
	 */
	List<OrderItem> findByBuyerId(String buyerId, int limit);

	/**
	 * Find orders by status (for operational queries)
	 *
	 * @param status Core flow status to filter by
	 * @param limit  Maximum number of orders to return
	 * @return List of orders
	 */
	List<OrderItem> findByStatus(String status, int limit);

	void save(MainOrder mainOrder);

	/**
	 * Exception thrown when optimistic locking fails
	 */
	class OptimisticLockException extends RuntimeException {
		private final String orderId;
		private final Integer expectedVersion;
		private final Integer actualVersion;

		public OptimisticLockException(String orderId, Integer expectedVersion, Integer actualVersion) {
			super(String.format("Optimistic lock failed for order %s: expected version %d, actual version %d",
				orderId, expectedVersion, actualVersion));
			this.orderId = orderId;
			this.expectedVersion = expectedVersion;
			this.actualVersion = actualVersion;
		}

		public String getOrderId() {
			return orderId;
		}

		public Integer getExpectedVersion() {
			return expectedVersion;
		}

		public Integer getActualVersion() {
			return actualVersion;
		}
	}
}

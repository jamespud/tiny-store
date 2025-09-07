package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.infrastructure.domain.order.OrderOutbox;
import com.github.spud.tinystore.infrastructure.domain.order.OrderOutbox.Status;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * @author Spud
 * @date 2025/8/17
 */
public interface OrderOutBoxRepository extends JpaRepository<OrderOutbox, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<OrderOutbox> findPendingEventsWithLock(
		Status status,
		LocalDateTime now,
		org.springframework.data.domain.Pageable pageable
	);
}

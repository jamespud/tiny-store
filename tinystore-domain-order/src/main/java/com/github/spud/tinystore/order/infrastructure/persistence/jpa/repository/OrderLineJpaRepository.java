package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderLineEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * OrderLine JPA Repository
 */
@Repository
public interface OrderLineJpaRepository extends JpaRepository<OrderLineEntity, Long> {

    List<OrderLineEntity> findByOrderId(String orderId);
}

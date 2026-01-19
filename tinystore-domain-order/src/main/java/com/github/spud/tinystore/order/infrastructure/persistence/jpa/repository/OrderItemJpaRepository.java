package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, Long> {

	List<OrderItemEntity> findByMainOrderNoOrderByLineNoAsc(String mainOrderNo);

	List<OrderItemEntity> findBySubOrderNoOrderByLineNoAsc(String subOrderNo);
}


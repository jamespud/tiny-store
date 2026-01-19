package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderSubEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSubJpaRepository extends JpaRepository<OrderSubEntity, Long> {

	Optional<OrderSubEntity> findBySubOrderNo(String subOrderNo);

	List<OrderSubEntity> findByMainOrderNo(String mainOrderNo);
}


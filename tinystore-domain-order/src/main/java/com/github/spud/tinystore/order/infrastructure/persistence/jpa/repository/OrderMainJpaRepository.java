package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderMainEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface OrderMainJpaRepository extends JpaRepository<OrderMainEntity, Long> {

	Optional<OrderMainEntity> findByOrderNo(String orderNo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM OrderMainEntity m WHERE m.orderNo = :orderNo")
	Optional<OrderMainEntity> findForUpdateByOrderNo(@Param("orderNo") String orderNo);
}


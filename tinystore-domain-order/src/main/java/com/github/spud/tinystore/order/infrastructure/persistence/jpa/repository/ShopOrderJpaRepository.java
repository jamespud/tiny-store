package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ShopOrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * ShopOrder JPA Repository
 */
@Repository
public interface ShopOrderJpaRepository extends JpaRepository<ShopOrderEntity, Long> {

    Optional<ShopOrderEntity> findByOrderId(String orderId);

    @Query("SELECT o FROM ShopOrderEntity o WHERE o.tradeId = :tradeId ORDER BY o.id ASC")
    List<ShopOrderEntity> findByTradeId(@Param("tradeId") String tradeId);

    List<ShopOrderEntity> findBySellerId(String sellerId);

    @Query("SELECT o FROM ShopOrderEntity o WHERE o.tradeId = :tradeId AND o.orderStatus = :orderStatus")
    List<ShopOrderEntity> findByTradeIdAndOrderStatus(@Param("tradeId") String tradeId, @Param("orderStatus") String orderStatus);

    @Query("SELECT o FROM ShopOrderEntity o WHERE o.orderStatus = :orderStatus AND o.createdAt < :threshold")
    List<ShopOrderEntity> findPendingReceiveByTimeout(@Param("orderStatus") String orderStatus, @Param("threshold") LocalDateTime threshold);
}

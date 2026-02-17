package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;

@Repository
public interface JpaCheckoutQuoteRepository extends JpaRepository<CheckoutQuoteEntity, UUID> {

	@Query("SELECT q FROM CheckoutQuoteEntity q WHERE q.status = 'QUOTED' AND q.expiresAt < :now")
	List<CheckoutQuoteEntity> findExpiredQuoted(@Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE CheckoutQuoteEntity q SET q.status = :to, q.updatedAt = :now WHERE q.id = :id AND q.status = :from")
	int updateStatus(@Param("id") UUID id, @Param("from") String from, @Param("to") String to,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE CheckoutQuoteEntity q SET q.status = 'COMMITTED', q.tradeId = :tradeId, q.updatedAt = :now " +
		"WHERE q.id = :id AND q.status = 'QUOTED'")
	int markCommitted(@Param("id") UUID id, @Param("tradeId") String tradeId, @Param("now") LocalDateTime now);

	Optional<CheckoutQuoteEntity> findByTradeId(String tradeId);
}


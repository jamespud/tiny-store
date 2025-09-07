package com.github.spud.tinystore.product.domain;

import com.github.spud.tinystore.infrastructure.domain.inventory.Stock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * @author Spud
 * @date 2025/8/10
 */
@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

	Stock findInventoryById(UUID id);

	@Query("SELECT SUM (i.total) FROM Stock i WHERE i.skuId = ?1")
	Integer findAllBySkuId(UUID skuId);

	@Modifying
	@Query("UPDATE Stock i SET i.total = i.reserved + ?2, i.total = i.total - ?2 WHERE i.id = ?1 AND i.total >= ?2")
	Integer freezeStockById(UUID id, Integer amount);

	@Modifying
	@Query("UPDATE Stock i SET i.reserved = i.reserved - ?2, i.total = i.total + ?2 WHERE i.id = ?1 AND i.reserved >= ?2")
	Integer thawStockById(UUID id, Integer amount);

	@Modifying
	@Query("UPDATE Stock i SET i.total = i.total + ?2 WHERE i.id = ?1")
	int increaseStockById(UUID id, Integer amount);

	@Modifying
	@Query("UPDATE Stock i SET i.reserved = i.reserved - ?2 WHERE i.id = ?1 AND i.reserved >= ?2")
	Integer decreaseStockById(UUID id, Integer amount);
	
	@Modifying
	@Query("UPDATE Stock i SET i.total = i.total + ?2, i.reserved = i.reserved - ?2 WHERE i.id = ?1 AND i.reserved >= ?2")
	Integer rollBackStock(UUID id, Integer amount);
}

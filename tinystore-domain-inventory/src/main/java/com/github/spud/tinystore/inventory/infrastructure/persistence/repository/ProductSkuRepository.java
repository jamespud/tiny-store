package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSku;
import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSpu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * SKU数据访问层（含库存操作）
 */
@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

	/**
	 * 查询SPU的所有SKU（用于商品详情页规格选择）
	 * @param spu SPU
	 * @return SKU列表
	 */
	List<ProductSku> findBySpuOrderByCreateTimeAsc(ProductSpu spu);

	/**
	 * 查询SPU的有效SKU（状态正常且有库存）
	 * @param spu SPU
	 * @param status 状态（1-正常）
	 * @param minStock 最小库存（>0）
	 * @return SKU列表
	 */
	List<ProductSku> findBySpuAndStatusAndStockGreaterThan(ProductSpu spu, Integer status, Integer minStock);

	/**
	 * 校验SPU的规格值组合是否重复（避免重复创建SKU）
	 * @param spu SPU
	 * @param specValueIds 规格值ID组合（如"1,5"）
	 * @return SKU信息
	 */
	Optional<ProductSku> findBySpuAndSpecValueIds(ProductSpu spu, String specValueIds);

	/**
	 * 库存锁定/扣减时加行锁（防止并发超卖）
	 * @param skuId SKU ID
	 * @return 加锁的SKU信息
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM ProductSku s WHERE s.skuId = :skuId")
	Optional<ProductSku> findBySkuIdForUpdate(@Param("skuId") Long skuId);

	/**
	 * 批量查询SKU（用于订单创建时校验库存）
	 * @param skuIds SKU ID列表
	 * @return SKU列表
	 */
	List<ProductSku> findBySkuIdIn(List<Long> skuIds);
}
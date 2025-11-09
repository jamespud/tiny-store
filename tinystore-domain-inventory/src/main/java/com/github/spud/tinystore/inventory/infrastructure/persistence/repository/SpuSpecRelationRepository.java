package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSpec;
import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSpu;
import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.SpuSpecRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SPU与规格关联数据访问层
 */
@Repository
public interface SpuSpecRelationRepository extends JpaRepository<SpuSpecRelation, Long> {

	/**
	 * 查询SPU关联的所有规格（用于商品规格展示）
	 * @param spu SPU
	 * @return 关联列表
	 */
	List<SpuSpecRelation> findBySpuOrderBySortAsc(ProductSpu spu);

	/**
	 * 校验SPU是否已关联某规格（避免重复关联）
	 * @param spu SPU
	 * @param spec 规格
	 * @return 关联信息
	 */
	Optional<SpuSpecRelation> findBySpuAndSpec(ProductSpu spu, ProductSpec spec);

	/**
	 * 批量删除SPU的规格关联（用于SPU编辑时更新规格）
	 * @param spu SPU
	 */
	void deleteBySpu(ProductSpu spu);
}
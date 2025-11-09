package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSpec;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 商品规格数据访问层
 */
@Repository
public interface ProductSpecRepository extends JpaRepository<ProductSpec, Long> {

	/**
	 * 根据规格名称查询（用于校验重复）
	 * @param specName 规格名称（如"颜色"）
	 * @return 规格信息
	 */
	Optional<ProductSpec> findBySpecName(String specName);

	/**
	 * 按排序权重查询规格（用于规格展示顺序）
	 * @return 规格列表
	 */
	List<ProductSpec> findAllByOrderBySortDesc();
}
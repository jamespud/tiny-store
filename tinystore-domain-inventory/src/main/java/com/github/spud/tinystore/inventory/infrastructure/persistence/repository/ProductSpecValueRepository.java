package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSpec;
import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSpecValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 规格值数据访问层
 */
@Repository
public interface ProductSpecValueRepository extends JpaRepository<ProductSpecValue, Long> {

	/**
	 * 查询某规格的所有值（如"颜色"的所有颜色值）
	 * @param spec 规格
	 * @return 规格值列表
	 */
	List<ProductSpecValue> findBySpecOrderBySortAsc(ProductSpec spec);

	/**
	 * 校验规格下的值是否重复（同一规格下值名称唯一）
	 * @param spec 规格
	 * @param valueName 规格值名称（如"黑色"）
	 * @return 存在性
	 */
	boolean existsBySpecAndValueName(ProductSpec spec, String valueName);
}
package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品品牌数据访问层
 */
@Repository
public interface ProductBrandRepository extends JpaRepository<ProductBrand, Long> {

	/**
	 * 根据品牌首字母查询（用于字母筛选）
	 * @param firstLetter 首字母
	 * @return 品牌列表
	 */
	List<ProductBrand> findByFirstLetterAndStatus(String firstLetter, Integer status);

	/**
	 * 根据品牌名称查询（用于校验重复）
	 * @param brandName 品牌名称
	 * @return 品牌信息（ Optional 避免空指针）
	 */
	Optional<ProductBrand> findByBrandName(String brandName);

	/**
	 * 查询启用的品牌（按排序权重倒序）
	 * @param status 状态（1-启用）
	 * @return 品牌列表
	 */
	List<ProductBrand> findByStatusOrderBySortDesc(Integer status);
}
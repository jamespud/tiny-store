package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商品分类数据访问层
 */
@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

	/**
	 * 根据父分类ID查询子分类（用于构建分类树）
	 * @param parentId 父分类ID
	 * @return 子分类列表
	 */
	List<ProductCategory> findByParentId(Long parentId);

	/**
	 * 根据层级和状态查询分类（如查询所有启用的三级分类）
	 * @param level 分类层级
	 * @param status 状态（0-禁用，1-启用）
	 * @return 分类列表
	 */
	List<ProductCategory> findByLevelAndStatus(Integer level, Integer status);

	/**
	 * 判断分类是否为叶子节点（用于校验是否可绑定商品）
	 * @param categoryId 分类ID
	 * @param isLeaf 是否叶子节点（1-是）
	 * @return 存在性
	 */
	boolean existsByCategoryIdAndIsLeaf(Long categoryId, Integer isLeaf);
}
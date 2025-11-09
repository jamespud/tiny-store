package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductBrand;
import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductCategory;
import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductSpu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SPU数据访问层
 */
@Repository
public interface ProductSpuRepository extends JpaRepository<ProductSpu, Long> {

	/**
	 * 查询商家的所有SPU（用于商家后台商品管理）
	 * @param merchant 所属商家
	 * @return SPU列表
	 */
//	List<ProductSpu> findByMerchantOrderByCreateTimeDesc(MerchantCore merchant);

	/**
	 * 按分类+品牌查询SPU（用于前台分类页筛选）
	 * @param category 所属分类
	 * @param brand 所属品牌
	 * @param auditStatus 审核状态（1-通过）
	 * @param saleStatus 销售状态（1-上架）
	 * @return SPU列表
	 */
	List<ProductSpu> findByCategoryAndBrandAndAuditStatusAndSaleStatus(
		ProductCategory category, ProductBrand brand, Integer auditStatus, Integer saleStatus);

	/**
	 * 根据SPU名称模糊查询（用于搜索）
	 * @param spuName 名称关键字
	 * @param auditStatus 审核状态（1-通过）
	 * @param saleStatus 销售状态（1-上架）
	 * @return SPU列表
	 */
	List<ProductSpu> findBySpuNameLikeAndAuditStatusAndSaleStatus(
		String spuName, Integer auditStatus, Integer saleStatus);

	/**
	 * 校验商家SPU名称是否重复（同一商家下SPU名称唯一）
	 * @param merchant 所属商家
	 * @param spuName SPU名称
	 * @return 存在性
	 */
//	boolean existsByMerchantAndSpuName(MerchantCore merchant, String spuName);
}
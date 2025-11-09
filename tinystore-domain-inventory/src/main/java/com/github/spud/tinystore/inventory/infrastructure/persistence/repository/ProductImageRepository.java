package com.github.spud.tinystore.inventory.infrastructure.persistence.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品图片数据访问层
 */
@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

	/**
	 * 查询SPU/SKU的所有图片（用于轮播展示）
	 * @param refType 关联类型（1-SPU，2-SKU）
	 * @param refId 关联ID（spuId或skuId）
	 * @return 图片列表（按排序权重倒序）
	 */
	List<ProductImage> findByRefTypeAndRefIdOrderBySortDesc(Integer refType, Long refId);

	/**
	 * 查询SPU的主图（用于列表页展示）
	 * @param refType 关联类型（1-SPU）
	 * @param refId SPU ID
	 * @param isMain 是否主图（1-是）
	 * @return 主图信息
	 */
	Optional<ProductImage> findByRefTypeAndRefIdAndIsMain(Integer refType, Long refId, Integer isMain);

	/**
	 * 批量删除SPU/SKU的图片（用于商品编辑时更新图片）
	 * @param refType 关联类型
	 * @param refId 关联ID
	 */
	void deleteByRefTypeAndRefId(Integer refType, Long refId);
}
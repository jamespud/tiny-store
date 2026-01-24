package com.github.spud.tinystore.product.domain.model.aggregate;

import com.github.spud.tinystore.product.domain.model.entity.ProductImage;
import com.github.spud.tinystore.product.domain.model.valueobject.Brand;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductAttribute;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductCategory;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class Product {

	private final ProductId productId;

	@NotBlank(message = "商品名称不能为空")
	private String name;

	@NotNull(message = "商品类目不能为空")
	private final ProductCategory category;

	@NotNull(message = "品牌不能为空")
	private final Brand brand;

	@Size(min = 1, message = "至少包含一个基础属性")
	private List<ProductAttribute> baseAttributes;

	private final ProductType type;

	private ProductStatus status;

	private List<ProductImage> images = new ArrayList<>();

	private final LocalDateTime createTime;
	private LocalDateTime updateTime;

	// 私有构造器（通过工厂方法创建）
	private Product(ProductId productId, String name, ProductCategory category,
		Brand brand, ProductType type, List<ProductAttribute> baseAttributes) {
		this.productId = productId;
		this.name = name;
		this.category = category;
		this.brand = brand;
		this.type = type;
		this.baseAttributes = baseAttributes;
		this.status = ProductStatus.DRAFT; // 初始状态为草稿
		this.createTime = LocalDateTime.now();
		this.updateTime = LocalDateTime.now();
	}

	private Product(ProductId productId, String name, ProductCategory category,
		Brand brand, ProductType type, List<ProductAttribute> baseAttributes,
		ProductStatus status, LocalDateTime createTime, LocalDateTime updateTime) {
		this.productId = productId;
		this.name = name;
		this.category = category;
		this.brand = brand;
		this.type = type;
		this.baseAttributes = baseAttributes;
		this.status = status != null ? status : ProductStatus.DRAFT;
		this.createTime = createTime != null ? createTime : LocalDateTime.now();
		this.updateTime = updateTime != null ? updateTime : this.createTime;
	}

	// 工厂方法（封装创建规则）
	public static Product create(ProductId productId, String name, ProductCategory category,
		Brand brand, ProductType type, List<ProductAttribute> baseAttributes) {
		// 校验业务规则（可抽离为领域服务）
		validateName(name);
		validateAttributes(baseAttributes, category);

		return new Product(productId, name, category, brand, type, baseAttributes);
	}

	public static Product rehydrate(ProductId productId, String name, ProductCategory category,
		Brand brand, ProductType type, List<ProductAttribute> baseAttributes,
		ProductStatus status, LocalDateTime createTime, LocalDateTime updateTime) {
		return new Product(productId, name, category, brand, type, baseAttributes, status, createTime,
			updateTime);
	}

	// 领域行为：更新基础属性
	public void updateAttributes(List<ProductAttribute> newAttributes) {
		if (!isModifiableStatus()) {
			throw new IllegalArgumentException("仅草稿或下架状态可修改属性");
		}
		validateAttributes(newAttributes, this.category);
		this.baseAttributes = newAttributes;
		this.updateTime = LocalDateTime.now();
	}

	public void publish() {
		if (this.status != ProductStatus.DRAFT) {
			throw new IllegalArgumentException("Only DRAFT products can be published");
		}
		this.status = ProductStatus.ONLINE;
		this.updateTime = LocalDateTime.now();
	}

	// 辅助方法：校验状态是否允许修改
	private boolean isModifiableStatus() {
		return this.status == ProductStatus.DRAFT || this.status == ProductStatus.OFFLINE;
	}

	// 私有校验方法
	private static void validateName(String name) {
		if (name.length() > 100) {
			throw new IllegalArgumentException("商品名称长度不能超过100字符");
		}
	}

	private static void validateAttributes(List<ProductAttribute> attributes,
		ProductCategory category) {
		// 实际应调用类目服务校验属性是否符合类目模板
		if (attributes.isEmpty()) {
			throw new IllegalArgumentException("商品属性不能为空");
		}
	}

	// 商品类型枚举（值对象）
	public enum ProductType {
		PHYSICAL_GOODS,  // 实物商品
		VIRTUAL_GOODS,   // 虚拟商品
		SERVICE          // 服务类商品
	}
}

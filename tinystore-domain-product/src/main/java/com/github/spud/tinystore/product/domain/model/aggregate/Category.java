package com.github.spud.tinystore.product.domain.model.aggregate;

import com.github.spud.tinystore.product.domain.model.valueobject.AttributeDefinition;
import com.github.spud.tinystore.product.domain.model.valueobject.AttributeTemplate;

public class Category {
    // 领域标识
    private String categoryId;
    // 类目名称
    private String name;
    // 父类目ID（支持多级分类）
    private String parentId;
    // 层级（如1级/2级）
    private int level;
    // 属性模板（必选/可选属性）
    private AttributeTemplate attributeTemplate;
    // 规格模板（用于生成SKU）
    private SpecificationTemplate specTemplate;
    // 状态（启用/禁用）
    private CategoryStatus status;

    // 领域行为：添加必选属性（仅允许对未启用的类目操作）
    public void addMandatoryAttribute(AttributeDefinition attribute) {
        if (this.status == CategoryStatus.ENABLED) {
            throw new InvalidOperationException("启用的类目不可修改必选属性");
        }
        this.attributeTemplate.addMandatory(attribute);
    }

    // 领域行为：启用类目（需包含至少一个必选属性）
    public void enable() {
        if (this.attributeTemplate.getMandatoryAttributes().isEmpty()) {
            throw new InvalidStateException("类目必须包含至少一个必选属性");
        }
        this.status = CategoryStatus.ENABLED;
    }
}
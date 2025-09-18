package com.github.spud.tinystore.product.domain.model.valueobject;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Value
public class ProductAttribute {
    @NotBlank(message = "属性键不能为空")
    String attributeKey;  // 如"材质"
    @NotBlank(message = "属性值不能为空")
    String attributeValue; // 如"棉"

    // 重写equals，确保属性键值对唯一
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductAttribute that = (ProductAttribute) o;
        return attributeKey.equals(that.attributeKey) && attributeValue.equals(that.attributeValue);
    }
}
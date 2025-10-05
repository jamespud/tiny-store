package com.github.spud.tinystore.product.domain.model.valueobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Brand {

    private String brandId;

    private String brandName;
    
    // Convenience constructor for name only
    public Brand(String brandName) {
        this.brandName = brandName;
    }
}

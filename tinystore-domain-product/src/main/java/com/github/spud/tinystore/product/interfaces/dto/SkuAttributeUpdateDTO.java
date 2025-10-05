package com.github.spud.tinystore.product.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class SkuAttributeUpdateDTO {
    @NotNull
    private Map<String, Object> attributes;
}

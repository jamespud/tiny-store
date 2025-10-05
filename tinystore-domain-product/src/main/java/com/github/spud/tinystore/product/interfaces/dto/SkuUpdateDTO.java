package com.github.spud.tinystore.product.interfaces.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SkuUpdateDTO extends SkuCreateDTO {
    // Inherits all fields from SkuCreateDTO
}

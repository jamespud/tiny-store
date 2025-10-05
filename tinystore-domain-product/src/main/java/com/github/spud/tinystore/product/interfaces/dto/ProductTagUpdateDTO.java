package com.github.spud.tinystore.product.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ProductTagUpdateDTO {
    @NotNull
    private List<String> tags;
}

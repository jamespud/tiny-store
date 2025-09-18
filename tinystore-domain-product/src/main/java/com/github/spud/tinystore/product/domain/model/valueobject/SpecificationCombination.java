package com.github.spud.tinystore.product.domain.model.valueobject;

import java.util.List;
import java.util.Objects;

public class SpecificationCombination {

    List<Specification> specifications;

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof SpecificationCombination that)) return false;

        return Objects.equals(specifications, that.specifications);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(specifications);
    }
}

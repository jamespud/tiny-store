package com.github.spud.tinystore.product.domain.model.valueobject;

import java.util.List;
import java.util.Objects;

public class SpecificationCombination {

    List<Specification> specifications;

    public static SpecificationCombination single(String name, String value) {
        SpecificationCombination combination = new SpecificationCombination();
        Specification specification = new Specification();
        specification.name = name;
        specification.value = value;
        combination.specifications = List.of(specification);
        return combination;
    }

    public List<Specification> getSpecifications() {
        return specifications;
    }

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

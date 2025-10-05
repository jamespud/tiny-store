package com.github.spud.tinystore.product.domain.model.valueobject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SpecificationCombination {

    List<Specification> specifications;

    public SpecificationCombination() {
        this.specifications = new ArrayList<>();
    }
    
    // Constructor from string format: "color=red,size=XL"
    public SpecificationCombination(String specString) {
        this.specifications = new ArrayList<>();
        if (specString != null && !specString.isBlank()) {
            String[] pairs = specString.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    Specification spec = new Specification();
                    spec.name = keyValue[0].trim();
                    spec.value = keyValue[1].trim();
                    this.specifications.add(spec);
                }
            }
        }
    }

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

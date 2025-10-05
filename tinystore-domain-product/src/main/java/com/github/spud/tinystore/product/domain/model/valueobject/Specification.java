package com.github.spud.tinystore.product.domain.model.valueobject;

import java.util.Objects;

public class Specification {

	String name;

	String value;

	@Override
	public final boolean equals(Object o) {
		if (!(o instanceof Specification that)) {
			return false;
		}

		return Objects.equals(name, that.name) && Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(name);
		result = 31 * result + Objects.hashCode(value);
		return result;
	}
}

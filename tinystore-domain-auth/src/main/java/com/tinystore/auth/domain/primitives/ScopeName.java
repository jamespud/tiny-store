package com.tinystore.auth.domain.primitives;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class ScopeName implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String value;

	private ScopeName(String value) {
		String normalized = Objects.requireNonNull(value, "scope must not be null").trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("scope must not be blank");
		}
		this.value = normalized;
	}

	public static ScopeName of(String value) {
		return new ScopeName(value);
	}

	public String getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ScopeName scopeName)) {
			return false;
		}
		return value.equals(scopeName.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return value;
	}
}
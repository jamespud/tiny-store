package com.github.spud.tinystore.auth.domain.primitives;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 手机号值对象，统一校验格式。
 */
public final class PhoneNumber implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final Pattern PATTERN = Pattern.compile("^\\+?[0-9]{6,20}$");

	private final String value;

	private PhoneNumber(String value) {
		String normalized = Objects.requireNonNull(value, "phone must not be null").trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("phone must not be blank");
		}
		if (!PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("invalid phone format");
		}
		this.value = normalized;
	}

	public static PhoneNumber of(String value) {
		return new PhoneNumber(value);
	}

	public String getValue() {
		return value;
	}

	public String masked() {
		if (value.length() <= 4) {
			return value;
		}
		int prefix = Math.max(0, value.length() - 4);
		return "*".repeat(prefix) + value.substring(prefix);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PhoneNumber that)) {
			return false;
		}
		return value.equals(that.value);
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
package com.tinystore.auth.domain.primitives;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 领域层用户标识，封装对 UUID 的校验与比较。
 */
public final class UserId implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID value;

	private UserId(UUID value) {
		this.value = Objects.requireNonNull(value, "userId must not be null");
	}

	public static UserId of(UUID value) {
		return new UserId(value);
	}

	public static UserId random() {
		return new UserId(UUID.randomUUID());
	}

	public UUID getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof UserId userId)) {
			return false;
		}
		return value.equals(userId.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
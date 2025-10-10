package com.github.spud.tinystore.auth.domain.primitives;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public record ClientId(String value) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public ClientId(String value) {
		String normalized = Objects.requireNonNull(value, "clientId must not be null").trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("clientId must not be blank");
		}
		this.value = normalized;
	}

	public static ClientId of(String value) {
		return new ClientId(value);
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ClientId clientId)) {
			return false;
		}
		return value.equals(clientId.value);
	}

	@Override
	public String toString() {
		return value;
	}
}
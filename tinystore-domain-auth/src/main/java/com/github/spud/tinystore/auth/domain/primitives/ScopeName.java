package com.github.spud.tinystore.auth.domain.primitives;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public record ScopeName(String value) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public ScopeName(String value) {
    String normalized = Objects.requireNonNull(value, "scope must not be null").trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    this.value = normalized;
  }

  public static ScopeName of(String value) {
    return new ScopeName(value);
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
  public String toString() {
    return value;
  }
}
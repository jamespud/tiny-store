package com.github.spud.tinystore.auth.domain.primitives;

import java.io.Serial;
import java.io.Serializable;

/**
 * 刷新令牌版本号，确保单调递增。
 */
public final class RtVersion implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final long value;

	private RtVersion(long value) {
		if (value < 1) {
			throw new IllegalArgumentException("rtVersion must be >= 1");
		}
		this.value = value;
	}

	public static RtVersion of(long value) {
		return new RtVersion(value);
	}

	public long getValue() {
		return value;
	}

	public RtVersion next() {
		return new RtVersion(value + 1);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof RtVersion that)) {
			return false;
		}
		return value == that.value;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(value);
	}

	@Override
	public String toString() {
		return Long.toString(value);
	}
}
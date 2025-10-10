package com.github.spud.tinystore.auth.domain.primitives;

import jodd.util.StringUtil;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * 领域层用户标识，封装对 String 的校验与比较。
 */
public record UserId(String value) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public UserId {
		if (StringUtil.isEmpty(value)) {
			throw new IllegalArgumentException("userId must not be empty");
		}
	}

	public static UserId of(String value) {
		return new UserId(value);
	}

	/**
	 * 生成一个随机的 UserId。
	 *
	 * @return 随机生成的 UserId 实例
	 */
	public static UserId random() {
		return new UserId(UUID.randomUUID().toString().replace("-", ""));
	}
}

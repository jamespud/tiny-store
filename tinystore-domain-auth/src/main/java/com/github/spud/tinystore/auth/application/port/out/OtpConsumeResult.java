package com.github.spud.tinystore.auth.application.port.out;

/**
 * OTP 原子校验+消费的结果（C4）。
 *
 * <p>把"查找 → 比对 → 标记已用"压缩成仓储层的一次原子操作：
 * 多副本下同一个验证码只能被成功消费一次，且任一副本都能校验另一副本签发的验证码。
 */
public enum OtpConsumeResult {

  /** 该手机号没有未消费的验证码（未发送过，或已被成功消费）。 */
  NOT_FOUND,

  /** 验证码存在但已过期，已被移除。 */
  EXPIRED,

  /** 验证码与记录不一致。 */
  MISMATCH,

  /** 校验通过，且已在同一次原子操作中消费。 */
  SUCCESS
}

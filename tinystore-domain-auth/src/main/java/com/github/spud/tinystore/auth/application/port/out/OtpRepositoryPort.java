package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import java.util.Optional;

public interface OtpRepositoryPort {

  Otp save(Otp otp);

  Optional<Otp> findLatest(PhoneNumber phone);

  void markUsed(Otp otp);

  /**
   * 原子校验并消费验证码（C4）。
   *
   * <p>实现必须保证"比对 + 消费"是一次原子操作：同一验证码在并发（含跨副本）场景下
   * 只能返回一次 {@link OtpConsumeResult#SUCCESS}。
   */
  OtpConsumeResult verifyAndConsume(PhoneNumber phone, OtpCode code);
}

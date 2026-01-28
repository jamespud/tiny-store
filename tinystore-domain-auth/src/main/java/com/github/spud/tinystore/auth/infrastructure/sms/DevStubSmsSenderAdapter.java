package com.github.spud.tinystore.auth.infrastructure.sms;

import com.github.spud.tinystore.auth.application.port.out.SmsSenderPort;
import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 开发态短信发送适配器：固定返回 123456 作为验证码，便于 E2E 调试。
 */
@Slf4j
@Component
@Primary
public class DevStubSmsSenderAdapter implements SmsSenderPort {

  @Override
  public OtpCode sendLoginCode(PhoneNumber phone, OtpCode code) {
    // 开发模式下，固定验证码 123456，忽略生成的随机码
    log.info("[DevStubSmsSenderAdapter] send login OTP to {} (stub: 123456)", phone.value());
    return OtpCode.of("123456");
  }
}
package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyOtpCommand;
import com.github.spud.tinystore.auth.application.port.in.AuthUseCase;
import com.github.spud.tinystore.auth.application.port.in.OtpUseCase;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService implements AuthUseCase {

  private final OtpUseCase otpUseCase;

  public AuthApplicationService(OtpUseCase otpUseCase) {
    this.otpUseCase = otpUseCase;
  }

  @Override
  public AuthResult loginByOtp(VerifyOtpCommand command) {
    return otpUseCase.verifyOtp(command);
  }

  @Override
  public AuthResult loginByPassword(VerifyOtpCommand command) {
    // TODO: 实现密码登录逻辑
    // 当前暂时委托给 OTP 验证，后续可以根据需要实现独立的密码验证逻辑
    throw new UnsupportedOperationException(
        "密码登录功能暂未实现，请使用 REST API 端点 /api/auth/login/password");
  }
}
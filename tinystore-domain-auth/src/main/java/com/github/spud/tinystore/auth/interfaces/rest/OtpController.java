package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.application.dto.SendOtpCommand;
import com.github.spud.tinystore.auth.application.port.in.OtpUseCase;
import com.github.spud.tinystore.auth.interfaces.security.otp.OtpAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

  private final OtpUseCase otpUseCase;
  private final AuthenticationManager authenticationManager;

  public OtpController(OtpUseCase otpUseCase, AuthenticationManager authenticationManager) {
    this.otpUseCase = otpUseCase;
    this.authenticationManager = authenticationManager;
  }

  @PostMapping("/send")
  public ResponseEntity<Void> send(@Valid @RequestBody SendRequest request,
      HttpServletRequest servletRequest) {
    otpUseCase.sendOtp(new SendOtpCommand(
        request.phone(),
        request.requestId() != null ? request.requestId() : UUID.randomUUID().toString(),
        servletRequest.getRemoteAddr(),
        servletRequest.getHeader("User-Agent")));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/verify")
  public ResponseEntity<?> verify(@Valid @RequestBody VerifyRequest request) {
    try {
      Authentication authentication = authenticationManager.authenticate(
          new OtpAuthenticationToken(request.phone(), request.code()));
      SecurityContextHolder.getContext().setAuthentication(authentication);

      // 返回统一的成功响应格式
      return ResponseEntity.ok(new VerifyResponse(
          "success",
          "验证码验证成功",
          authentication.getName(),
          null // TODO: 根据需要添加 token 信息
      ));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(new VerifyResponse(
          "error",
          "验证码验证失败: " + e.getMessage(),
          null,
          null
      ));
    }
  }

  public record SendRequest(@NotBlank String phone, String requestId, @NotBlank String ipAddress,
                            @NotBlank String userAgent) {

  }

  public record VerifyRequest(@NotBlank String phone, @NotBlank String code) {

  }

  public record VerifyResponse(String status, String message, String userId, String token) {

  }
}
package com.github.spud.tinystore.auth.interfaces.security.otp;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyOtpCommand;
import com.github.spud.tinystore.auth.application.service.OtpApplicationService;
import java.util.Collection;
import java.util.Collections;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

// 手机号+验证码认证提供者
@Component
public class OtpAuthenticationProvider implements AuthenticationProvider {

  private final OtpApplicationService otpApplicationService;

  public OtpAuthenticationProvider(OtpApplicationService otpApplicationService) {
    this.otpApplicationService = otpApplicationService;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {

    if (authentication instanceof OtpAuthenticationToken token) {
      String phone = (String) token.getPrincipal();
      String otp = (String) token.getCredentials();
      AuthResult authResult = otpApplicationService.verifyOtp(new VerifyOtpCommand(phone, otp));
      // 返回“已认证”的令牌，确保会话中标记为 authenticated=true
      var user = authResult.user();
      Collection<? extends GrantedAuthority> authorities = user.getAuthorities() != null
        ? user.getAuthorities()
        : Collections.emptyList();
      return new OtpAuthenticationToken(user, null, authorities);
    }
    return null;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return OtpAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
package com.github.spud.tinystore.auth.interfaces.security.password;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.service.PasswordApplicationService;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class PasswordAuthenticationProvider implements AuthenticationProvider {

  private final PasswordApplicationService passwordApplicationService;

  public PasswordAuthenticationProvider(@Lazy ObjectProvider<PasswordApplicationService> passwordServiceProvider) {
    this.passwordApplicationService = passwordServiceProvider.getIfAvailable();
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String phone = null;
    String password = null;
    if (authentication instanceof PasswordAuthenticationToken token) {
      phone = (String) token.getPrincipal();
      password = (String) token.getCredentials();
    } else if (authentication instanceof UsernamePasswordAuthenticationToken token) {
      phone = (String) token.getPrincipal();
      password = (String) token.getCredentials();
    }
    if (phone != null && password != null) {
      AuthResult authResult = passwordApplicationService.verifyPassword(
          new VerifyPasswordCommand(phone, password));
      var user = authResult.user();
      Collection<? extends GrantedAuthority> authorities = user.getAuthorities() != null
          ? user.getAuthorities()
          : Collections.emptyList();
      var principal = new com.github.spud.tinystore.auth.domain.model.MallUserPrincipal(user,
          authorities);
      return new PasswordAuthenticationToken(principal, null, authorities);
    }
    return null;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PasswordAuthenticationToken.class.isAssignableFrom(authentication)
        || UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}

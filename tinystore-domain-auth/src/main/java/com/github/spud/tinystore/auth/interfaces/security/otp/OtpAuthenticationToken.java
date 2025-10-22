package com.github.spud.tinystore.auth.interfaces.security.otp;

import com.github.spud.tinystore.auth.domain.model.MallUserPrincipal;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class OtpAuthenticationToken extends AbstractAuthenticationToken {

  private final Object principal;
  private final Object credentials;

  public OtpAuthenticationToken(String phone, String code) {
    super(null);
    this.principal = phone;
    this.credentials = code;
    setAuthenticated(false);
  }

  public OtpAuthenticationToken(MallUserPrincipal principal,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    this.credentials = null;
    setAuthenticated(true);
  }

  public OtpAuthenticationToken(UserDetails userDetails, String code,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = userDetails;
    this.credentials = code;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return credentials;
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  public String getPhone() {
    if (principal instanceof MallUserPrincipal mallUserPrincipal) {
      return mallUserPrincipal.user().getPhone().value();
    }
    return (String) principal;
  }

  public String getCode() {
    return (String) credentials;
  }
}

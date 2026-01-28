package com.github.spud.tinystore.auth.interfaces.security.password;

import com.github.spud.tinystore.auth.domain.model.MallUserPrincipal;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

public class PasswordAuthenticationToken extends AbstractAuthenticationToken {

  private final Object principal;

  private Object credentials;

  public PasswordAuthenticationToken(String phone, String password) {
    super(null);
    this.principal = phone;
    this.credentials = password;
    setAuthenticated(false);
  }

  public PasswordAuthenticationToken(MallUserPrincipal principal, Object credentials,
    Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    this.credentials = credentials;
    super.setAuthenticated(true); // must use super, as we override
  }

  @Override
  public Object getCredentials() {
    return this.credentials;
  }

  @Override
  public Object getPrincipal() {
    return this.principal;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
    Assert.isTrue(!isAuthenticated,
      "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
    super.setAuthenticated(false);
  }

  @Override
  public void eraseCredentials() {
    super.eraseCredentials();
    this.credentials = null;
  }

}
package com.github.spud.tinystore.auth.domain.model.user;

import com.github.spud.tinystore.auth.domain.exception.UserFrozenException;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.util.Collection;
import java.util.Objects;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Mall 用户聚合根。
 */
@Getter
public class MallUser implements UserDetails, Authentication {

  private final UserId id;
  private PhoneNumber phone;
  private String username;
  private String passwordHash;
  private String avatar;
  private Collection<SimpleGrantedAuthority> authorities;
  private MallUserStatus status;
  private RtVersion rtVersion;

  private MallUser(UserId id,
      PhoneNumber phone,
      String username,
      String avatar,
      String passwordHash,
      MallUserStatus status,
      RtVersion rtVersion) {
    this.id = Objects.requireNonNull(id, "id");
    this.phone = Objects.requireNonNull(phone, "phone");
    this.username = username;
    this.passwordHash = passwordHash;
    this.avatar = avatar;
    this.status = Objects.requireNonNullElse(status, MallUserStatus.ACTIVE);
    this.rtVersion = Objects.requireNonNullElse(rtVersion, RtVersion.of(1));
  }

  public static MallUser register(UserId id, PhoneNumber phone, String nickname, String avatar) {
    // 注册时不设置密码，密码应通过独立的凭证管理系统设置
    return new MallUser(id, phone, nickname, avatar, null, MallUserStatus.ACTIVE, RtVersion.of(1));
  }

  public static MallUser restore(UserId id, PhoneNumber phone, String nickname, String avatar,
      String passwordHash,
      MallUserStatus status, RtVersion version) {
    return new MallUser(id, phone, nickname, avatar, passwordHash, status, version);
  }

  public void freeze() {
    this.status = MallUserStatus.FROZEN;
  }

  public void unfreeze() {
    this.status = MallUserStatus.ACTIVE;
  }

  public void ensureActive() {
    if (status == MallUserStatus.FROZEN) {
      throw new UserFrozenException("mall user is frozen");
    }
  }

  public void updateProfile(String nickname, String avatar) {
    this.username = nickname;
    this.avatar = avatar;
  }

  public RtVersion bumpRtVersion() {
    this.rtVersion = this.rtVersion.next();
    return this.rtVersion;
  }

  public boolean isFrozen() {
    return status == MallUserStatus.FROZEN;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public Object getCredentials() {
    return getPassword();
  }

  @Override
  public Object getDetails() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return getPhone();
  }

  @Override
  public boolean isAuthenticated() {
    return false;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {

  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return UserDetails.super.isAccountNonExpired();
  }

  @Override
  public boolean isAccountNonLocked() {
    return UserDetails.super.isAccountNonLocked();
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return UserDetails.super.isCredentialsNonExpired();
  }

  @Override
  public boolean isEnabled() {
    // 冻结用户应被视为不可用
    return !isFrozen();
  }

  @Override
  public String getName() {
    return username;
  }
}
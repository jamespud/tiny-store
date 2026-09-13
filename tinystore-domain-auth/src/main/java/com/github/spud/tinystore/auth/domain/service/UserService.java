package com.github.spud.tinystore.auth.domain.service;

import com.github.spud.tinystore.auth.domain.exception.AccountFrozenException;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.model.user.MallUserStatus;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

  private final AccountServiceFeignClient accountServiceFeignClient;

  public MallUser getOrCreateByPhone(String phone) {
    // 账号域对"查不到用户"返回 404，Feign 会直接抛 FeignException.NotFound；
    // 这正是"新用户首次 OTP 登录"的正常路径，必须当成 null 处理，否则首次登录必然失败。
    AccountServiceFeignClient.UserCoreDto dto;
    try {
      dto = accountServiceFeignClient.getUserByPhone(phone);
    } catch (feign.FeignException.NotFound notFound) {
      dto = null;
    }
    if (dto != null) {
      MallUser user = convertToMallUser(dto);
      return ensureNotFrozen(user);
    } else {
      log.warn("User not found for phone: {}, creating new user in auth domain", phone);
      return MallUser.register(UserId.random(), PhoneNumber.of(phone),
        "用户" + phone.substring(Math.max(phone.length() - 4, 0)), "default");
    }
  }

  public Optional<MallUser> findById(String id) {
    AccountServiceFeignClient.UserCoreDto dto = accountServiceFeignClient.getUserById(id);
    return dto != null ? Optional.of(convertToMallUser(dto)) : Optional.empty();
  }

  public long incrementRtVersion(String id) {
    // TODO: 后续需要在账号域实现refresh token版本管理
    return System.currentTimeMillis() / 1000;
  }

  private MallUser ensureNotFrozen(MallUser user) {
    if (user.isFrozen()) {
      throw new AccountFrozenException("mall user is frozen");
    }
    return user;
  }

  private MallUser convertToMallUser(AccountServiceFeignClient.UserCoreDto userCoreDto) {
    long rtVersion = userCoreDto.credentialVersion() == null ? 1L : userCoreDto.credentialVersion();
    return MallUser.restore(
      UserId.of(userCoreDto.userId()),
      PhoneNumber.of(userCoreDto.account()),
      userCoreDto.nickname(),
      userCoreDto.avatarUrl(),
      null,
      userCoreDto.accountStatus() == 1 ? MallUserStatus.ACTIVE : MallUserStatus.FROZEN,
      RtVersion.of(rtVersion)
    );
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // 同上：账号域的 404 表示"用户名不存在"，应转成 UsernameNotFoundException，
    // 而不是把 Feign 异常原样抛给调用方。
    AccountServiceFeignClient.UserCoreDto userCoreDto;
    try {
      userCoreDto = accountServiceFeignClient.getUserByUsername(username);
    } catch (feign.FeignException.NotFound notFound) {
      throw new UsernameNotFoundException("User not found: " + username);
    }
    if (userCoreDto == null) {
      throw new UsernameNotFoundException("User not found: " + username);
    }
    return org.springframework.security.core.userdetails.User
      .withUsername(userCoreDto.account())
      .password("N/A") // 密码不在认证域存储
      .authorities("ROLE_USER")
      .accountExpired(false)
      .accountLocked(false)
      .credentialsExpired(false)
      .disabled(userCoreDto.accountStatus() != 1)
      .build();
  }

}
